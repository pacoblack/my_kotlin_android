package com.test.gang.proxy.file

import android.text.TextUtils
import com.test.gang.proxy.ProxyCacheUtils

/**
 * Implementation of [FileNameGenerator] that uses MD5 of url as file name
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class Md5FileNameGenerator : FileNameGenerator {
    override fun generate(url: String): String {
        val extension = getExtension(url)
        val name: String = ProxyCacheUtils.computeMD5(url)
        return if (TextUtils.isEmpty(extension)) name else "$name.$extension"
    }

    private fun getExtension(url: String): String {
        val dotIndex = url.lastIndexOf('.')
        val slashIndex = url.lastIndexOf('/')
        return if (dotIndex != -1 && dotIndex > slashIndex && dotIndex + 2 + MAX_EXTENSION_LENGTH > url.length) url.substring(
            dotIndex + 1,
            url.length
        ) else ""
    }

    companion object {
        private const val MAX_EXTENSION_LENGTH = 4
    }
}