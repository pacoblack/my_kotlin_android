package com.test.gang.lib.video

import android.net.Uri
import com.google.android.exoplayer2.util.MimeTypes

internal object SubtitleUtils {
    fun getSubtitleMime(uri: Uri): String {
        val path = uri.path
        return if (path!!.endsWith(".ssa") || path.endsWith(".ass")) {
            MimeTypes.TEXT_SSA
        } else if (path.endsWith(".vtt")) {
            MimeTypes.TEXT_VTT
        } else if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) {
            MimeTypes.APPLICATION_TTML
        } else {
            MimeTypes.APPLICATION_SUBRIP
        }
    }

    fun getSubtitleLanguage(uri: Uri): String? {
        val path = uri.path!!.toLowerCase()
        if (path.endsWith(".srt")) {
            val last = path.lastIndexOf(".")
            var prev = last
            for (i in last downTo 0) {
                prev = path.indexOf(".", i)
                if (prev != last) break
            }
            val len = last - prev
            if (len in 2..6) {
                // TODO: Validate lang
                return path.substring(prev + 1, last)
            }
        }
        return null
    }
}