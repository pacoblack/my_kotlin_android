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
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import java.io.IOException
import java.util.*

@UnstableApi open class ListBox(
    position: Long,
    size: Int,
    val type: Int,
    private val readerStack: Deque<IReader>
) : BoxReader(position, size), Box {
    private val list = ArrayList<Box>()

    companion object {
        const val LIST = 0x5453494c // LIST
        const val TYPE_HDRL = 0x6c726468 // hdrl - Header List
        const val TYPE_STRL = 0x6c727473 // strl - Stream List
        const val TYPE_ODML = 0x6C6D646F // odlm - OpenDML List
        private val SUPPORTED_TYPES = intArrayOf(TYPE_HDRL, TYPE_STRL, TYPE_ODML)

        init {
            Arrays.sort(SUPPORTED_TYPES)
        }
    }

    override fun getChunkId(): Int {
        return LIST
    }

    @VisibleForTesting
    fun add(box: Box) {
        list.add(box)
    }

    override fun isComplete(): Boolean {
        if (super.isComplete()) {
            for (box in list) {
                if (box is BoxReader && !(box as BoxReader).isComplete()) {
                    return false
                }
            }
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun read(input: ExtractorInput): Boolean {
        if (isComplete()) {
            return true
        }
        val chunkId = headerPeeker.peak(input, CHUNK_HEADER_SIZE)
        val size = headerPeeker.getSize()
        when (chunkId) {
            AviHeaderBox.AVIH -> add(AviHeaderBox(getByteBuffer(input, size)))
            StreamHeaderBox.STRH -> add(StreamHeaderBox(getByteBuffer(input, size)))
            StreamFormatBox.STRF -> add(StreamFormatBox(getByteBuffer(input, size)))
            StreamNameBox.STRN -> add(StreamNameBox(getByteBuffer(input, size)))
            IndexBox.INDX -> add(IndexBox(getByteBuffer(input, size)))
            ExtendedAviHeader.DMLH -> add(ExtendedAviHeader(getByteBuffer(input, size)))
            LIST -> {
                val type = headerPeeker.peakType(input)
                if (Arrays.binarySearch(SUPPORTED_TYPES, type) >= 0) {
                    val listBox =
                        ListBox(position + PARENT_HEADER_SIZE, size - 4, type, readerStack)
                    add(listBox)
                    readerStack.push(listBox)
                }
            }
        }
        return advancePosition()
    }

    val children: List<Box>
        get() = Collections.unmodifiableList(list)

    fun <T : Box?> getChild(type: Class<T>): T? {
        for (box in list) {
            if (box.javaClass == type) {
                return type.cast(box)
            }
        }
        return null
    }

    override fun toString(): String {
        return "ListBox{" +
                "type=" + AviExtractor.toString(type) +
                ", position=" + position +
                ", list=" + list +
                '}'
    }
}