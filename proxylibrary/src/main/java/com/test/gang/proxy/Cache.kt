package com.test.gang.proxy

import com.test.gang.proxy.e.ProxyCacheException

/**
 * Cache for proxy.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
interface Cache {
    @Throws(ProxyCacheException::class)
    fun available(): Long

    @Throws(ProxyCacheException::class)
    fun read(buffer: ByteArray, offset: Long, length: Int): Int

    @Throws(ProxyCacheException::class)
    fun append(data: ByteArray, length: Int)

    @Throws(ProxyCacheException::class)
    fun close()

    @Throws(ProxyCacheException::class)
    fun complete()
    val isCompleted: Boolean
}