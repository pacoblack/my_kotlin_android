package com.test.gang.proxy

import android.text.TextUtils
import com.test.gang.proxy.e.ProxyCacheException
import com.test.gang.proxy.file.FileCache
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.util.*

/**
 * [ProxyCache] that read http url and writes data to [Socket]
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal class HttpProxyCache(
    source: HttpUrlSource,
    cache: FileCache
) :
    ProxyCache(source, cache) {
    private val source: HttpUrlSource
    private val cache: FileCache
    private var listener: CacheListener? = null
    fun registerCacheListener(cacheListener: CacheListener) {
        listener = cacheListener
    }

    fun unregisterCacheListener() {
        listener = null
    }

    @Throws(IOException::class, ProxyCacheException::class)
    fun processRequest(request: GetRequest, socket: Socket) {
        val out: OutputStream = BufferedOutputStream(socket.getOutputStream())
        val responseHeaders = newResponseHeaders(request)
        out.write(responseHeaders.toByteArray(charset("UTF-8")))
        val offset: Long = request.rangeOffset
        if (isUseCache(request)) {
            responseWithCache(out, offset)
        } else {
            responseWithoutCache(out, offset)
        }
    }

    @Throws(ProxyCacheException::class)
    private fun isUseCache(request: GetRequest): Boolean {
        val sourceLength: Long = source.length()
        val sourceLengthKnown = sourceLength > 0
        val cacheAvailable: Long = cache.available()
        // do not use cache for partial requests which too far from available cache. It seems user seek video.
        return !sourceLengthKnown || !request.partial || request.rangeOffset <= cacheAvailable + sourceLength * NO_CACHE_BARRIER
    }

    @Throws(IOException::class, ProxyCacheException::class)
    private fun newResponseHeaders(request: GetRequest): String {
        val mime: String = source.mime
        val mimeKnown = !TextUtils.isEmpty(mime)
        val length: Long = if (cache.isCompleted) cache.available() else source.length()
        val lengthKnown = length >= 0
        val contentLength = if (request.partial) length - request.rangeOffset else length
        val addRange = lengthKnown && request.partial
        return StringBuilder()
            .append(if (request.partial) "HTTP/1.1 206 PARTIAL CONTENT\n" else "HTTP/1.1 200 OK\n")
            .append("Accept-Ranges: bytes\n")
            .append(if (lengthKnown) format("Content-Length: %d\n", contentLength) else "")
            .append(
                if (addRange) format(
                    "Content-Range: bytes %d-%d/%d\n",
                    request.rangeOffset,
                    length - 1,
                    length
                ) else ""
            )
            .append(if (mimeKnown) format("Content-Type: %s\n", mime) else "")
            .append("\n") // headers end
            .toString()
    }

    @Throws(ProxyCacheException::class, IOException::class)
    private fun responseWithCache(out: OutputStream, offset: Long) {
        var offset = offset
        val buffer = ByteArray(ProxyCacheUtils.DEFAULT_BUFFER_SIZE)
        var readBytes: Int
        while (read(buffer, offset, buffer.size).also { readBytes = it } != -1) {
            out.write(buffer, 0, readBytes)
            offset += readBytes.toLong()
        }
        out.flush()
    }

    @Throws(ProxyCacheException::class, IOException::class)
    private fun responseWithoutCache(out: OutputStream, offset: Long) {
        var offset = offset
        val newSourceNoCache: HttpUrlSource =
            HttpUrlSource(
                source
            )
        try {
            newSourceNoCache.open(offset.toInt().toLong())
            val buffer = ByteArray(ProxyCacheUtils.DEFAULT_BUFFER_SIZE)
            var readBytes: Int
            while (newSourceNoCache.read(buffer).also { readBytes = it } != -1) {
                out.write(buffer, 0, readBytes)
                offset += readBytes.toLong()
            }
            out.flush()
        } finally {
            newSourceNoCache.close()
        }
    }

    private fun format(pattern: String, vararg args: Any): String {
        return String.format(Locale.US, pattern, *args)
    }

    protected override fun onCachePercentsAvailableChanged(percents: Int) {
        cache.file?.let { listener?.onCacheAvailable(it, source.url, percents) }
    }

    companion object {
        private const val NO_CACHE_BARRIER = .2f
    }

    init {
        this.cache = cache
        this.source = source
    }
}