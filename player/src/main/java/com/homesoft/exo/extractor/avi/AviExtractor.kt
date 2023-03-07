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
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.*
import com.homesoft.exo.extractor.avi.AviHeaderBox
import com.homesoft.exo.extractor.avi.BoxReader.Companion.getByteBuffer
import com.homesoft.exo.extractor.avi.BoxReader.HeaderPeeker
import com.homesoft.exo.extractor.avi.ExtendedAviHeader
import com.homesoft.exo.extractor.avi.IndexBox
import com.homesoft.exo.extractor.avi.StreamFormatBox
import com.homesoft.exo.extractor.avi.StreamHeaderBox
import com.homesoft.exo.extractor.avi.StreamNameBox
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

@UnstableApi
/**
 * Extractor based on the official MicroSoft spec
 * https://docs.microsoft.com/en-us/windows/win32/directshow/avi-riff-file-reference
 */
class AviExtractor : Extractor {
    @VisibleForTesting
    val readerStack: Deque<IReader> = ArrayDeque(4)

    @VisibleForTesting
    val moviList = ArrayList<MoviBox>()

    @VisibleForTesting
    var output: ExtractorOutput? = null

    /**
     * From the AviHeader
     */
    var duration = C.TIME_UNSET
        private set

    /**
     * ChunkHandlers by StreamId
     */
    private var streamHandlers = arrayOfNulls<StreamHandler>(0)

    @VisibleForTesting
    var seekMap: SeekMap? = null

    @Throws(IOException::class)
    override fun sniff(input: ExtractorInput): Boolean {
        val headerPeeker = HeaderPeeker()
        headerPeeker.peak(input, BoxReader.PARENT_HEADER_SIZE)
        return headerPeeker.chunkId == RIFF && headerPeeker.type == AVI_
    }

    /**
     * Build and set the SeekMap based on the indices
     */
    private fun buildSeekMap() {
        var maxStreamDurationUs: Long = 0
        for (streamHandler in streamHandlers) {
            if (streamHandler is AudioStreamHandler) {
                if ((streamHandler.durationUs - duration) / duration.toFloat() > .05f) {
                    w("Audio #" + streamHandler.getId() + " duration is off, using videoDuration")
                    streamHandler.durationUs = duration
                }
            }
            maxStreamDurationUs = Math.max(maxStreamDurationUs, streamHandler!!.durationUs)
        }
        val seekStreamHandler = seekStreamHandler
        if (seekStreamHandler == null) {
            setSeekMap(SeekMap.Unseekable(duration))
            w("No video track found")
            return
        }
        val positions = seekStreamHandler.setSeekStream()
        for (streamHandler in streamHandlers) {
            // Currently, only Audio streams can be secondary.
            if (streamHandler is AudioStreamHandler && streamHandler !== seekStreamHandler) {
                positions?.let { streamHandler.setSeekFrames(it) }
            }
        }
        // The AviHeader value can have rounding errors, so use the max stream duration if it's larger
        setSeekMap(
            AviSeekMap(
                maxStreamDurationUs.coerceAtLeast(duration),
                seekStreamHandler, moviList[0].getStart()
            )
        )
    }

    @JvmName("setSeekMap1")
    @VisibleForTesting
    fun setSeekMap(seekMap: SeekMap?) {
        this.seekMap = seekMap
        output!!.seekMap(seekMap!!)
        //Parsing complete, load movi(s)
        seek(0L, 0L)
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        readerStack.add(RootReader())
    }

