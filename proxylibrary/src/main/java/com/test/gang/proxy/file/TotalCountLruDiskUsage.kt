package com.test.gang.proxy.file

import java.io.File

/**
 * [DiskUsage] that uses LRU (Least Recently Used) strategy and trims cache size to max files count if needed.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class TotalCountLruDiskUsage(maxCount: Int) : LruDiskUsage() {
    private val maxCount: Int
    protected override fun accept(file: File, totalSize: Long, totalCount: Int): Boolean {
        return totalCount <= maxCount
    }

    init {
        require(maxCount > 0) { "Max count must be positive number!" }
        this.maxCount = maxCount
    }
}