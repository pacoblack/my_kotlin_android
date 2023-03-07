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

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.TrackOutput
import java.io.IOException
import java.util.*

@UnstableApi
/**
 * Generic base class for NAL (0x00 0x00 0x01) chunk headers
 * Theses are used by AVC and MP4V (XVID)
 */
abstract class NalStreamHandler internal constructor(
    id: Int, durationUs: Long, trackOutput: TrackOutput,
    peakSize: Int
) : VideoStreamHandler(id, durationUs, trackOutput) {
    private val peekSize: Int

    @Transient
    private var remaining = 0

    @Transient
    var buffer: ByteArray = ByteArray(0)

    @Transient
    var pos = 0

    /**
     * True if we are using the clock data in the stream
     */
    protected var useStreamClock = false
    override fun seekPosition(position: Long) {
        super.seekPosition(position)
        if (useStreamClock) {
            reset()
        }
    }

    @Throws(IOException::class)
    abstract fun processChunk(input: ExtractorInput, nalTypeOffset: Int)

    /**
     *
     * @return NAL offset from pos
     */
    private val nalTypeOffset: Int
        private get() {
            if (buffer[pos].toInt() == 0 && buffer[pos + 1].toInt() == 0) {
                if (buffer[pos + 2].toInt() == 1) {
                    return 3
                } else if (buffer[pos + 2].toInt() == 0 && buffer[pos + 3].toInt() == 1) {
                    return 4
                }
            }
            return -1
        }

    /**
     * Look for the next NAL in buffer, incrementing pos
     * @return offset of the nal from the pos
     */
    private fun seekNal(): Int {
        var nalOffset: Int
        while (nalTypeOffset.also { nalOffset = it } < 0 && pos < buffer.size - 5) {
            pos++
        }
        return nalOffset
    }

    /**
     * Removes everything before the pos
     */
    fun compact() {
        //Compress down to the last NAL
        val newBuffer = ByteArray(buffer.size - pos)
        System.arraycopy(buffer, pos, newBuffer, 0, newBuffer.size)
        buffer = newBuffer
        pos = 0
    }

    /**
     * @param peekSize number of bytes to append
     */
    @Throws(IOException::class)
    fun append(input: ExtractorInput, peekSize: Int) {
        val oldLength = buffer.size
        buffer = buffer.copyOf(oldLength + peekSize)
        input.peekFully(buffer, oldLength, peekSize)
        remaining -= peekSize
    }

    /**
     *
     * @return NAL offset from pos, -1 if end of input
     */
    @Throws(IOException::class)
    fun seekNextNal(input: ExtractorInput, skip: Int): Int {
        pos += skip
        while (pos + 5 < buffer.size || remaining > 0) {
            if (buffer.size - pos < SEEK_PEEK_SIZE && remaining > 0) {
                append(input, Math.min(SEEK_PEEK_SIZE, remaining))
            }
            val nalOffset = seekNal()
            if (nalOffset > 0) {
                return nalOffset
            }
        }
        pos = buffer.size
        return -1
    }

    abstract fun skip(nalType: Byte): Boolean
    abstract fun reset()
    @Throws(IOException::class)
    override fun read(input: ExtractorInput): Boolean {
        if (readSize == readRemaining) {
            peek(input, readSize)
        }
        return super.read(input)
    }

    @Throws(IOException::class)
    fun peek(input: ExtractorInput, size: Int) {
        buffer = ByteArray(peekSize)
        if (!input.peekFully(buffer, 0, peekSize, true)) {
            return
        }
        pos = 0
        val nalTypeOffset = nalTypeOffset
        if (nalTypeOffset < 0 || skip(buffer[nalTypeOffset])) {
            input.resetPeekPosition()
            return
        }
        remaining = size - peekSize
        processChunk(input, nalTypeOffset)
        input.resetPeekPosition()
    }

    companion object {
        private const val SEEK_PEEK_SIZE = 256
    }

    init {
        require(peakSize >= 5) { "Peak size must at least be 5" }
        peekSize = peakSize
    }
}