package com.test.gang.proxy

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.test.gang.proxy.e.ProxyCacheException
import com.test.gang.proxy.file.FileCache
import java.io.File
import java.io.IOException
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client for [HttpProxyCacheServer]
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal class HttpProxyCacheServerClients(url: String, config: Config?) {
    private val clientsCount = AtomicInteger(0)
    private val url: String

    @Volatile
    private var proxyCache: HttpProxyCache? = null
    private val listeners: MutableList<CacheListener> = CopyOnWriteArrayList()
    private val uiCacheListener: CacheListener
    private val config: Config
    @Throws(ProxyCacheException::class, IOException::class)
    fun processRequest(request: GetRequest, socket: Socket) {
        startProcessRequest()
        try {
            clientsCount.incrementAndGet()
            proxyCache?.processRequest(request, socket)
        } finally {
            finishProcessRequest()
        }
    }

    @Synchronized
    @Throws(ProxyCacheException::class)
    private fun startProcessRequest() {
        proxyCache = if (proxyCache == null) newHttpProxyCache() else proxyCache
    }

    @Synchronized
    private fun finishProcessRequest() {
        if (clientsCount.decrementAndGet() <= 0) {
            proxyCache?.shutdown()
            proxyCache = null
        }
    }

    fun registerCacheListener(cacheListener: CacheListener) {
        listeners.add(cacheListener)
    }

    fun unregisterCacheListener(cacheListener: CacheListener) {
        listeners.remove(cacheListener)
    }

    fun shutdown() {
        listeners.clear()
        if (proxyCache != null) {
            proxyCache!!.unregisterCacheListener()
            proxyCache!!.shutdown()
            proxyCache = null
        }
        clientsCount.set(0)
    }

    fun getClientsCount(): Int {
        return clientsCount.get()
    }

    @Throws(ProxyCacheException::class)
    private fun newHttpProxyCache(): HttpProxyCache {
        val source: HttpUrlSource = HttpUrlSource(
            url,
            config.sourceInfoStorage,
            config.headerInjector
        )
        val cache: FileCache =
            FileCache(config.generateCacheFile(url), config.diskUsage)
        val httpProxyCache = HttpProxyCache(source, cache)
        httpProxyCache.registerCacheListener(uiCacheListener)
        return httpProxyCache
    }

    private class UiListenerHandler(private val url: String, listeners: List<CacheListener>) :
        Handler(Looper.getMainLooper()), CacheListener {
        private val listeners: List<CacheListener>
        override fun onCacheAvailable(file: File, url: String, percentsAvailable: Int) {
            val message = obtainMessage()
            message.arg1 = percentsAvailable
            message.obj = file
            sendMessage(message)
        }

        override fun handleMessage(msg: Message) {
            for (cacheListener in listeners) {
                cacheListener.onCacheAvailable(msg.obj as File, url, msg.arg1)
            }
        }

        init {
            this.listeners = listeners
        }
    }

    init {
        this.url = Preconditions.checkNotNull(url)
        this.config =
            Preconditions.checkNotNull(
                config
            )
        uiCacheListener = UiListenerHandler(url, listeners)
    }
}