    @VisibleForTesting
    fun buildStreamHandler(streamList: ListBox, streamId: Int): StreamHandler? {
        val streamHeader = streamList.getChild(StreamHeaderBox::class.java)
        val streamFormat = streamList.getChild(StreamFormatBox::class.java)
        if (streamHeader == null) {
            w("Missing Stream Header")
            return null
        }
        //i(streamHeader.toString());
        if (streamFormat == null) {
            w("Missing Stream Format")
            return null
        }
        val durationUs = streamHeader.durationUs
        val builder = Format.Builder()
        builder.setId(streamId)
        val suggestedBufferSize = streamHeader.suggestedBufferSize
        if (suggestedBufferSize != 0) {
            builder.setMaxInputSize(suggestedBufferSize)
        }
        val streamName = streamList.getChild(StreamNameBox::class.java)
        if (streamName != null) {
            builder.setLabel(streamName.name)
        }
        val streamHandler: StreamHandler?
        if (streamHeader.isVideo) {
            val videoFormat = streamFormat.videoFormat
            val mimeType = videoFormat.mimeType
            if (mimeType == null) {
                Log.w(TAG, "Unknown FourCC: " + toString(videoFormat.compression))
                return null
            }
            val trackOutput = output!!.track(streamId, C.TRACK_TYPE_VIDEO)
            builder.setWidth(videoFormat.width)
            builder.setHeight(videoFormat.height)
            builder.setFrameRate(streamHeader.frameRate)
            builder.setSampleMimeType(mimeType)
            streamHandler = if (MimeTypes.VIDEO_H264 == mimeType) {
                AvcStreamHandler(streamId, durationUs, trackOutput, builder)
            } else if (MimeTypes.VIDEO_MP4V == mimeType) {
                Mp4VStreamHandler(streamId, durationUs, trackOutput, builder)
            } else {
                VideoStreamHandler(streamId, durationUs, trackOutput)
            }
            trackOutput.format(builder.build())
        } else if (streamHeader.isAudio) {
            val audioFormat = streamFormat.audioFormat
            val trackOutput = output!!.track(streamId, C.TRACK_TYPE_AUDIO)
            val mimeType = audioFormat.mimeType
            builder.setSampleMimeType(mimeType)
            builder.setChannelCount(audioFormat.channels.toInt())
            builder.setSampleRate(audioFormat.samplesPerSecond)
            val bytesPerSecond = audioFormat.avgBytesPerSec
            if (bytesPerSecond != 0) {
                builder.setAverageBitrate(bytesPerSecond * 8)
            }
            if (MimeTypes.AUDIO_RAW == mimeType) {
                val bps = audioFormat.bitsPerSample
                if (bps.toInt() == 8) {
                    builder.setPcmEncoding(C.ENCODING_PCM_8BIT)
                } else if (bps.toInt() == 16) {
                    builder.setPcmEncoding(C.ENCODING_PCM_16BIT)
                }
            }
            if (MimeTypes.AUDIO_AAC == mimeType && audioFormat.cbSize > 0) {
                builder.setInitializationData(listOf(audioFormat.codecData))
            }
            trackOutput.format(builder.build())
            streamHandler = if (MimeTypes.AUDIO_MPEG == mimeType) {
                MpegAudioStreamHandler(
                    streamId, durationUs, trackOutput,
                    audioFormat.samplesPerSecond
                )
            } else {
                AudioStreamHandler(
                    streamId, durationUs,
                    trackOutput
                )
            }
        } else {
            streamHandler = null
        }
        if (streamHandler != null) {
            val indexBox = streamList.getChild(IndexBox::class.java)
            if (indexBox != null && indexBox.indexType == IndexBox.AVI_INDEX_OF_INDEXES) {
                streamHandler.indexBox = indexBox
            }
        }
        return streamHandler
    }

    //Can't find video? just default to first stream
    @get:VisibleForTesting
    val seekStreamHandler: StreamHandler?
        get() {
            if (streamHandlers.isEmpty()) {
                return null
            }
            for (streamHandler in streamHandlers) {
                if (streamHandler is VideoStreamHandler) {
                    return streamHandler
                }
            }
            //Can't find video? just default to first stream
            return streamHandlers[0]
        }
    val indexBoxList: List<IndexBox>
        get() {
            val list = ArrayList<IndexBox>()
            for (streamHandler in streamHandlers) {
                val indexBox = streamHandler!!.indexBox
                if (indexBox != null) {
                    list.add(indexBox)
                }
            }
            if (list.size > 0 && list.size != streamHandlers.size) {
                w("StreamHandlers.length != IndexBoxes.length")
                list.clear()
            }
            return list
        }

