package com.test.gang.proxy

import java.io.File

/**
 * Listener for cache availability.
 *
 * @author Egor Makovsky (yahor.makouski@gmail.com)
 * @author Alexey Danilov (danikula@gmail.com).
 */
interface CacheListener {
    fun onCacheAvailable(cacheFile: File, url: String, percentsAvailable: Int)
}