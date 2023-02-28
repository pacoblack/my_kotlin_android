package com.test.gang.proxy

import android.content.Context
import android.net.Uri
import com.test.gang.proxy.e.ProxyCacheException
import com.test.gang.proxy.file.*
import com.test.gang.proxy.headers.EmptyHeadersInjector
import com.test.gang.proxy.headers.HeaderInjector
import com.test.gang.proxy.storage.SourceInfoStorage
import com.test.gang.proxy.storage.SourceInfoStorageFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Simple lightweight proxy server with file caching support that handles HTTP requests.
 * Typical usage:
 * <pre>`
 * public onCreate(Bundle state) {
 * super.onCreate(state);
 *
 * HttpProxyCacheServer proxy = getProxy();
 * String proxyUrl = proxy.getProxyUrl(VIDEO_URL);
 * videoView.setVideoPath(proxyUrl);
 * }
 *
 * private HttpProxyCacheServer getProxy() {
 * // should return single instance of HttpProxyCacheServer shared for whole app.
 * }
`</pre> *
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class HttpProxyCacheServer private constructor(config: Config) {
    private val clientsLock = Any()
    private val socketProcessor = Executors.newFixedThreadPool(8)
    private val clientsMap: MutableMap<String, HttpProxyCacheServerClients?> =
        ConcurrentHashMap<String, HttpProxyCacheServerClients?>()
    private var serverSocket: ServerSocket
    private var port = 0
    private var waitConnectionThread: Thread? = null
    private val config: Config
    private var pinger: Pinger

    constructor(context: Context?) : this(Builder(context).buildConfig()) {}

    /**
     * Returns url that wrap original url and should be used for client (MediaPlayer, ExoPlayer, etc).
     *
     *
     * If file for this url is fully cached (it means method [.isCached] returns `true`)
     * then file:// uri to cached file will be returned.
     *
     *
     * Calling this method has same effect as calling [.getProxyUrl] with 2nd parameter set to `true`.
     *
     * @param url a url to file that should be cached.
     * @return a wrapped by proxy url if file is not fully cached or url pointed to cache file otherwise.
     */
    fun getProxyUrl(url: String): String {
        return getProxyUrl(url, true)
    }

    /**
     * Returns url that wrap original url and should be used for client (MediaPlayer, ExoPlayer, etc).
     *
     *
     * If parameter `allowCachedFileUri` is `true` and file for this url is fully cached
     * (it means method [.isCached] returns `true`) then file:// uri to cached file will be returned.
     *
     * @param url                a url to file that should be cached.
     * @param allowCachedFileUri `true` if allow to return file:// uri if url is fully cached
     * @return a wrapped by proxy url if file is not fully cached or url pointed to cache file otherwise (if `allowCachedFileUri` is `true`).
     */
    fun getProxyUrl(url: String, allowCachedFileUri: Boolean): String {
        if (allowCachedFileUri && isCached(url)) {
            val cacheFile = getCacheFile(url)
            touchFileSafely(cacheFile)
            return Uri.fromFile(cacheFile).toString()
        }
        return if (isAlive) appendToProxyUrl(url) else url
    }

    fun registerCacheListener(cacheListener: CacheListener, url: String) {
        Preconditions.checkAllNotNull(cacheListener, url)
        synchronized(clientsLock) {
            try {
                getClients(url).registerCacheListener(cacheListener)
            } catch (e: ProxyCacheException) {
                LOG.warn("Error registering cache listener", e)
            }
        }
    }

    fun unregisterCacheListener(cacheListener: CacheListener, url: String) {
        Preconditions.checkAllNotNull(cacheListener, url)
        synchronized(clientsLock) {
            try {
                getClients(url).unregisterCacheListener(cacheListener)
            } catch (e: ProxyCacheException) {
                LOG.warn("Error registering cache listener", e)
            }
        }
    }

    fun unregisterCacheListener(cacheListener: CacheListener) {
        Preconditions.checkNotNull(cacheListener)
        synchronized(clientsLock) {
            for (clients in clientsMap.values) {
                clients?.unregisterCacheListener(cacheListener)
            }
        }
    }

    /**
     * Checks is cache contains fully cached file for particular url.
     *
     * @param url an url cache file will be checked for.
     * @return `true` if cache contains fully cached file for passed in parameters url.
     */
    fun isCached(url: String): Boolean {
        Preconditions.checkNotNull(url, "Url can't be null!")
        return getCacheFile(url).exists()
    }

    fun shutdown() {
        LOG.info("Shutdown proxy server")
        shutdownClients()
        config.sourceInfoStorage.release()
        waitConnectionThread!!.interrupt()
        try {
            if (!serverSocket.isClosed) {
                serverSocket.close()
            }
        } catch (e: IOException) {
            onError(ProxyCacheException("Error shutting down proxy server", e))
        }
    }

    // 70+140+280=max~500ms
    private val isAlive: Boolean
        private get() = pinger.ping(3, 70) // 70+140+280=max~500ms

    private fun appendToProxyUrl(url: String?): String {
        return String.format(
            Locale.US,
            "http://%s:%d/%s",
            PROXY_HOST,
            port,
            ProxyCacheUtils.encode(url)
        )
    }

    private fun getCacheFile(url: String): File {
        val cacheDir: File = config.cacheRoot
        val fileName: String = config.fileNameGenerator.generate(url)
        return File(cacheDir, fileName)
    }

    private fun touchFileSafely(cacheFile: File) {
        try {
            config.diskUsage.touch(cacheFile)
        } catch (e: IOException) {
            LOG.error("Error touching file $cacheFile", e)
        }
    }

    private fun shutdownClients() {
        synchronized(clientsLock) {
            for (clients in clientsMap.values) {
                clients?.shutdown()
            }
            clientsMap.clear()
        }
    }

    private fun waitForRequest() {
        try {
            while (!Thread.currentThread().isInterrupted) {
                val socket = serverSocket.accept()
                LOG.debug("Accept new socket $socket")
                socketProcessor.submit(SocketProcessorRunnable(socket))
            }
        } catch (e: IOException) {
            onError(ProxyCacheException("Error during waiting connection", e))
        }
    }

    private fun processSocket(socket: Socket) {
        try {
            val request: GetRequest = GetRequest.read(socket.getInputStream())
            LOG.debug("Request to cache proxy:$request")
            val url: String = ProxyCacheUtils.decode(request.uri)
            if (pinger.isPingRequest(url)) {
                pinger.responseToPing(socket)
            } else {
                val clients: HttpProxyCacheServerClients? = getClients(url)
                clients?.processRequest(request, socket)
            }
        } catch (e: SocketException) {
            // There is no way to determine that client closed connection http://stackoverflow.com/a/10241044/999458
            // So just to prevent log flooding don't log stacktrace
            LOG.debug("Closing socket… Socket is closed by client.")
        } catch (e: ProxyCacheException) {
            onError(ProxyCacheException("Error processing request", e))
        } catch (e: IOException) {
            onError(ProxyCacheException("Error processing request", e))
        } finally {
            releaseSocket(socket)
            LOG.debug("Opened connections: $clientsCount")
        }
    }

    @Throws(ProxyCacheException::class)
    private fun getClients(url: String): HttpProxyCacheServerClients {
        synchronized(clientsLock) {
            var clients: HttpProxyCacheServerClients? = clientsMap[url]
            if (clients == null) {
                clients = HttpProxyCacheServerClients(url, config)
                clientsMap[url] = clients
            }
            return clients
        }
    }

    private val clientsCount: Int
        get() {
            synchronized(clientsLock) {
                var count = 0
                for (clients in clientsMap.values) {
                    count += clients?.getClientsCount() ?: 0
                }
                return count
            }
        }

    private fun releaseSocket(socket: Socket) {
        closeSocketInput(socket)
        closeSocketOutput(socket)
        closeSocket(socket)
    }

    private fun closeSocketInput(socket: Socket) {
        try {
            if (!socket.isInputShutdown) {
                socket.shutdownInput()
            }
        } catch (e: SocketException) {
            // There is no way to determine that client closed connection http://stackoverflow.com/a/10241044/999458
            // So just to prevent log flooding don't log stacktrace
            LOG.debug("Releasing input stream… Socket is closed by client.")
        } catch (e: IOException) {
            onError(ProxyCacheException("Error closing socket input stream", e))
        }
    }

    private fun closeSocketOutput(socket: Socket) {
        try {
            if (!socket.isOutputShutdown) {
                socket.shutdownOutput()
            }
        } catch (e: IOException) {
            LOG.warn(
                "Failed to close socket on proxy side: {}. It seems client have already closed connection.",
                e.message
            )
        }
    }

    private fun closeSocket(socket: Socket) {
        try {
            if (!socket.isClosed) {
                socket.close()
            }
        } catch (e: IOException) {
            onError(ProxyCacheException("Error closing socket", e))
        }
    }

    private fun onError(e: Throwable) {
        LOG.error("HttpProxyCacheServer error", e)
    }

    private inner class WaitRequestsRunnable(private val startSignal: CountDownLatch) :
        Runnable {
        override fun run() {
            startSignal.countDown()
            waitForRequest()
        }
    }

    private inner class SocketProcessorRunnable(private val socket: Socket) : Runnable {
        override fun run() {
            processSocket(socket)
        }
    }

    /**
     * Builder for [HttpProxyCacheServer].
     */
    class Builder(context: Context?) {
        private var cacheRoot: File
        private var fileNameGenerator: FileNameGenerator
        private var diskUsage: DiskUsage
        private val sourceInfoStorage: SourceInfoStorage
        private var headerInjector: HeaderInjector

        /**
         * Overrides default cache folder to be used for caching files.
         *
         *
         * By default AndroidVideoCache uses
         * '/Android/data/[app_package_name]/cache/video-cache/' if card is mounted and app has appropriate permission
         * or 'video-cache' subdirectory in default application's cache directory otherwise.
         *
         * **Note** directory must be used **only** for AndroidVideoCache files.
         *
         * @param file a cache directory, can't be null.
         * @return a builder.
         */
        fun cacheDirectory(file: File?): Builder {
            cacheRoot = Preconditions.checkNotNull(file)
            return this
        }

        /**
         * Overrides default cache file name generator [Md5FileNameGenerator] .
         *
         * @param fileNameGenerator a new file name generator.
         * @return a builder.
         */
        fun fileNameGenerator(fileNameGenerator: FileNameGenerator?): Builder {
            this.fileNameGenerator =
                Preconditions.checkNotNull(
                    fileNameGenerator
                )
            return this
        }

        /**
         * Sets max cache size in bytes.
         *
         *
         * All files that exceeds limit will be deleted using LRU strategy.
         * Default value is 512 Mb.
         *
         * Note this method overrides result of calling [.maxCacheFilesCount]
         *
         * @param maxSize max cache size in bytes.
         * @return a builder.
         */
        fun maxCacheSize(maxSize: Long): Builder {
            diskUsage = TotalSizeLruDiskUsage(maxSize)
            return this
        }

        /**
         * Sets max cache files count.
         * All files that exceeds limit will be deleted using LRU strategy.
         * Note this method overrides result of calling [.maxCacheSize]
         *
         * @param count max cache files count.
         * @return a builder.
         */
        fun maxCacheFilesCount(count: Int): Builder {
            diskUsage = TotalCountLruDiskUsage(count)
            return this
        }

        /**
         * Set custom DiskUsage logic for handling when to keep or clean cache.
         *
         * @param diskUsage a disk usage strategy, cant be `null`.
         * @return a builder.
         */
        fun diskUsage(diskUsage: DiskUsage?): Builder {
            this.diskUsage =
                Preconditions.checkNotNull(diskUsage)
            return this
        }

        /**
         * Add headers along the request to the server
         *
         * @param headerInjector to inject header base on url
         * @return a builder
         */
        fun headerInjector(headerInjector: HeaderInjector?): Builder {
            this.headerInjector = Preconditions.checkNotNull(headerInjector)
            return this
        }

        /**
         * Builds new instance of [HttpProxyCacheServer].
         *
         * @return proxy cache. Only single instance should be used across whole app.
         */
        fun build(): HttpProxyCacheServer {
            val config: Config = buildConfig()
            return HttpProxyCacheServer(config)
        }

        internal fun buildConfig(): Config {
            return Config(
                cacheRoot,
                fileNameGenerator,
                diskUsage,
                sourceInfoStorage,
                headerInjector
            )
        }

        companion object {
            private const val DEFAULT_MAX_SIZE = (512 * 1024 * 1024).toLong()
        }

        init {
            sourceInfoStorage = SourceInfoStorageFactory.newSourceInfoStorage(context)
            cacheRoot = StorageUtils.getIndividualCacheDirectory(context)
            diskUsage = TotalSizeLruDiskUsage(DEFAULT_MAX_SIZE)
            fileNameGenerator = Md5FileNameGenerator()
            headerInjector = EmptyHeadersInjector()
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger("HttpProxyCacheServer")
        private const val PROXY_HOST = "127.0.0.1"
    }

    init {
        this.config =
            Preconditions.checkNotNull(
                config
            )
        try {
            val inetAddress = InetAddress.getByName(PROXY_HOST)
            serverSocket = ServerSocket(0, 8, inetAddress)
            port = serverSocket.localPort
            IgnoreHostProxySelector.install(PROXY_HOST, port)
            val startSignal = CountDownLatch(1)
            waitConnectionThread = Thread(WaitRequestsRunnable(startSignal))
            waitConnectionThread!!.start()
            startSignal.await() // freeze thread, wait for server starts
            pinger = Pinger(PROXY_HOST, port)
            LOG.info("Proxy cache server started. Is it alive? $isAlive")
        } catch (e: IOException) {
            socketProcessor.shutdown()
            throw IllegalStateException("Error starting local proxy server", e)
        } catch (e: InterruptedException) {
            socketProcessor.shutdown()
            throw IllegalStateException("Error starting local proxy server", e)
        }
    }
}