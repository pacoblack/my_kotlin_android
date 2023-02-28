package com.test.gang.proxy.file

import java.io.File

/**
 * [DiskUsage] that uses LRU (Least Recently Used) strategy and trims cache size to max size if needed.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class TotalSizeLruDiskUsage(maxSize: Long) : LruDiskUsage() {
    private val maxSize: Long
    override fun accept(file: File, totalSize: Long, totalCount: Int): Boolean {
        return totalSize <= maxSize
    }

    init {
        require(maxSize > 0) { "Max size must be positive number!" }
        this.maxSize = maxSize
    }
}
