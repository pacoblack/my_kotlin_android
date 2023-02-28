package com.test.gang.proxy

import com.test.gang.proxy.e.InterruptedProxyCacheException
import com.test.gang.proxy.e.ProxyCacheException
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proxy for [Source] with caching support ([Cache]).
 *
 *
 * Can be used only for sources with persistent data (that doesn't change with time).
 * Method [.read] will be blocked while fetching data from source.
 * Useful for streaming something with caching e.g. streaming video/audio etc.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal open class ProxyCache(
    source: Source?,
    cache: Cache?
) {
    private val source: Source
    private val cache: Cache
    private val wc = Object()
    private val stopLock = Any()
    private val readSourceErrorsCount: AtomicInteger

    @Volatile
    private var sourceReaderThread: Thread? = null

    @Volatile
    private var stopped = false

    @Volatile
    private var percentsAvailable = -1
    @Throws(ProxyCacheException::class)
    fun read(buffer: ByteArray, offset: Long, length: Int): Int {
        ProxyCacheUtils.assertBuffer(buffer, offset, length)
        while (!cache.isCompleted && cache.available() < offset + length && !stopped) {
            readSourceAsync()
            waitForSourceData()
            checkReadSourceErrorsCount()
        }
        val read: Int = cache.read(buffer, offset, length)
        if (cache.isCompleted && percentsAvailable != 100) {
            percentsAvailable = 100
            onCachePercentsAvailableChanged(100)
        }
        return read
    }

    @Throws(ProxyCacheException::class)
    private fun checkReadSourceErrorsCount() {
        val errorsCount = readSourceErrorsCount.get()
        if (errorsCount >= MAX_READ_SOURCE_ATTEMPTS) {
            readSourceErrorsCount.set(0)
            throw ProxyCacheException("Error reading source $errorsCount times")
        }
    }

    fun shutdown() {
        synchronized(stopLock) {
            LOG.debug("Shutdown proxy for $source")
            try {
                stopped = true
                if (sourceReaderThread != null) {
                    sourceReaderThread!!.interrupt()
                }
                cache.close()
            } catch (e: ProxyCacheException) {
                onError(e)
            }
        }
    }

    @Synchronized
    @Throws(ProxyCacheException::class)
    private fun readSourceAsync() {
        val readingInProgress =
            sourceReaderThread != null && sourceReaderThread!!.state != Thread.State.TERMINATED
        if (!stopped && !cache.isCompleted && !readingInProgress) {
            sourceReaderThread = Thread(
                SourceReaderRunnable(),
                "Source reader for $source"
            )
            sourceReaderThread!!.start()
        }
    }

    @Throws(ProxyCacheException::class)
    private fun waitForSourceData() {
        synchronized(wc) {
            try {
                wc.wait(1000)
            } catch (e: InterruptedException) {
                throw ProxyCacheException(
                    "Waiting source data is interrupted!",
                    e
                )
            }
        }
    }

    private fun notifyNewCacheDataAvailable(cacheAvailable: Long, sourceAvailable: Long) {
        onCacheAvailable(cacheAvailable, sourceAvailable)
        synchronized(wc) { wc.notifyAll() }
    }

    protected fun onCacheAvailable(cacheAvailable: Long, sourceLength: Long) {
        val zeroLengthSource = sourceLength == 0L
        val percents =
            if (zeroLengthSource) 100 else (cacheAvailable.toFloat() / sourceLength * 100).toInt()
        val percentsChanged = percents != percentsAvailable
        val sourceLengthKnown = sourceLength >= 0
        if (sourceLengthKnown && percentsChanged) {
            onCachePercentsAvailableChanged(percents)
        }
        percentsAvailable = percents
    }

    protected open fun onCachePercentsAvailableChanged(percentsAvailable: Int) {}
    private fun readSource() {
        var sourceAvailable: Long = -1
        var offset: Long = 0
        try {
            offset = cache.available()
            source.open(offset)
            sourceAvailable = source.length()
            val buffer = ByteArray(ProxyCacheUtils.DEFAULT_BUFFER_SIZE)
            var readBytes: Int
            while (source.read(buffer).also { readBytes = it } != -1) {
                synchronized(stopLock) {
                    if (isStopped()) {
                        return
                    }
                    cache.append(buffer, readBytes)
                }
                offset += readBytes.toLong()
                notifyNewCacheDataAvailable(offset, sourceAvailable)
            }
            tryComplete()
            onSourceRead()
        } catch (e: Throwable) {
            readSourceErrorsCount.incrementAndGet()
            onError(e)
        } finally {
            closeSource()
            notifyNewCacheDataAvailable(offset, sourceAvailable)
        }
    }

    private fun onSourceRead() {
        // guaranteed notify listeners after source read and cache completed
        percentsAvailable = 100
        onCachePercentsAvailableChanged(percentsAvailable)
    }

    @Throws(ProxyCacheException::class)
    private fun tryComplete() {
        synchronized(stopLock) {
            if (!isStopped() && cache.available() == source.length()) {
                cache.complete()
            }
        }
    }

    private fun isStopped(): Boolean {
        return Thread.currentThread().isInterrupted || stopped
    }

    private fun closeSource() {
        try {
            source.close()
        } catch (e: ProxyCacheException) {
            onError(ProxyCacheException("Error closing source $source", e))
        }
    }

    protected fun onError(e: Throwable?) {
        val interruption = e is InterruptedProxyCacheException
        if (interruption) {
            LOG.debug("ProxyCache is interrupted")
        } else {
            LOG.error("ProxyCache error", e)
        }
    }

    private inner class SourceReaderRunnable : Runnable {
        override fun run() {
            readSource()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger("ProxyCache")
        private const val MAX_READ_SOURCE_ATTEMPTS = 1
    }

    init {
        this.source =
            Preconditions.checkNotNull(
                source
            )
        this.cache =
            Preconditions.checkNotNull(cache)
        readSourceErrorsCount = AtomicInteger()
    }
}