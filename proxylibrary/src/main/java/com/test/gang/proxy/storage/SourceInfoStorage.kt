package com.test.gang.proxy.storage

import com.test.gang.proxy.SourceInfo

/**
 * Storage for [SourceInfo].
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
interface SourceInfoStorage {
    operator fun get(url: String): SourceInfo?
    fun put(url: String, sourceInfo: SourceInfo)
    fun release()
}