    /**
     * Reads the index and sets the keyFrames and creates the SeekMap
     */
    fun parseIdx1(indexByteBuffer: ByteBuffer) {
        if (indexByteBuffer.capacity() < 16) {
            setSeekMap(SeekMap.Unseekable(duration))
            w("Index too short")
            return
        }
        val firstChunkPos: Long = getFirstChunkPosition()
        // Specifies the location of the data chunk in the file.
        // The value should be specified as an offset, in bytes, from the start of the 'movi' list;
        // however, in some AVI files it is given as an offset from the start of the file.
        val baseOffset: Long
        baseOffset = if (indexByteBuffer.getInt(8) < firstChunkPos) {
            // This is offset from the box start not the first chunk, so subtract 'movi'
            firstChunkPos - 4
        } else {
            //Bug: Some muxers use absolute position
            0L
        }
        while (indexByteBuffer.remaining() >= 16) {
            val chunkId = indexByteBuffer.int //0
            val flags = indexByteBuffer.int //4
            val offset = indexByteBuffer.int //8
            val size = indexByteBuffer.int // 12 Size
            val streamHandler = getStreamHandler(chunkId)
            streamHandler?.chunkIndex?.add(
                baseOffset + (offset and UINT_MASK.toInt()),
                size,
                flags and AVIIF_KEYFRAME == AVIIF_KEYFRAME
            )
        }
        buildSeekMap()
    }

    @VisibleForTesting
    fun getStreamHandler(chunkId: Int): StreamHandler? {
        for (streamHandler in streamHandlers) {
            if (streamHandler!!.handlesChunkId(chunkId)) {
                return streamHandler
            }
        }
        return null
    }

    fun createStreamHandlers(headerListBox: ListBox) {
        val aviHeader = headerListBox.getChild(AviHeaderBox::class.java)
            ?: throw IllegalArgumentException("Expected AviHeader in header ListBox")
        var totalFrames = aviHeader.totalFrames.toLong()
        for (box in headerListBox.children) {
            if (box is ListBox) {
                if (box.type == ListBox.TYPE_STRL) {
                    val streamId = streamHandlers.size
                    val streamHandler = buildStreamHandler(box, streamId)
                    if (streamHandler != null) {
                        streamHandlers = streamHandlers.copyOf(streamId + 1)
                        streamHandlers[streamId] = streamHandler
                    }
                } else if (box.type == ListBox.TYPE_ODML) {
                    val extendedAviHeader = box.getChild(
                        ExtendedAviHeader::class.java
                    )
                    if (extendedAviHeader != null) {
                        totalFrames = extendedAviHeader.totalFrames
                    }
                }
            }
        }
        duration = totalFrames * aviHeader.microSecPerFrame
        output!!.endTracks()
    }

    @Throws(IOException::class)
    private fun maybeSetPosition(
        input: ExtractorInput,
        positionHolder: PositionHolder,
        position: Long
    ): Int {
        val skip = position - input.position
        return if (skip == 0L) {
            Extractor.RESULT_CONTINUE
        } else if (skip < 0 || skip > RELOAD_MINIMUM_SEEK_DISTANCE) {
            positionHolder.position = position
            Extractor.RESULT_SEEK
        } else {
            input.skipFully(skip.toInt())
            Extractor.RESULT_CONTINUE
        }
    }

    @Throws(IOException::class)
    override fun read(input: ExtractorInput, positionHolder: PositionHolder): Int {
        val reader = readerStack.peek() ?: return Extractor.RESULT_END_OF_INPUT
        if (reader.position != input.position) {
            //      if (op == RESULT_SEEK) {
//        i("Seek from: " + input.getPosition() + " for " + reader);
//      }
            return maybeSetPosition(input, positionHolder, reader.position)
        }
        if (reader.read(input)) {
            readerStack.remove(reader)
            if (reader is Runnable) {
                (reader as Runnable).run()
            }
        }
        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        //i("Seek pos=" + position +", us="+timeUs);
        if (seekMap == null) {
            //Until we have the seekMap assume we are still parsing
            return
        }
        readerStack.clear()
        for (moviBox in moviList) {
            if (moviBox.setPosition(position)) {
                readerStack.add(moviBox)
            }
        }
        for (streamHandler in streamHandlers) {
            streamHandler!!.seekPosition(position)
        }
    }

    override fun release() {
        readerStack.clear()
        moviList.clear()
        streamHandlers = arrayOfNulls(0)
    }

    @VisibleForTesting
    fun setChunkHandlers(streamHandlers: Array<StreamHandler?>) {
        this.streamHandlers = streamHandlers
    }

