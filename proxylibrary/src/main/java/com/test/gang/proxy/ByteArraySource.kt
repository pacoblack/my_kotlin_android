package com.test.gang.proxy

import com.test.gang.proxy.e.ProxyCacheException
import java.io.ByteArrayInputStream

/**
 * Simple memory based [Source] implementation.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class ByteArraySource(private val data: ByteArray) : Source {
    private var arrayInputStream: ByteArrayInputStream? = null

    @Throws(ProxyCacheException::class)
    override fun read(buffer: ByteArray): Int {
        return arrayInputStream!!.read(buffer, 0, buffer.size)
    }

    @Throws(ProxyCacheException::class)
    override fun length(): Long {
        return data.size.toLong()
    }

    @Throws(ProxyCacheException::class)
    override fun open(offset: Long) {
        arrayInputStream = ByteArrayInputStream(data)
        arrayInputStream!!.skip(offset)
    }

    @Throws(ProxyCacheException::class)
    override fun close() {
    }
}