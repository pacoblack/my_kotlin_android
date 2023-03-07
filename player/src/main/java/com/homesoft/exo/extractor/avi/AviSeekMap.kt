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
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint

@UnstableApi
/**
 * Seek map for AVI.
 * Consists of Video chunk offsets and indexes for all streams
 */
class AviSeekMap(
    private val durationUs: Long,
    private val seekStreamHandler: StreamHandler,
    private val startPosition: Long
) : SeekMap {
    override fun isSeekable(): Boolean {
        return true
    }

    override fun getDurationUs(): Long {
        return durationUs
    }

    @VisibleForTesting
    fun getFirstSeekIndex(index: Int): Int {
        var firstIndex = -index - 2
        if (firstIndex < 0) {
            firstIndex = 0
        }
        return firstIndex
    }

    private fun getSeekPoint(seekIndex: Int): SeekPoint {
        val timeUs = seekStreamHandler.getTimeUs(seekIndex)
        val position =
            if (seekIndex == 0) startPosition else seekStreamHandler.getPosition(seekIndex)
        return SeekPoint(timeUs, position)
    }

    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val seekIndex = seekStreamHandler.getTimeUsSeekIndex(timeUs)
        if (seekIndex >= 0) {
            return SeekMap.SeekPoints(getSeekPoint(seekIndex))
        }
        val firstSeekIndex = getFirstSeekIndex(seekIndex)
        return if (firstSeekIndex + 1 < seekStreamHandler.getSeekPointCount()) {
            SeekMap.SeekPoints(
                getSeekPoint(firstSeekIndex),
                getSeekPoint(firstSeekIndex + 1)
            )
        } else {
            SeekMap.SeekPoints(getSeekPoint(firstSeekIndex))
        }
    }
}