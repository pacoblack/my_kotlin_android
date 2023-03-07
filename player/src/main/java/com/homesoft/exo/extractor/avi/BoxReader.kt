package com.homesoft.exo.extractor.avi

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import com.gang.test.player.BuildConfig
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
/**
 * Reads the Boxes(Chunks) contains within a parent Box
 */
abstract class BoxReader internal constructor(override var position: Long, private val size: Int) :
    IReader {
    @JvmField
    protected val headerPeeker = HeaderPeeker()
    protected open val end: Long
    open fun getSize(): Long {
        return (size and AviExtractor.UINT_MASK.toInt()).toLong()
    }

    /**
     * Get the position of first chunk
     */
    fun getStart(): Long {
        return end - getSize()
    }

    open fun isComplete(): Boolean {
        if (BuildConfig.DEBUG && position > end) {
            Log.wtf(javaClass.simpleName, "position($position) > end($end)")
        }
        return position == end
    }

    protected fun advancePosition(bytes: Int = 8 + headerPeeker.getSize()): Boolean {
        position += bytes.toLong()
        //AVI's are byte aligned
        if (position and 1 == 1L) {
            position++
        }
        return isComplete()
    }

    @UnstableApi
    class HeaderPeeker {
        private val peakBuffer = AviExtractor.allocate(12)
        val chunkId: Int
            get() = peakBuffer.getInt(0)

        fun getSize(): Int {
            return peakBuffer.getInt(4)
        }

        val type: Int
            get() = peakBuffer.getInt(8)

        @Throws(IOException::class)
        fun peakSafe(input: ExtractorInput): Boolean {
            if (input.peekFully(peakBuffer.array(), 0, PARENT_HEADER_SIZE, true)) {
                input.resetPeekPosition()
                peakBuffer.position(PARENT_HEADER_SIZE)
                return true
            }
            return false
        }

        @Throws(IOException::class)
        fun peak(input: ExtractorInput, bytes: Int): Int {
            input.peekFully(peakBuffer.array(), 0, bytes)
            input.resetPeekPosition()
            peakBuffer.position(bytes)
            return chunkId
        }

        @Throws(IOException::class)
        fun peakType(input: ExtractorInput): Int {
            input.advancePeekPosition(CHUNK_HEADER_SIZE)
            input.peekFully(peakBuffer.array(), CHUNK_HEADER_SIZE, 4)
            input.resetPeekPosition()
            peakBuffer.position(PARENT_HEADER_SIZE)
            return type
        }
    }

    override fun toString(): String {
        return javaClass.simpleName + "{" +
                "position=" + position +
                ", start=" + position +
                ", end=" + end +
                '}'
    }

    companion object {
        const val CHUNK_HEADER_SIZE = 8
        const val PARENT_HEADER_SIZE = 12
        @JvmStatic
        @Throws(IOException::class)
        fun getByteBuffer(input: ExtractorInput, size: Int): ByteBuffer {
            //This bit of grossness makes sure that the input pointer is always aligned to a chunk
            val buffer = ByteArray(CHUNK_HEADER_SIZE + size)
            input.readFully(buffer, 0, buffer.size)
            val temp = ByteBuffer.wrap(buffer, CHUNK_HEADER_SIZE, buffer.size - CHUNK_HEADER_SIZE)
            val byteBuffer = temp.slice()
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            return byteBuffer
        }
    }

    /**
     *
     * @param start Start of first chunk usually there is a type preceding the chunk collection
     * @param size Not including the enclosing Box
     */
    init {
        end = position + size
    }
}