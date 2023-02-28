package com.test.gang.proxy

import android.text.TextUtils
import com.test.gang.proxy.e.InterruptedProxyCacheException
import com.test.gang.proxy.e.ProxyCacheException
import com.test.gang.proxy.headers.EmptyHeadersInjector
import com.test.gang.proxy.headers.HeaderInjector
import com.test.gang.proxy.storage.SourceInfoStorage
import com.test.gang.proxy.storage.SourceInfoStorageFactory
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * [Source] that uses http resource as source for [ProxyCache].
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class HttpUrlSource : Source {
    private val sourceInfoStorage: SourceInfoStorage
    private val headerInjector: HeaderInjector
    private var sourceInfo: SourceInfo
    private var connection: HttpURLConnection? = null
    private var inputStream: InputStream? = null

    @JvmOverloads
    constructor(
        url: String,
        sourceInfoStorage: SourceInfoStorage = SourceInfoStorageFactory.newEmptySourceInfoStorage(),
        headerInjector: HeaderInjector? = EmptyHeadersInjector()
    ) {
        this.sourceInfoStorage =
            Preconditions.checkNotNull(
                sourceInfoStorage
            )
        this.headerInjector = Preconditions.checkNotNull(headerInjector)
        val sourceInfo: SourceInfo? = sourceInfoStorage[url]
        this.sourceInfo =
            sourceInfo
                ?: SourceInfo(
                    url,
                    Int.MIN_VALUE.toLong(),
                    ProxyCacheUtils.getSupposablyMime(url)?:""
                )
    }

    constructor(source: HttpUrlSource) {
        sourceInfo = source.sourceInfo
        sourceInfoStorage = source.sourceInfoStorage
        headerInjector = source.headerInjector
    }

    @Synchronized
    @Throws(ProxyCacheException::class)
    override fun length(): Long {
        if (sourceInfo.length == Int.MIN_VALUE.toLong()) {
            fetchContentInfo()
        }
        return sourceInfo.length
    }

    @Throws(ProxyCacheException::class)
    override fun open(offset: Long) {
        try {
            connection = openConnection(offset, -1)
            val mime = connection!!.contentType
            inputStream = BufferedInputStream(
                connection!!.inputStream,
                ProxyCacheUtils.DEFAULT_BUFFER_SIZE
            )
            val length = readSourceAvailableBytes(connection, offset, connection!!.responseCode)
            sourceInfo = SourceInfo(sourceInfo.url, length, mime)
            sourceInfoStorage.put(sourceInfo.url, sourceInfo)
        } catch (e: IOException) {
            throw ProxyCacheException(
                "Error opening connection for " + sourceInfo.url + " with offset " + offset,
                e
            )
        }
    }

    @Throws(IOException::class)
    private fun readSourceAvailableBytes(
        connection: HttpURLConnection?,
        offset: Long,
        responseCode: Int
    ): Long {
        val contentLength = getContentLength(connection)
        return if (responseCode == HttpURLConnection.HTTP_OK) contentLength else if (responseCode == HttpURLConnection.HTTP_PARTIAL) contentLength + offset else sourceInfo.length
    }

    private fun getContentLength(connection: HttpURLConnection?): Long {
        val contentLengthValue = connection!!.getHeaderField("Content-Length")
        return contentLengthValue?.toLong() ?: -1
    }

    @Throws(ProxyCacheException::class)
    override fun close() {
        if (connection != null) {
            try {
                connection!!.disconnect()
            } catch (e: NullPointerException) {
                val message = "Wait... but why? WTF!? " +
                        "Really shouldn't happen any more after fixing https://github.com/danikula/AndroidVideoCache/issues/43. " +
                        "If you read it on your device log, please, notify me danikula@gmail.com or create issue here " +
                        "https://github.com/danikula/AndroidVideoCache/issues."
                throw RuntimeException(message, e)
            } catch (e: IllegalArgumentException) {
                val message = "Wait... but why? WTF!? " +
                        "Really shouldn't happen any more after fixing https://github.com/danikula/AndroidVideoCache/issues/43. " +
                        "If you read it on your device log, please, notify me danikula@gmail.com or create issue here " +
                        "https://github.com/danikula/AndroidVideoCache/issues."
                throw RuntimeException(message, e)
            } catch (e: ArrayIndexOutOfBoundsException) {
                LOG.error(
                    "Error closing connection correctly. Should happen only on Android L. " +
                            "If anybody know how to fix it, please visit https://github.com/danikula/AndroidVideoCache/issues/88. " +
                            "Until good solution is not know, just ignore this issue :(", e
                )
            }
        }
    }

    @Throws(ProxyCacheException::class)
    override fun read(buffer: ByteArray): Int {
        if (inputStream == null) {
            throw ProxyCacheException("Error reading data from " + sourceInfo.url + ": connection is absent!")
        }
        return try {
            inputStream!!.read(buffer, 0, buffer.size)
        } catch (e: InterruptedIOException) {
            throw InterruptedProxyCacheException(
                "Reading source " + sourceInfo.url + " is interrupted",
                e
            )
        } catch (e: IOException) {
            throw ProxyCacheException(
                "Error reading data from " + sourceInfo.url,
                e
            )
        }
    }

    @Throws(ProxyCacheException::class)
    private fun fetchContentInfo() {
        LOG.debug("Read content info from " + sourceInfo.url)
        var urlConnection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            urlConnection = openConnection(0, 10000)
            val length = getContentLength(urlConnection)
            val mime = urlConnection.contentType
            inputStream = urlConnection.inputStream
            sourceInfo = SourceInfo(sourceInfo.url, length, mime)
            sourceInfoStorage.put(sourceInfo.url, sourceInfo)
            LOG.debug("Source info fetched: $sourceInfo")
        } catch (e: IOException) {
            LOG.error("Error fetching info from " + sourceInfo.url, e)
        } finally {
            ProxyCacheUtils.close(inputStream)
            urlConnection?.disconnect()
        }
    }

    @Throws(IOException::class, ProxyCacheException::class)
    private fun openConnection(offset: Long, timeout: Int): HttpURLConnection {
        var connection: HttpURLConnection
        var redirected: Boolean
        var redirectCount = 0
        var url: String = sourceInfo.url
        do {
            LOG.debug("Open connection " + (if (offset > 0) " with offset $offset" else "") + " to " + url)
            connection = URL(url).openConnection() as HttpURLConnection
            injectCustomHeaders(connection, url)
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=$offset-")
            }
            if (timeout > 0) {
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
            }
            val code = connection.responseCode
            redirected =
                code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_SEE_OTHER
            if (redirected) {
                url = connection.getHeaderField("Location")
                redirectCount++
                connection.disconnect()
            }
            if (redirectCount > MAX_REDIRECTS) {
                throw ProxyCacheException("Too many redirects: $redirectCount")
            }
        } while (redirected)
        return connection
    }

    private fun injectCustomHeaders(connection: HttpURLConnection, url: String) {
        val extraHeaders: Map<String, String> = headerInjector.addHeaders(url)
        for ((key, value) in extraHeaders) {
            connection.setRequestProperty(key, value)
        }
    }

    @get:Throws(ProxyCacheException::class)
    @get:Synchronized
    val mime: String
        get() {
            if (TextUtils.isEmpty(sourceInfo.mime)) {
                fetchContentInfo()
            }
            return sourceInfo.mime
        }
    val url: String
        get() = sourceInfo.url

    override fun toString(): String {
        return "HttpUrlSource{sourceInfo='$sourceInfo}"
    }

    companion object {
        private val LOG = LoggerFactory.getLogger("HttpUrlSource")
        private const val MAX_REDIRECTS = 5
    }
}
