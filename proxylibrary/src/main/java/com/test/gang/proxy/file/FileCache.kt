package com.test.gang.proxy.file

import com.test.gang.proxy.Cache
import com.test.gang.proxy.e.ProxyCacheException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * [Cache] that uses file for storing data.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class FileCache @JvmOverloads constructor(
    file: File,
    diskUsage: DiskUsage? = UnlimitedDiskUsage()
) : Cache {
    private var diskUsage: DiskUsage? = null

    /**
     * Returns file to be used fo caching. It may as original file passed in constructor as some temp file for not completed cache.
     *
     * @return file for caching.
     */
    var file: File? = null
    private var dataFile: RandomAccessFile? = null

    @Synchronized
    @Throws(ProxyCacheException::class)
    override fun available(): Long {
        return try {
            dataFile!!.length().toInt().toLong()
        } catch (e: IOException) {
            throw ProxyCacheException("Error reading length of file $file", e)
        }
    }

    @Synchronized
    @Throws(ProxyCacheException::class)
    override fun read(buffer: ByteArray, offset: Long, length: Int): Int {
        return try {
            dataFile!!.seek(offset)
            dataFile!!.read(buffer, 0, length)
        } catch (e: IOException) {
            val format =
                "Error reading %d bytes with offset %d from file[%d bytes] to buffer[%d bytes]"
            throw ProxyCacheException(
                String.format(
                    format,
                    length,
                    offset,
                    available(),
                    buffer?.size ?: 0
                ), e
            )
        }
    }

    @Synchronized
    @Throws(ProxyCacheException::class)
    override fun append(data: ByteArray, length: Int) {
        try {
            if (isCompleted) {
                throw ProxyCacheException("Error append cache: cache file $file is completed!")
            }
            dataFile!!.seek(available())
            dataFile!!.write(data, 0, length)
        } catch (e: IOException) {
            val format = "Error writing %d bytes to %s from buffer with size %d"
            data?.let {
                throw ProxyCacheException(String.format(format, length, dataFile, it.size), e)
            }
        }
    }

    @Synchronized
    @Throws(ProxyCacheException::class)
    override fun close() {
        try {
            dataFile!!.close()
            file?.let { diskUsage?.touch(it) }
        } catch (e: IOException) {
            throw ProxyCacheException("Error closing file $file", e)
        }
    }

    @Synchronized
    @Throws(ProxyCacheException::class)
    override fun complete() {
        if (isCompleted) {
            return
        }
        close()
        val fileName = file!!.name.substring(0, file!!.name.length - TEMP_POSTFIX.length)
        val completedFile = File(file!!.parentFile, fileName)
        val renamed = file!!.renameTo(completedFile)
        if (!renamed) {
            throw ProxyCacheException("Error renaming file $file to $completedFile for completion!")
        }
        file = completedFile
        try {
            dataFile = RandomAccessFile(file, "r")
            diskUsage?.touch(file!!)
        } catch (e: IOException) {
            throw ProxyCacheException("Error opening $file as disc cache", e)
        }
    }

    @get:Synchronized
    override val isCompleted: Boolean
        get() = !isTempFile(file)

    private fun isTempFile(file: File?): Boolean {
        return file!!.name.endsWith(TEMP_POSTFIX)
    }

    companion object {
        private const val TEMP_POSTFIX = ".download"
    }

    init {
        try {
            if (diskUsage == null) {
                throw NullPointerException()
            }
            this.diskUsage = diskUsage
            val directory = file.parentFile
            Files.makeDir(directory)
            val completed = file.exists()
            this.file = if (completed) file else File(file.parentFile, file.name + TEMP_POSTFIX)
            dataFile = RandomAccessFile(this.file, if (completed) "r" else "rw")
        } catch (e: IOException) {
            throw ProxyCacheException("Error using file $file as disc cache", e)
        }
    }
}