package com.test.gang.proxy.file

import java.io.File

/**
 * Unlimited version of [DiskUsage].
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class UnlimitedDiskUsage : DiskUsage {
    override fun touch(file: File) {
    }
}