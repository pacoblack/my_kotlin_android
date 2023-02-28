package com.test.gang.proxy.file

/**
 * Generator for files to be used for caching.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
interface FileNameGenerator {
    fun generate(url: String): String
}