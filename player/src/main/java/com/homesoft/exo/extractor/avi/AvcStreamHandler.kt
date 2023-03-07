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
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.NalUnitUtil
import androidx.media3.extractor.ParsableNalUnitBitArray
import androidx.media3.extractor.TrackOutput
import java.io.IOException

@UnstableApi
/**
 * Corrects the time and PAR for H264 streams
 * AVC is very rare in AVI due to the rise of the mp4 container
 */
class AvcStreamHandler(
    id: Int, durationUs: Long, trackOutput: TrackOutput,
    private val formatBuilder: Format.Builder
) : NalStreamHandler(id, durationUs, trackOutput, 16) {
    private var pixelWidthHeightRatio = 1f

    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var spsData: NalUnitUtil.SpsData? = null
        private set

    //The frame as a calculated from the picCount
    @VisibleForTesting
    var picOffset = 0

    @VisibleForTesting
    var lastPicCount = 0

    @VisibleForTesting
    var maxPicCount = 0
    private var step = 2
    private var posHalf = 0
    private var negHalf = 0
    public override fun skip(nalType: Byte): Boolean {
        return if (useStreamClock) {
            false
        } else {
            //If the clock is ChunkClock, skip "normal" frames
            nalType in 0..NAL_TYPE_IDR
        }
    }

    /**
     * Greatly simplified way to calculate the picOrder
     * Full logic is here
     * https://chromium.googlesource.com/chromium/src/media/+/refs/heads/main/video/h264_poc.cc
     */
    fun updatePicCountClock(nalTypeOffset: Int) {
        val `in` = ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, buffer.size)
        //slide_header()
        `in`.readUnsignedExpGolombCodedInt() //first_mb_in_slice
        `in`.readUnsignedExpGolombCodedInt() //slice_type
        `in`.readUnsignedExpGolombCodedInt() //pic_parameter_set_id
        if (spsData!!.separateColorPlaneFlag) {
            `in`.skipBits(2) //colour_plane_id
        }
        val frameNum = `in`.readBits(spsData!!.frameNumLength) //frame_num
        if (!spsData!!.frameMbsOnlyFlag) {
            val field_pic_flag = `in`.readBit() // field_pic_flag
            if (field_pic_flag) {
                `in`.readBit() // bottom_field_flag
            }
        }
        //We skip IDR in the switch
        if (spsData!!.picOrderCountType == 0) {
            val picOrderCountLsb = `in`.readBits(spsData!!.picOrderCntLsbLength)
            setPicCount(picOrderCountLsb)
        } else if (spsData!!.picOrderCountType == 2) {
            setPicCount(frameNum)
        }
    }

    @VisibleForTesting
    @Throws(IOException::class)
    fun readSps(input: ExtractorInput, nalTypeOffset: Int): Int {
        var nalTypeOffset = nalTypeOffset
        val spsStart = nalTypeOffset + 1
        nalTypeOffset = seekNextNal(input, spsStart)
        spsData = NalUnitUtil.parseSpsNalUnitPayload(buffer, spsStart, pos)
        //If we can have B Frames, upgrade to PicCountClock
        if (spsData!!.maxNumRefFrames > 1 && !useStreamClock) {
            useStreamClock = true
            reset()
        }
        if (useStreamClock) {
            if (spsData!!.picOrderCountType == 0) {
                setMaxPicCount(1 shl spsData!!.picOrderCntLsbLength, 2)
            } else if (spsData!!.picOrderCountType == 2) {
                //Plus one because we double the frame number
                setMaxPicCount(1 shl spsData!!.frameNumLength, 1)
            }
        }
        if (spsData!!.pixelWidthHeightRatio != pixelWidthHeightRatio) {
            pixelWidthHeightRatio = spsData!!.pixelWidthHeightRatio
            formatBuilder.setPixelWidthHeightRatio(pixelWidthHeightRatio)
            trackOutput.format(formatBuilder.build())
        }
        return nalTypeOffset
    }

    @Throws(IOException::class)
    public override fun processChunk(input: ExtractorInput, nalTypeOffset: Int) {
        var nalTypeOffset = nalTypeOffset
        while (true) {
            val nalType: Int =  NAL_TYPE_MASK and buffer[nalTypeOffset].toInt()
            nalTypeOffset = when (nalType) {
                1, 2, 3, 4 -> {
                    if (useStreamClock) {
                        updatePicCountClock(nalTypeOffset)
                    }
                    return
                }
                NAL_TYPE_IDR -> {
                    if (useStreamClock) {
                        reset()
                    }
                    return
                }
                NAL_TYPE_AUD, NAL_TYPE_SEI, NAL_TYPE_PPS -> {
                    seekNextNal(input, nalTypeOffset)
                }
                NAL_TYPE_SPS -> readSps(input, nalTypeOffset)
                else -> return
            }
            if (nalTypeOffset < 0) {
                return
            }
            compact()
        }
    }

    /**
     * Reset the clock
     */
    public override fun reset() {
        picOffset = 0
        lastPicCount = picOffset
    }

    override fun advanceTime() {
        super.advanceTime()
        if (useStreamClock) {
            picOffset--
        }
    }

    override var timeUs: Long
        set(value) {}
        get()  {
            return if (useStreamClock) {
                getChunkTimeUs(index + picOffset)
            } else {
                super.timeUs
         }
    }

    fun setMaxPicCount(maxPicCount: Int, step: Int) {
        this.maxPicCount = maxPicCount
        this.step = step
        posHalf = maxPicCount / 2
        negHalf = -posHalf
    }

    fun setPicCount(picCount: Int) {
        var delta = picCount - lastPicCount
        if (delta < negHalf) {
            delta += maxPicCount
        } else if (delta > posHalf) {
            delta -= maxPicCount
        }
        picOffset += delta / step
        lastPicCount = picCount
    }

    companion object {
        private const val NAL_TYPE_MASK = 0x1f
        private const val NAL_TYPE_IDR = 5 //I Frame
        private const val NAL_TYPE_SEI = 6
        private const val NAL_TYPE_SPS = 7
        private const val NAL_TYPE_PPS = 8
        private const val NAL_TYPE_AUD = 9
    }
}