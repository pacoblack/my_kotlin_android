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

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Wrapper around the AVISTREAMHEADER structure
 */
class StreamHeaderBox internal constructor(byteBuffer: ByteBuffer?) :
    ResidentBox(STRH, byteBuffer) {
    val isAudio: Boolean
        get() = steamType == AUDS
    val isVideo: Boolean
        get() = steamType == VIDS
    val frameRate: Float
        get() = rate / scale.toFloat()
    val durationUs: Long
        @UnstableApi get() = C.MICROS_PER_SECOND * scale * length / rate
    val steamType: Int
        get() = byteBuffer.getInt(0)

    //4 - fourCC
    //8 - dwFlags
    //12 - wPriority
    //14 - wLanguage
    val initialFrames: Int
        get() = byteBuffer.getInt(16)
    val scale: Int
        get() = byteBuffer.getInt(20)
    val rate: Int
        get() = byteBuffer.getInt(24)

    //28 - dwStart - doesn't seem to ever be set
    val length: Int
        get() = byteBuffer.getInt(32)
    val suggestedBufferSize: Int
        get() = byteBuffer.getInt(36)

    //40 - dwQuality
    //44 - dwSampleSize
    override fun toString(): String {
        return "scale=$scale rate=$rate length=$length us=$durationUs"
    }

    companion object {
        const val STRH = 0x68727473 // strh

        //Audio Stream
        const val AUDS = 0x73647561 // auds

        //Videos Stream
        const val VIDS = 0x73646976 // vids
    }
}