    /**
     * Queue the IReader to run next
     * @param reader If the reader is Runnable, it will be run on completion
     */
    fun push(reader: IReader) {
        readerStack.push(reader)
    }

    /**
     * Add a MoviBox to the list
     */
    fun addMovi(moviBox: MoviBox) {
        moviList.add(moviBox)
    }

    fun getFirstChunkPosition():Long{
        return moviList[0].getStart();
    }

    internal inner class RootReader : BoxReader(0L, -1), Runnable {
        override var end = Long.MIN_VALUE
            private set

        override fun getSize(): Long {
            return end
        }

        private var riffReader: RiffReader? = null
        override fun isComplete(): Boolean {
            return super.isComplete() && (riffReader == null || riffReader!!.isComplete())
        }

        @Throws(IOException::class)
        override fun read(input: ExtractorInput): Boolean {
            if (end == Long.MIN_VALUE) {
                end = input.length
            }
            if (isComplete()) {
                return true
            }
            val chunkId: Int = if (headerPeeker.peakSafe(input)) {
                headerPeeker.chunkId
            } else {
                return true
            }
            if (chunkId != RIFF) {
                throw IOException("Expected RIFF")
            }
            val type = headerPeeker.type
            if (type and AVIX_MASK != AVIX) {
                throw IOException("Expected AVI?")
            }
            riffReader = RiffReader(position + PARENT_HEADER_SIZE, headerPeeker.getSize() - 4, type)
            push(riffReader!!)
            return advancePosition(CHUNK_HEADER_SIZE + headerPeeker.getSize())
        }

        override fun run() {
            //After the last RiffBox finishes process the OpenDML indexes
            val indexBoxList = indexBoxList
            if (!indexBoxList.isEmpty()) {
                val list: MutableList<Long> = ArrayList()
                for (indexBox in indexBoxList) {
                    list.addAll(indexBox.positions)
                }
                readerStack.push(IdxxBox(list))
            }
        }
    }

    internal inner class RiffReader(start: Long, size: Int, private val riffType: Int) :
        BoxReader(start, size) {
        @Throws(IOException::class)
        override fun read(input: ExtractorInput): Boolean {
            val chunkId = headerPeeker.peak(input, CHUNK_HEADER_SIZE)
            val size = headerPeeker.getSize()
            when (chunkId) {
                ListBox.LIST -> {
                    val type = headerPeeker.peakType(input)
                    if (type == MOVI) {
                        addMovi(MoviBox(position + PARENT_HEADER_SIZE, size - 4))
                        if (riffType == AVIX || indexBoxList.size > 0) {
                            //If we have OpenDML Indexes exit early and skip the IDX1 Index
                            position = end
                            return true
                        }
                    } else if (type == ListBox.TYPE_HDRL) {
                        readerStack.push(
                            HeaderListBox(
                                position + PARENT_HEADER_SIZE,
                                size - 4,
                                readerStack
                            )
                        )
                    }
                }
                IDX1 -> {
                    val byteBuffer = getByteBuffer(input, size)
                    parseIdx1(byteBuffer)
                }
            }
            return advancePosition()
        }
    }

    /**
     * Box of stream chunks
     */
    inner class MoviBox(start: Long, size: Int) : BoxReader(start, size) {
        /**
         * Prepares the MoviBox to be added to the readerQueue
         * @param position will be set to [.getStart]
         * @return false if position after end (don't  use)
         */
        fun setPosition(position: Long): Boolean {
            if (position > end) {
                return false
            }
            super.position = getStart().coerceAtLeast(position)
            return true
        }

        @Throws(IOException::class)
        override fun read(input: ExtractorInput): Boolean {
            val chunkId = headerPeeker.peak(input, CHUNK_HEADER_SIZE)
            val streamHandler = getStreamHandler(chunkId)
            if (streamHandler != null) {
                streamHandler.setRead(position + CHUNK_HEADER_SIZE, headerPeeker.getSize())
                push(streamHandler)
            } else if (chunkId == ListBox.LIST) {
                val type = headerPeeker.peakType(input)
                if (type == REC_) {
                    return advancePosition(PARENT_HEADER_SIZE)
                }
            }
            return advancePosition()
        }
    }

