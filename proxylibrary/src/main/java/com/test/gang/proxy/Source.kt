package com.test.gang.proxy

import com.test.gang.proxy.e.ProxyCacheException

/**
 * Source for proxy.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
interface Source {
    /**
     * Opens source. Source should be open before using [.read]
     *
     * @param offset offset in bytes for source.
     * @throws ProxyCacheException if error occur while opening source.
     */
    @Throws(ProxyCacheException::class)
    fun open(offset: Long)

    /**
     * Returns length bytes or **negative value** if length is unknown.
     *
     * @return bytes length
     * @throws ProxyCacheException if error occur while fetching source data.
     */
    @Throws(ProxyCacheException::class)
    fun length(): Long

    /**
     * Read data to byte buffer from source with current offset.
     *
     * @param buffer a buffer to be used for reading data.
     * @return a count of read bytes
     * @throws ProxyCacheException if error occur while reading source.
     */
    @Throws(ProxyCacheException::class)
    fun read(buffer: ByteArray): Int

    /**
     * Closes source and release resources. Every opened source should be closed.
     *
     * @throws ProxyCacheException if error occur while closing source.
     */
    @Throws(ProxyCacheException::class)
    fun close()
}
