package com.homesoft.exo.extractor.avi

import java.nio.ByteBuffer

/**
 * Optional: Total frames from the AVI
 */
class ExtendedAviHeader internal constructor(byteBuffer: ByteBuffer?) :
    ResidentBox(DMLH, byteBuffer) {
    val totalFrames: Long
        get() = (byteBuffer.getInt(0) and AviExtractor.UINT_MASK.toInt()).toLong()

    override fun toString(): String {
        return "ExtendedAviHeader{frames=$totalFrames}"
    }

    companion object {
        const val DMLH = 0x686C6D64
    }
}