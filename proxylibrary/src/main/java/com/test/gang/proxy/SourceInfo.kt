package com.test.gang.proxy

/**
 * Stores source's info.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class SourceInfo(val url: String, val length: Long, val mime: String) {
    override fun toString(): String {
        return "SourceInfo{" +
                "url='" + url + '\'' +
                ", length=" + length +
                ", mime='" + mime + '\'' +
                '}'
    }
}
