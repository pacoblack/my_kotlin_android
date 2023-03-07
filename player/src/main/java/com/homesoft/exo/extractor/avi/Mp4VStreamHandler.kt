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

import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ParsableNalUnitBitArray
import androidx.media3.extractor.TrackOutput
import okhttp3.internal.and
import java.io.IOException
import kotlin.experimental.and

@UnstableApi
/**
 * Peeks an MP4V stream looking for pixelWidthHeightRatio data
 */
class Mp4VStreamHandler(
    id: Int, durationUs: Long, trackOutput: TrackOutput,
    private val formatBuilder: Format.Builder
) : NalStreamHandler(id, durationUs, trackOutput, 12) {
    @VisibleForTesting
    var pixelWidthHeightRatio = 1f
    var vopTimeIncrementBits = 0
    var vopTimeIncrementResolution = 0
    var clockOffsetUs: Long = 0
    var frameOffsetUs: Long = 0

    // modulo_time_base from the last I/P frame, used to calculate the time offset of B-frames
    var priorModulo = 0
    public override fun skip(nalType: Byte): Boolean {
        return nalType != SEQUENCE_START_CODE && (!useStreamClock || nalType != VOP_START_CODE)
    }

    public override fun reset() {
        frameOffsetUs = 0L
        clockOffsetUs = frameOffsetUs
    }

    public override var timeUs: Long
        set(value) {}
        get() {
            return if (useStreamClock) {
                super.timeUs + frameOffsetUs
            } else {
                super.timeUs
            }
        }

    override fun advanceTime() {
        if (!useStreamClock) {
            super.advanceTime()
        }
    }

    fun readMarkerBit(`in`: ParsableNalUnitBitArray) {
        check(`in`.readBit()) { "Marker Bit false" }
    }

    @VisibleForTesting
    fun parseVideoObjectLayer(nalTypeOffset: Int) {
        val `in` = ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, pos)
        `in`.skipBit() // random_accessible_vol
        `in`.skipBits(8) // video_object_type_indication
        val is_object_layer_identifier = `in`.readBit()
        var video_object_layer_verid = 0
        if (is_object_layer_identifier) {
            video_object_layer_verid = `in`.readBits(4)
            `in`.skipBits(3) // video_object_layer_priority
        }
        val aspect_ratio_info = `in`.readBits(4)
        val aspectRatio: Float
        aspectRatio = if (aspect_ratio_info == Extended_PAR) {
            val par_width = `in`.readBits(8).toFloat()
            val par_height = `in`.readBits(8).toFloat()
            par_width / par_height
        } else {
            ASPECT_RATIO[aspect_ratio_info]
        }
        if (aspectRatio != pixelWidthHeightRatio) {
            trackOutput.format(formatBuilder.setPixelWidthHeightRatio(aspectRatio).build())
            pixelWidthHeightRatio = aspectRatio
        }
        //vol_control_parameters
        if (`in`.readBit()) {
            `in`.skipBits(2 + 1) //chroma_format, low_delay
            //vbv_parameters
            if (`in`.readBit()) {
                `in`.skipBits(15 + 1 + 15 + 1 + 1 + 3 + 11 + 1 + 15 + 1) //first_half_bit_rate...
            }
        }
        val video_object_layer_shape = `in`.readBits(2)
        if (video_object_layer_shape == SHAPE_TYPE_GRAYSCALE && video_object_layer_verid != 1) {
            `in`.skipBits(4) //video_object_layer_shape_extension
        }
        readMarkerBit(`in`)
        vopTimeIncrementResolution = `in`.readBits(16)
        vopTimeIncrementBits =
            (Math.log(vopTimeIncrementResolution.toDouble()) / Math.log(2.0)).toInt() + 1
    }

    fun parseVideoObjectPlane(nalTypeOffset: Int) {
        val `in` = ParsableNalUnitBitArray(buffer, nalTypeOffset + 1, buffer.size)
        val vop_coding_type = `in`.readBits(2)
        // Usually this is 0, but on clock advance is 1
        var modulo_time_base = 0
        while (`in`.readBit()) {
            modulo_time_base++
        }
        readMarkerBit(`in`)
        val vop_time_increment = `in`.readBits(vopTimeIncrementBits)
        var frameUs = C.MICROS_PER_SECOND * vop_time_increment / vopTimeIncrementResolution
        if (vop_coding_type == VOP_TYPE_B) {
            if (priorModulo != modulo_time_base) {
                // Subtract the modulo delta from the clock offset.
                frameUs -= (priorModulo - modulo_time_base) * C.MICROS_PER_SECOND
            }
        } else {
            priorModulo = modulo_time_base
            if (modulo_time_base != 0) {
                clockOffsetUs += modulo_time_base * C.MICROS_PER_SECOND
            }
        }
        frameOffsetUs = clockOffsetUs + frameUs
    }

    @Throws(IOException::class)
    public override fun processChunk(input: ExtractorInput, nalTypeOffset: Int) {
        var nalTypeOffset = nalTypeOffset
        while (true) {
            val nalType = buffer[nalTypeOffset]
            if (useStreamClock && nalType == VOP_START_CODE) {
                parseVideoObjectPlane(nalTypeOffset)
                break
            } else if (nalType == SEQUENCE_START_CODE) {
                val profileAndLevelIndication = buffer[nalTypeOffset + 1]
                useStreamClock =
                    enableStreamClock && (SIMPLE_PROFILE_MASK.and(profileAndLevelIndication) != profileAndLevelIndication)
            } else if (0xf0 and nalType.toInt() == LAYER_START_CODE) {
                //Read the whole NAL into the buffer
                seekNextNal(input, nalTypeOffset)
                parseVideoObjectLayer(nalTypeOffset)
                // There may be a VOP start code after this NAL, so if we are tracking B frames, don't exit
                nalTypeOffset = if (useStreamClock) {
                    // Due to seekNextNal() above the pointer should be at the next NAL offset
                    0
                } else {
                    break
                }
            }
            nalTypeOffset = seekNextNal(input, nalTypeOffset)
            if (nalTypeOffset < 0) {
                break
            }
            compact()
        }
    }

    companion object {
        @VisibleForTesting
        val SEQUENCE_START_CODE = 0xb0.toByte()

        @VisibleForTesting
        val VOP_START_CODE = 0xb6.toByte()

        @VisibleForTesting
        val LAYER_START_CODE = 0x20
        private val ASPECT_RATIO = floatArrayOf(0f, 1f, 12f / 11f, 10f / 11f, 16f / 11f, 40f / 33f)
        private const val SIMPLE_PROFILE_MASK: Byte = 15
        private const val SHAPE_TYPE_GRAYSCALE = 3
        const val VOP_TYPE_B = 2

        @VisibleForTesting
        val Extended_PAR = 0xf

        // This chipset(device?) seems to correct the clock for B-Frame inside the codec.
        // Not sure if it's the whole series (MediaTek P35) or just this chipset.
        private val enableStreamClock = Build.HARDWARE != "mt6765" //Samsung A7 Lite
    }
}