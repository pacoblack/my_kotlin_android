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
import androidx.media3.common.C
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.MpegAudioUtil
import androidx.media3.extractor.TrackOutput
import java.io.IOException

@UnstableApi
/**
 * This is an MP3 Extractor within the AviExtractor
 *
 * Resolves several issues with Mpeg Audio
 * 1. That muxers don't always mux MPEG audio on the frame boundary
 * 2. That some codecs can't handle multiple or partial frames (Pixels)
 */
class MpegAudioStreamHandler internal constructor(
    id: Int, durationUs: Long, trackOutput: TrackOutput,
    private val samplesPerSecond: Int
) : AudioStreamHandler(id, durationUs, trackOutput) {
    private val header = MpegAudioUtil.Header()
    private val scratch = ParsableByteArray(8)

    /**
     * Bytes remaining in the Mpeg Audio frame
     * This includes bytes in both the scratch buffer and the stream
     * 0 means we are seeking a new frame
     */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var frameRemaining = 0
        private set

    override fun advanceTime(size: Int) {
        timeUs += header.samplesPerFrame * C.MICROS_PER_SECOND / samplesPerSecond
    }

    @Throws(IOException::class)
    override fun read(input: ExtractorInput): Boolean {
        if (readSize == 0) {
            //Empty frame, just advance the clock
            advanceTime(0)
            return true
        }
        if (frameRemaining == 0) {
            //Find the next frame
            if (!findFrame(input)) {
                if (scratch.limit() >= readSize) {
                    // Couldn't find an MPEG audio frame header in chunk.
                    // Might be ID3 or leading 0s
                    // Dump the chunk as it can mess up the (Pixel) decoder
                    scratch.reset(0)
                    // Not sure if this is the right thing to do.  Maybe nothing
                    advanceTime(0)
                }
                return readComplete()
            }
        }
        val scratchBytes = scratch.bytesLeft()
        if (scratchBytes > 0) {
//      System.out.println("SampleData-scratch: " + scratchBytes);
            trackOutput.sampleData(scratch, scratchBytes)
            frameRemaining -= scratchBytes
            scratch.reset(0)
        }

//    System.out.println("SampleData-input : " + Math.min(frameRemaining, readRemaining));
        val bytes = trackOutput.sampleData(input, frameRemaining.coerceAtMost(readRemaining), false)
        frameRemaining -= bytes
        if (frameRemaining == 0) {
            sendMetadata(header.frameSize)
        }
        readRemaining -= bytes
        return readComplete()
    }

    /**
     * Soft read from input to scratch
     * @param bytes to attempt to read
     * @return [C.RESULT_END_OF_INPUT] or number of bytes read.
     */
    @Throws(IOException::class)
    fun readScratch(input: ExtractorInput, bytes: Int): Int {
        val toRead = Math.min(bytes, readRemaining)
        scratch.ensureCapacity(scratch.limit() + toRead)
        val read = input.read(scratch.data, scratch.limit(), toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            return read
        }
        readRemaining -= read
        scratch.setLimit(scratch.limit() + read)
        return read
    }

    /**
     * Attempt to find a frame header in the input
     * @return true if a frame header was found
     */
    @VisibleForTesting
    @Throws(IOException::class)
    fun findFrame(input: ExtractorInput): Boolean {
        var toRead = 4
        while (readRemaining > 0 && readScratch(input, toRead) != C.RESULT_END_OF_INPUT) {
            while (scratch.bytesLeft() >= 4) {
                if (header.setForHeaderData(scratch.readInt())) {
                    scratch.skipBytes(-4)
                    frameRemaining = header.frameSize
                    return true
                }
                scratch.skipBytes(-3)
            }
            // 16 is small, but if we end up reading multiple frames into scratch, things get complicated.
            // We should only loop on seek, so this is the lesser of the evils.
            toRead = readRemaining.coerceAtMost(16)
        }
        return false
    }

    override fun seekPosition(position: Long) {
        super.seekPosition(position)
        scratch.reset(0)
        frameRemaining = 0
    }

    companion object {
        // Number of samples in a typical MP3 Frame.
        // Usually expressed as 144 since it's multiplied by 8 bits per byte
        private const val SAMPLES_PER_FRAME_L3_V1 = 1152
    }

    init {
        //Default samples per frame to handle blank leading chunks.
        header.samplesPerFrame = SAMPLES_PER_FRAME_L3_V1
    }
}