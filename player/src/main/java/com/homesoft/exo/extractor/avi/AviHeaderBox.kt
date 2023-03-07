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

import java.nio.ByteBuffer

/**
 * Wrapper around the AVIMAINHEADER structure
 */
class AviHeaderBox internal constructor(byteBuffer: ByteBuffer?) : ResidentBox(AVIH, byteBuffer) {
    val microSecPerFrame: Long
        get() = (byteBuffer.getInt(0) and AviExtractor.UINT_MASK.toInt()).toLong()

    //4 = dwMaxBytesPerSec
    //8 = dwPaddingGranularity - Always 0, but should be 2
    fun hasIndex(): Boolean {
        return flags and AVIF_HASINDEX == AVIF_HASINDEX
    }

    fun mustUseIndex(): Boolean {
        return flags and AVIF_MUSTUSEINDEX == AVIF_MUSTUSEINDEX
    }

    val flags: Int
        get() = byteBuffer.getInt(12)
    val totalFrames: Int
        get() = byteBuffer.getInt(16)

    // 20 - dwInitialFrames
    // 28 - dwSuggestedBufferSize
    val streams: Int
        get() = byteBuffer.getInt(24)

    // 32 - dwWidth
    // 36 - dwHeight
    companion object {
        const val LEN = 0x38
        const val AVIF_HASINDEX = 0x10
        private const val AVIF_MUSTUSEINDEX = 0x20
        const val AVIH = 0x68697661 // avih
    }
}