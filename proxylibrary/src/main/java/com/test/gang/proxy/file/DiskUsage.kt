package com.test.gang.proxy.file

import java.io.File
import java.io.IOException

/**
 * Declares how [FileCache] will use disc space.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
interface DiskUsage {
    @Throws(IOException::class)
    fun touch(file: File)
}
