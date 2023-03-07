package com.homesoft.exo.extractor.avi

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.TrackOutput
import java.util.*

open class AudioStreamHandler internal constructor(
    id: Int,
    durationUs: Long,
    trackOutput: TrackOutput
) : StreamHandler(id, TYPE_AUDIO, durationUs, trackOutput) {
    var times = LongArray(0)

    /**
     * Current time in the stream
     */

    private fun calcTimeUs(streamPosition: Long): Long {
        return durationUs * streamPosition / chunkIndex.size
    }

    protected open fun advanceTime(sampleSize: Int) {
        timeUs += calcTimeUs(sampleSize.toLong())
    }

    @UnstableApi override fun sendMetadata(size: Int) {
        if (size > 0) {
            //System.out.println("AudioStream: " + getId() + " Us: " + getTimeUs() + " size: " + size);
            trackOutput.sampleMetadata(
                timeUs, C.BUFFER_FLAG_KEY_FRAME, size, 0, null
            )
        }
        advanceTime(size)
    }

    private fun setSeekFrames(seekFrameIndices: IntArray) {
        setSeekPointSize(seekFrameIndices.size)
        val chunks = chunkIndex.count
        var k = 0
        var streamBytes: Long = 0
        for (c in 0 until chunks) {
            if (seekFrameIndices[k] == c) {
                positions[k] = chunkIndex.getChunkPosition(c)
                times[k] = calcTimeUs(streamBytes)
                k++
                if (k == positions.size) {
                    //We have moved beyond this streams length
                    break
                }
            }
            streamBytes += chunkIndex.getChunkSize(c).toLong()
        }
        chunkIndex.release()
    }

    override fun setSeekStream(): LongArray {
        setSeekFrames(chunkIndex.getChunkSubset(durationUs, 3))
        return positions
    }

    fun setSeekFrames(positions: LongArray) {
        setSeekFrames(chunkIndex.getIndices(positions))
    }

    public override var timeUs: Long = 0

    override fun seekPosition(position: Long) {
        val seekIndex = getSeekIndex(position)
        timeUs = times[seekIndex]
    }

    override fun setSeekPointSize(seekPointCount: Int) {
        super.setSeekPointSize(seekPointCount)
        times = LongArray(seekPointCount)
    }

    public override fun getTimeUsSeekIndex(timeUs: Long): Int {
        return Arrays.binarySearch(times, timeUs)
    }

    override fun getTimeUs(seekIndex: Int): Long {
        return times[seekIndex]
    }
}