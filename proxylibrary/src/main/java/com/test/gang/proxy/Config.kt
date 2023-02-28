package com.test.gang.proxy

import com.test.gang.proxy.file.DiskUsage
import com.test.gang.proxy.file.FileNameGenerator
import com.test.gang.proxy.headers.HeaderInjector
import com.test.gang.proxy.storage.SourceInfoStorage
import java.io.File

/**
 * Configuration for proxy cache.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal class Config(
    val cacheRoot: File,
    fileNameGenerator: FileNameGenerator,
    diskUsage: DiskUsage,
    sourceInfoStorage: SourceInfoStorage,
    headerInjector: HeaderInjector
) {
    val fileNameGenerator: FileNameGenerator
    val diskUsage: DiskUsage
    val sourceInfoStorage: SourceInfoStorage
    val headerInjector: HeaderInjector
    fun generateCacheFile(url: String): File {
        val name: String = fileNameGenerator.generate(url)
        return File(cacheRoot, name)
    }

    init {
        this.fileNameGenerator = fileNameGenerator
        this.diskUsage = diskUsage
        this.sourceInfoStorage = sourceInfoStorage
        this.headerInjector = headerInjector
    }
}