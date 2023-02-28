package com.test.gang.proxy.storage

import com.test.gang.proxy.SourceInfo

/**
 * [SourceInfoStorage] that does nothing.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class NoSourceInfoStorage : SourceInfoStorage {
    override operator fun get(url: String): SourceInfo? {
        return null
    }

    override fun put(url: String, sourceInfo: SourceInfo) {}
    override fun release() {}
}