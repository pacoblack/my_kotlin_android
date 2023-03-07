/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.homesoft.exo.extractor.avi

import androidx.annotation.VisibleForTesting
import androidx.media3.common.MimeTypes
import java.nio.ByteBuffer
import java.util.*

/**
 * Wrapper around the BITMAPINFOHEADER structure
 */
class VideoFormat(private val byteBuffer: ByteBuffer) {
    companion object {
        const val XVID = 0x44495658 // XVID
        private val STREAM_MAP = HashMap<Int, String>()

        init {
            //Although other types are technically supported, AVI is almost exclusively MP4V and MJPEG
            val mimeType = MimeTypes.VIDEO_MP4V

            //I've never seen an Android devices that actually supports MP42
            STREAM_MAP[0x3234504d] = MimeTypes.VIDEO_MP42 // MP42
            //Samsung seems to support the rare MP43.
            STREAM_MAP[0x3334504d] = MimeTypes.VIDEO_MP43 // MP43
            STREAM_MAP[0x34363248] = MimeTypes.VIDEO_H264 // H264
            STREAM_MAP[0x31637661] = MimeTypes.VIDEO_H264 // avc1
            STREAM_MAP[0x31435641] = MimeTypes.VIDEO_H264 // AVC1
            STREAM_MAP[0x44495633] = mimeType // 3VID
            STREAM_MAP[0x78766964] = mimeType // divx
            STREAM_MAP[0x58564944] = mimeType // DIVX
            STREAM_MAP[0x30355844] = mimeType // DX50
            STREAM_MAP[0x34504d46] = mimeType // FMP4
            STREAM_MAP[0x64697678] = mimeType // xvid
            STREAM_MAP[XVID] = mimeType // XVID
            STREAM_MAP[0x47504a4d] = MimeTypes.VIDEO_MJPEG // MJPG
            STREAM_MAP[0x67706a6d] = MimeTypes.VIDEO_MJPEG // mjpg
        }
    }

    // 0 - biSize - (uint)
    @set:VisibleForTesting
    var width: Int
        get() = byteBuffer.getInt(4)
        set(width) {
            byteBuffer.putInt(4, width)
        }

    @set:VisibleForTesting
    var height: Int
        get() = byteBuffer.getInt(8)
        set(height) {
            byteBuffer.putInt(8, height)
        }

    // 12 - biPlanes
    // 14 - biBitCount
    @set:VisibleForTesting
    var compression: Int
        get() = byteBuffer.getInt(16)
        set(compression) {
            byteBuffer.putInt(16, compression)
        }
    val mimeType: String?
        get() = STREAM_MAP[compression]
}