package com.test.gang.proxy.file

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * [DiskUsage] that uses LRU (Least Recently Used) strategy to trim cache.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
abstract class LruDiskUsage : DiskUsage {
    private val workerThread = Executors.newSingleThreadExecutor()

    @Throws(IOException::class)
    override fun touch(file: File) {
        workerThread.submit(TouchCallable(file))
    }

    @Throws(IOException::class)
    private fun touchInBackground(file: File) {
        Files.setLastModifiedNow(file)
        val pf = file.parentFile
        pf?.let{
            val files: List<File> = Files.getLruListFiles(it)
            trim(files)
        }

    }

    protected abstract fun accept(file: File, totalSize: Long, totalCount: Int): Boolean
    private fun trim(files: List<File>) {
        var totalSize = countTotalSize(files)
        var totalCount = files.size
        for (file in files) {
            val accepted = accept(file, totalSize, totalCount)
            if (!accepted) {
                val fileSize = file.length()
                val deleted = file.delete()
                if (deleted) {
                    totalCount--
                    totalSize -= fileSize
                    LOG.info("Cache file $file is deleted because it exceeds cache limit")
                } else {
                    LOG.error("Error deleting file $file for trimming cache")
                }
            }
        }
    }

    private fun countTotalSize(files: List<File>): Long {
        var totalSize: Long = 0
        for (file in files) {
            totalSize += file.length()
        }
        return totalSize
    }

    private inner class TouchCallable(private val file: File) :
        Callable<Void?> {
        @Throws(Exception::class)
        override fun call(): Void? {
            touchInBackground(file)
            return null
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger("LruDiskUsage")
    }
}