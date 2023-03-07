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

/**
 * Handles chunk data from a given stream.
 * This acts a bridge between AVI and ExoPlayer
 */
abstract class StreamHandler internal constructor(
    id: Int,
    chunkType: Int,
    var durationUs: Long,
    @JvmField val trackOutput: TrackOutput
) : IReader {

    /**
     * The chunk id as it appears in the index and the movi
     */
    private val chunkId: Int = getChunkIdLower(id) or chunkType
//    @JvmField
//    open var durationUs: Long

    /**
     * Seek point variables
     */
    @JvmField
    var positions = LongArray(0)
    //  /**
    //   * Size total size of the stream in bytes calculated by the index
    //   */
    //  int size;
    /**
     * Open DML IndexBox, currently we just support one, but multiple are possible
     * Will be null if non-exist or if processed (to ChunkIndex)
     */
    var indexBox: IndexBox? = null
    var chunkIndex = ChunkIndex()
        protected set

    /**
     * Size of the current chunk in bytes
     */
    @JvmField
    @Transient
    var readSize = 0

    /**
     * Bytes remaining in the chunk to be processed
     */
    @JvmField
    @Transient
    var readRemaining = 0

    @JvmField
    @Transient
    var readEnd: Long = 0

    /**
     *
     * @return true if this can handle the chunkId
     */
    open fun handlesChunkId(chunkId: Int): Boolean {
        return this.chunkId == chunkId
    }

    abstract var timeUs: Long
    abstract fun seekPosition(position: Long)
    override val position: Long
        get() = readEnd - readRemaining

    fun setRead(position: Long, size: Int) {
        readEnd = position + size
        readSize = size
        readRemaining = readSize
    }

    protected fun readComplete(): Boolean {
        return readRemaining == 0
    }

    @UnstableApi /**
     * Resume a partial read of a chunk
     * May be called multiple times
     */
    @Throws(IOException::class)
    override fun read(input: ExtractorInput): Boolean {
        readRemaining -= trackOutput.sampleData(input, readRemaining, false)
        return if (readComplete()) {
            sendMetadata(readSize)
            true
        } else {
            false
        }
    }

    /**
     * Done reading a chunk.  Send the timing info and advance the clock
     * @param size the amount of data passed to the trackOutput
     */
    protected abstract fun sendMetadata(size: Int)

    /**
     * Set this stream as the primary seek stream
     * Populate our seekPosition
     * @return the positions of seekFrames
     */
    abstract fun setSeekStream(): LongArray?

    /**
     * Perform a BinarySearch to get the correct index
     * @return the exact match or a negative as defined in Arrays.binarySearch()
     */
    abstract fun getTimeUsSeekIndex(timeUs: Long): Int
    abstract fun getTimeUs(seekIndex: Int): Long

    /**
     * Gets the streamId.
     * @return The unique stream id for this file
     */
    fun getId():Int{
        return getId(chunkId)
    }

    protected open fun setSeekPointSize(seekPointCount: Int) {
        positions = LongArray(seekPointCount)
    }

    fun getSeekPointCount(): Int {
        return positions.size
    }

    fun getPosition(index: Int): Long {
        return positions[index]
    }

    protected fun getValidSeekIndex(index: Int): Int {
        var index = index
        if (index < 0) {
            index = -index - 1
            if (index >= positions.size) {
                index = positions.size - 1
            }
        }
        return index
    }

    protected fun getSeekIndex(position: Long): Int {
        return if (position == 0L) {
            0
        } else getValidSeekIndex(Arrays.binarySearch(positions, position))
    }

    override fun toString(): String {
        return javaClass.simpleName + "{position=" + position + "}"
    }

    companion object {
        const val TYPE_VIDEO = 'd'.code shl 16 or ('c'.code shl 24)
        const val TYPE_AUDIO = 'w'.code shl 16 or ('b'.code shl 24)

        /**
         * Get stream id in ASCII
         */
        @JvmStatic
        protected fun getChunkIdLower(id: Int): Int {
            val tens = id / 10
            val ones = id % 10
            return '0'.code + tens or ('0'.code + ones shl 8)
        }

        fun getId(chunkId: Int): Int {
            return (chunkId shr 8 and 0xf) + (chunkId and 0xf) * 10
        }
    }

}