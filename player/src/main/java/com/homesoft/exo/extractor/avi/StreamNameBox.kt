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
 * Box containing a human readable stream name
 */
class StreamNameBox internal constructor(byteBuffer: ByteBuffer?) : ResidentBox(STRN, byteBuffer) {
    val name: String
        get() {
            var len = byteBuffer.capacity()
            if (byteBuffer[len - 1].toInt() == 0) {
                len -= 1
            }
            val bytes = ByteArray(len)
            byteBuffer.position(0)
            byteBuffer[bytes]
            return String(bytes)
        }

    companion object {
        const val STRN = 0x6e727473 // strn
    }
}