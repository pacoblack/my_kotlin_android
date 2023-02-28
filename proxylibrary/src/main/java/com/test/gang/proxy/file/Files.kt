package com.test.gang.proxy.file

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*

/**
 * Utils for work with files.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal object Files {
    private val LOG: Logger = LoggerFactory.getLogger("Files")
    @Throws(IOException::class)
    fun makeDir(directory: File) {
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw IOException("File $directory is not directory!")
            }
        } else {
            val isCreated = directory.mkdirs()
            if (!isCreated) {
                throw IOException(
                    String.format(
                        "Directory %s can't be created",
                        directory.absolutePath
                    )
                )
            }
        }
    }

    fun getLruListFiles(directory: File): List<File> {
        var result: List<File> = LinkedList()
        val files = directory.listFiles()
        if (files != null) {
            result = mutableListOf(*files)
            Collections.sort(result, LastModifiedComparator())
        }
        return result
    }

    @Throws(IOException::class)
    fun setLastModifiedNow(file: File) {
        if (file.exists()) {
            val now = System.currentTimeMillis()
            val modified = file.setLastModified(now) // on some devices (e.g. Nexus 5) doesn't work
            if (!modified) {
                modify(file)
                if (file.lastModified() < now) {
                    // NOTE: apparently this is a known issue (see: http://stackoverflow.com/questions/6633748/file-lastmodified-is-never-what-was-set-with-file-setlastmodified)
                    LOG.warn(
                        "Last modified date {} is not set for file {}",
                        Date(file.lastModified()),
                        file.absolutePath
                    )
                }
            }
        }
    }

    @Throws(IOException::class)
    fun modify(file: File) {
        val size = file.length()
        if (size == 0L) {
            recreateZeroSizeFile(file)
            return
        }
        val accessFile = RandomAccessFile(file, "rwd")
        accessFile.seek(size - 1)
        val lastByte = accessFile.readByte()
        accessFile.seek(size - 1)
        accessFile.write(lastByte.toInt())
        accessFile.close()
    }

    @Throws(IOException::class)
    private fun recreateZeroSizeFile(file: File) {
        if (!file.delete() || !file.createNewFile()) {
            throw IOException("Error recreate zero-size file $file")
        }
    }

    private class LastModifiedComparator : Comparator<File?> {

        private fun compareLong(first: Long, second: Long): Int {
            return if (first < second) -1 else if (first == second) 0 else 1
        }

        override fun compare(lhs: File?, rhs: File?): Int {
            return compareLong(lhs!!.lastModified(), rhs!!.lastModified())
        }
    }
}