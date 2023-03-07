package com.homesoft.exo.extractor.avi

import java.nio.ByteBuffer
import java.util.*

/**
 * Open DML Index Box
 */
class IndexBox internal constructor(byteBuffer: ByteBuffer?) : ResidentBox(INDX, byteBuffer) {
    val longsPerEntry: Int
        get() = 0xffff and byteBuffer.getShort(0).toInt()
    val indexType: Byte
        get() = byteBuffer[3]
    val entriesInUse: Int
        get() = byteBuffer[4].toInt()

    //8 = IndexChunkId
    val positions: List<Long>
        get() {
            val entriesInUse = entriesInUse
            val list = ArrayList<Long>(
                entriesInUse
            )
            val entrySize = longsPerEntry * 4
            for (i in 0 until entriesInUse) {
                list.add(byteBuffer.getLong(0x18 + i * entrySize))
            }
            return list
        }

    companion object {
        const val INDX = 0x78646E69

        //Supported IndexType(s)
        const val AVI_INDEX_OF_INDEXES: Byte = 0
        const val AVI_INDEX_OF_CHUNKS: Byte = 1
    }
}