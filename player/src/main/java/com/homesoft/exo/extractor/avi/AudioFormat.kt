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

import android.util.SparseArray
import androidx.media3.common.MimeTypes
import java.nio.ByteBuffer

/**
 * Wrapper for the WAVEFORMAT structure
 */
class AudioFormat(private val byteBuffer: ByteBuffer) {
    companion object {
        private const val WAVE_FORMAT_MPEGLAYER3 = 0x55
        private val FORMAT_MAP = SparseArray<String>()

        init {
            FORMAT_MAP.put(0x1, MimeTypes.AUDIO_RAW) // WAVE_FORMAT_PCM
            FORMAT_MAP.put(WAVE_FORMAT_MPEGLAYER3, MimeTypes.AUDIO_MPEG)
            FORMAT_MAP.put(0xff, MimeTypes.AUDIO_AAC) // WAVE_FORMAT_AAC
            FORMAT_MAP.put(0x2000, MimeTypes.AUDIO_AC3) // WAVE_FORMAT_DVM - AC3
            FORMAT_MAP.put(0x2001, MimeTypes.AUDIO_DTS) // WAVE_FORMAT_DTS2
        }
    }

    val mimeType: String
        get() = FORMAT_MAP[formatTag]
    val formatTag: Int
        get() = AviExtractor.USHORT_MASK and byteBuffer.getShort(0).toInt()
    val channels: Short
        get() = byteBuffer.getShort(2)
    val samplesPerSecond: Int
        get() = byteBuffer.getInt(4)
    val avgBytesPerSec: Int
        get() = byteBuffer.getInt(8)
    val blockAlign: Int
        get() = AviExtractor.USHORT_MASK and byteBuffer.getShort(12).toInt()
    val bitsPerSample: Short
        get() = byteBuffer.getShort(14)
    val cbSize: Int
        get() = AviExtractor.USHORT_MASK and byteBuffer.getShort(16).toInt()

    // Only valid for MPEGLAYER3WAVEFORMAT
    val id: Short
        get() = byteBuffer.getShort(18)
    val flags: Int
        get() = byteBuffer.getInt(20)
    val blockSize: Int
        get() = AviExtractor.USHORT_MASK and byteBuffer.getShort(24).toInt()
    val framesPerBlock: Int
        get() = AviExtractor.USHORT_MASK and byteBuffer.getShort(26).toInt()
    val codecData: ByteArray
        get() {
            val size = cbSize
            val temp = byteBuffer.duplicate()
            temp.clear()
            temp.position(18)
            temp.limit(18 + size)
            val data = ByteArray(size)
            temp[data]
            return data
        }

    override fun toString(): String {
        val formatTag = formatTag
        val sb = StringBuilder(
            "AudioFormat{" +
                    "formatTag=" + formatTag +
                    ", channels=" + channels +
                    ", samplesPerSecond=" + samplesPerSecond +
                    ", avgBytesPerSecond=" + avgBytesPerSec +
                    ", blockAlign=" + blockAlign +
                    ", bitsPerSample=" + bitsPerSample
        )
        if (byteBuffer.capacity() > 16) {
            // WAVEFORMATEX
            sb.append(", cbSize=")
            sb.append(cbSize)
            if (formatTag == WAVE_FORMAT_MPEGLAYER3) {
                sb.append(", ID=")
                sb.append(id.toInt())
                sb.append(", flags=")
                sb.append(flags)
                sb.append(", blockSize=")
                sb.append(blockSize)
                sb.append(", framesPerBlock=")
                sb.append(framesPerBlock)
            }
        }
        sb.append('}')
        return sb.toString()
    }
}