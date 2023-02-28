package com.test.gang.proxy

import com.test.gang.proxy.e.ProxyCacheException
import java.io.ByteArrayInputStream

/**
 * Simple memory based [Cache] implementation.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class ByteArrayCache @JvmOverloads constructor(data: ByteArray? = ByteArray(0)) : Cache {
    @Volatile
    private var data: ByteArray

    @Volatile
    override var isCompleted = false
        private set

    @Throws(ProxyCacheException::class)
    override fun read(buffer: ByteArray, offset: Long, length: Int): Int {
        if (offset >= data.size) {
            return -1
        }
        require(offset <= Int.MAX_VALUE) { "Too long offset for memory cache $offset" }
        return ByteArrayInputStream(data).read(buffer, offset.toInt(), length)
    }

    @Throws(ProxyCacheException::class)
    override fun available(): Long {
        return data.size.toLong()
    }

    @Throws(ProxyCacheException::class)
    override fun append(newData: ByteArray, length: Int) {
        Preconditions.checkNotNull(data)
        Preconditions.checkArgument(length >= 0 && length <= newData.size)
        val appendedData = data.copyOf(data.size + length)
        System.arraycopy(newData, 0, appendedData, data.size, length)
        data = appendedData
    }

    @Throws(ProxyCacheException::class)
    override fun close() {
    }

    override fun complete() {
        isCompleted = true
    }

    init {
        this.data = Preconditions.checkNotNull(data)
    }
}
