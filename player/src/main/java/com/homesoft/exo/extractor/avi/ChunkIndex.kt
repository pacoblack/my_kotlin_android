package com.homesoft.exo.extractor.avi

import androidx.annotation.VisibleForTesting
import java.util.*

/**
 * Used to parse Indexes and build the SeekMap
 * In this class position is absolute file position
 */
class ChunkIndex {
    val keyFrames = BitSet()

    @VisibleForTesting
    var positions = LongArray(8)
    var sizes = IntArray(8)

    /**
     * Chunks in the stream
     */
    @set:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var count = 0

    /**
     * Total size of the stream
     */
    var size: Long = 0
        private set

    /**
     * Add a chunk
     * @param key key frame
     */
    fun add(position: Long, size: Int, key: Boolean) {
        if (positions.size <= count) {
            checkReleased()
            grow()
        }
        positions[count] = position
        sizes[count] = size
        this.size += size.toLong()
        if (key) {
            keyFrames.set(count)
        }
        count++
    }

    val isAllKeyFrames: Boolean
        get() = keyFrames.cardinality() == count
    val keyFrameCount: Int
        get() = keyFrames.cardinality()

    /**
     * Get the key frame indices
     * @return the key frame indices or [.ALL_KEY_FRAMES]
     */
    val chunkSubset: IntArray
        get() {
            checkReleased()
            return if (isAllKeyFrames) {
                ALL_KEY_FRAMES
            } else {
                val keyFrameIndices = IntArray(keyFrameCount)
                var i = 0
                for (f in 0 until count) {
                    if (keyFrames[f]) {
                        keyFrameIndices[i++] = f
                    }
                }
                keyFrameIndices
            }
        }

    /**
     * Used for creating the SeekMap
     * @param seekPositions array of positions, usually key frame positions of another stream
     * @return the chunk indices after the seekPosition (next frame).
     */
    fun getIndices(seekPositions: LongArray): IntArray {
        checkReleased()
        val work = IntArray(seekPositions.size)
        var i = 0
        val maxI = count - 1
        // Start p at 1, so seekPosition[0] is always mapped to index 0
        for (p in 1 until seekPositions.size) {
            while (i < maxI && positions[i] < seekPositions[p]) {
                i++
            }
            work[p] = i
        }
        return work
    }

    fun getChunkPosition(index: Int): Long {
        return positions[index]
    }

    fun getChunkSize(index: Int): Int {
        return sizes[index]
    }

    /**
     * Build a subset of chunk indices given the stream duration and a chunk rate per second
     * Useful for generating a sparse set of key frames
     * @param durationUs stream length in Us
     * @param chunkRate secs between chunks
     */
    fun getChunkSubset(durationUs: Long, chunkRate: Int): IntArray {
        checkReleased()
        val chunkDurUs = durationUs / count
        val chunkRateUs = chunkRate * 1000000L
        val work = IntArray(count) //This is overkill, but keeps the logic simple.
        var clockUs: Long = 0
        var nextChunkUs: Long = 0
        var k = 0
        for (f in 0 until count) {
            if (clockUs >= nextChunkUs) {
                work[k++] = f
                nextChunkUs += chunkRateUs
            }
            clockUs += chunkDurUs
        }
        return work.copyOf(k)
    }

    private fun checkReleased() {
        check(positions != RELEASED) { "ChunkIndex released." }
    }

    /**
     * Release the arrays at this point only getChunks() and isAllKeyFrames() are allowed
     */
    fun release() {
        positions = RELEASED
        keyFrames.clear()
    }

    private fun grow() {
        val newLength = positions.size * 5 / 4
        positions = positions.copyOf(newLength)
        sizes = sizes.copyOf(newLength)
    }

    companion object {
        val ALL_KEY_FRAMES = IntArray(0)
        private val RELEASED = LongArray(0)
    }
}