package com.homesoft.exo.extractor.avi

import androidx.annotation.VisibleForTesting
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.TrackOutput
import java.util.*

@UnstableApi open class VideoStreamHandler internal constructor(
    id: Int,
    durationUs: Long,
    trackOutput: TrackOutput
) : StreamHandler(id, TYPE_VIDEO, durationUs, trackOutput) {
    private var frameUs: Long = 0
    protected var index = 0
    private var allKeyFrames = false

    @VisibleForTesting
    var indices = IntArray(0)

    /**
     * Secondary chunk id.  Bad muxers sometimes use uncompressed for key frames
     */
    private val chunkIdAlt: Int = getChunkIdLower(id) or ('d'.toInt() shl 16) or ('b'.toInt() shl 24)
    override fun handlesChunkId(chunkId: Int): Boolean {
        return super.handlesChunkId(chunkId) || chunkIdAlt == chunkId
    }

    override var timeUs: Long
        get() =  getChunkTimeUs(index)
        set(value) {}

    // -8 because the position array includes the header, but the read skips it.
    protected val isKeyFrame: Boolean
        protected get() =// -8 because the position array includes the header, but the read skips it.
            allKeyFrames || Arrays.binarySearch(positions, readEnd - readSize - 8) >= 0

    protected open fun advanceTime() {
        index++
    }

    override fun sendMetadata(size: Int) {
        if (size > 0) {
            //System.out.println("VideoStream: " + getId() + " Us: " + getTimeUs() + " size: " + size + " key: " + isKeyFrame());
            trackOutput.sampleMetadata(
                timeUs, if (isKeyFrame) C.BUFFER_FLAG_KEY_FRAME else 0, size, 0, null
            )
        }
        advanceTime()
    }

    override fun setSeekStream(): LongArray? {
        val seekFrameIndices: IntArray
        if (chunkIndex.isAllKeyFrames) {
            allKeyFrames = true
            seekFrameIndices = chunkIndex.getChunkSubset(durationUs, 3)
        } else {
            seekFrameIndices = chunkIndex.chunkSubset
        }
        val frames = chunkIndex.count
        frameUs = durationUs / frames
        setSeekPointSize(seekFrameIndices.size)
        for (i in seekFrameIndices.indices) {
            val index = seekFrameIndices[i]
            positions[i] = chunkIndex.getChunkPosition(index)
            indices[i] = index
        }
        chunkIndex.release()
        return positions
    }

    /**
     * Get the stream time for a chunk index
     * @param index the index of chunk in the stream
     */
    protected fun getChunkTimeUs(index: Int): Long {
        return durationUs * index / chunkIndex.count
    }

    override fun getTimeUs(seekIndex: Int): Long {
        return if (seekIndex == 0) {
            0L
        } else getChunkTimeUs(indices[seekIndex])
    }

//    protected override fun getTimeUs(): Long {
//
//    }


    override fun getTimeUsSeekIndex(timeUs: Long): Int {
        if (timeUs == 0L) {
            return 0
        }
        val index = (timeUs / frameUs).toInt()
        val seekIndex = Arrays.binarySearch(indices, index)
        if (seekIndex >= 0) {
            // The search rounds down to the nearest chunk time,
            // if we aren't an exact time match fix up the result
            if (getChunkTimeUs(indices[seekIndex]) != timeUs) {
                return -seekIndex - 1
            }
        }
        return seekIndex
    }

    override fun seekPosition(position: Long) {
        val seekIndex = getSeekIndex(position)
        index = indices[seekIndex]
    }

    override fun setSeekPointSize(seekPointCount: Int) {
        super.setSeekPointSize(seekPointCount)
        indices = IntArray(seekPointCount)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun setFps(fps: Int) {
        val chunks = (durationUs * fps / C.MICROS_PER_SECOND).toInt()
        chunkIndex.count = chunks
        frameUs = C.MICROS_PER_SECOND / fps
    }

}