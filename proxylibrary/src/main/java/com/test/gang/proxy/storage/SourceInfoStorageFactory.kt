package com.test.gang.proxy.storage

import android.content.Context

/**
 * Simple factory for [SourceInfoStorage].
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
object SourceInfoStorageFactory {
    fun newSourceInfoStorage(context: Context?): SourceInfoStorage {
        return DatabaseSourceInfoStorage(context)
    }

    fun newEmptySourceInfoStorage(): SourceInfoStorage {
        return NoSourceInfoStorage()
    }
}