    internal inner class IdxxBox(positionList: List<Long>?) : IReader {
        private val deque: ArrayDeque<Long>
        override val position: Long
            get() = deque.peekFirst()

        @Throws(IOException::class)
        override fun read(input: ExtractorInput): Boolean {
            val headerPeeker = HeaderPeeker()
            headerPeeker.peak(input, BoxReader.CHUNK_HEADER_SIZE)
            val byteBuffer = getByteBuffer(input, headerPeeker.getSize())
            deque.pop()
            byteBuffer.position(byteBuffer.position() + 2) //Skip longs per entry
            val indexSubType = byteBuffer.get()
            require(indexSubType.toInt() == 0) { "Expected IndexSubType 0 got $indexSubType" }
            val indexType = byteBuffer.get()
            require(indexType == IndexBox.AVI_INDEX_OF_CHUNKS) { "Expected IndexType 1 got $indexType" }
            val entriesInUse = byteBuffer.int
            val chunkId = byteBuffer.int
            val streamHandler = getStreamHandler(chunkId)
            if (streamHandler == null) {
                w("No StreamHandler for " + toString(chunkId))
            } else {
                val chunkIndex = streamHandler.chunkIndex
                //baseOffset does not include the chunk header, so -8 to be compatible with IDX1
                val baseOffset = byteBuffer.long - 8
                byteBuffer.position(byteBuffer.position() + 4) // Skip reserved
                for (i in 0 until entriesInUse) {
                    val offset = byteBuffer.int
                    val size = byteBuffer.int
                    val size31 = size and 0x7fffffff
                    chunkIndex.add(
                        baseOffset + (offset and UINT_MASK.toInt()), size31,
                        size == size31
                    )
                }
            }
            if (!deque.isEmpty()) {
                return false
            }
            buildSeekMap()
            return true
        }

        override fun toString(): String {
            return "IdxxBox{positions=" + deque +
                    "}"
        }

        init {
            Collections.sort(positionList)
            deque = ArrayDeque(positionList)
        }
    }

    internal inner class HeaderListBox(position: Long, size: Int, readerStack: Deque<IReader>) :
        ListBox(position, size, TYPE_HDRL, readerStack), Runnable {
        override fun run() {
            createStreamHandlers(this)
        }

    }

    companion object {
        //Minimum time between keyframes in the AviSeekMap
        const val MIN_KEY_FRAME_RATE_US = 2000000L
        const val UINT_MASK = 0xffffffffL
        const val USHORT_MASK = 0xffff
        private const val RELOAD_MINIMUM_SEEK_DISTANCE = 256 * 1024
        fun getUInt(byteBuffer: ByteBuffer): Long {
            return (byteBuffer.int and UINT_MASK.toInt()).toLong()
        }

        fun toString(tag: Int): String {
            var tag = tag
            val sb = StringBuilder(4)
            for (i in 0..3) {
                sb.append((tag and 0xff).toChar())
                tag = tag shr 8
            }
            return sb.toString()
        }

        fun allocate(bytes: Int): ByteBuffer {
            val buffer = ByteArray(bytes)
            val byteBuffer = ByteBuffer.wrap(buffer)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            return byteBuffer
        }

        @VisibleForTesting
        fun getStreamId(chunkId: Int): Int {
            val upperChar = chunkId and 0xff
            if (Character.isDigit(upperChar)) {
                val lowerChar = chunkId shr 8 and 0xff
                if (Character.isDigit(upperChar)) {
                    return (lowerChar and 0xf) + (upperChar and 0xf) * 10
                }
            }
            return -1
        }

        const val TAG = "AviExtractor"

        @VisibleForTesting
        val PEEK_BYTES = 28
        const val AVIIF_KEYFRAME = 16
        const val RIFF = 0x46464952 // RIFF
        const val AVIX_MASK = 0x00ffffff
        const val AVIX = 0x00495641
        const val AVI_ = 0x20495641 // AVI<space>

        //movie data box
        const val MOVI = 0x69766f6d // movi

        //Index
        const val IDX1 = 0x31786469 // idx1
        const val JUNK = 0x4b4e554a // JUNK
        const val REC_ = 0x20636572 // rec<space>
        private fun w(message: String) {
            Log.w(TAG, message)
        }

        private fun i(message: String) {
            Log.i(TAG, message)
        }
    }
}