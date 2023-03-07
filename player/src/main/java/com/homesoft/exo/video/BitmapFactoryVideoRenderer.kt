package com.homesoft.exo.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.view.Surface
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.video.VideoRendererEventListener
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@UnstableApi
class BitmapFactoryVideoRenderer(
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?
) : BaseRenderer(C.TRACK_TYPE_VIDEO) {
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val rect = Rect()
    private val eventDispatcher: VideoRendererEventListener.EventDispatcher =
        VideoRendererEventListener.EventDispatcher(eventHandler, eventListener)
    private val threadPoolExecutor = ThreadPoolExecutor(
        0, 1, 1,
        TimeUnit.SECONDS, ArrayBlockingQueue(2)
    ) { r -> Thread(r, TAG + "#" + threadId++) }
    val decoderInputBuffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)

    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Volatile
    var surface: Surface? = null
    private var lastVideoSize = VideoSize.UNKNOWN
    private var firstFrameRendered = false

    @get:VisibleForTesting
    var decoderCounters: DecoderCounters? = null
        private set
    private var decodeRunnable: DecodeRunnable? = null
    override fun getName(): String {
        return TAG
    }

    override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
        decoderCounters = DecoderCounters()
        eventDispatcher.enabled(decoderCounters!!)
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        // Prevent the current pending frame from being rendered
        decodeRunnable = null
    }

    override fun onDisabled() {
        super.onDisabled()
        decodeRunnable = null
        val decoderCounters = decoderCounters
        if (decoderCounters != null) {
            eventDispatcher.disabled(decoderCounters)
        }
    }

    override fun onReset() {
        super.onReset()
        threadPoolExecutor.shutdownNow()
    }

    /**
     * If the dropped frame was not caused by a positionReset(), report it
     */
    private fun maybeReportDroppedFrame(timeUs: Long, elapsedRealtimeUs: Long) {
        if (timeUs > readingPositionUs) {
            eventDispatcher.droppedFrames(1, elapsedRealtimeUs)
        }
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        val myDecodeRunnable = decodeRunnable
        if (myDecodeRunnable != null) {
            val earlyUs = myDecodeRunnable.timeUs - positionUs
            // If we are within 1 ms, display the frame
            if (earlyUs >= 1000L) {
                return
            }
            try {
                val bitmap = myDecodeRunnable.getBitmap()
                    ?: // Decoder is running late!
                    return
                if (isFrameLate(earlyUs)) {
                    maybeReportDroppedFrame(myDecodeRunnable.timeUs, elapsedRealtimeUs)
                } else {
                    renderBitmap(bitmap)
                }
            } catch (e: Exception) {
                eventDispatcher.videoCodecError(e)
            }
            decodeRunnable = null
        }
        while (true) {
            decoderInputBuffer.clear()
            val result = readSource(formatHolder, decoderInputBuffer, 0)
            when (result) {
                C.RESULT_BUFFER_READ -> {
                    if (decoderInputBuffer.isEndOfStream) {
                        return
                    }
                    if (decoderInputBuffer.timeUs < positionUs) {
                        // When seeking the player sends all the frames from the last I frame to the current frame.
                        // This is necessary for progressive video (P/B frames)
                        // These frames seems to always be late and since we are all I frames, we can ignore them.
                        maybeReportDroppedFrame(decoderInputBuffer.timeUs, elapsedRealtimeUs)
                        continue
                    }
                    val byteBuffer = decoderInputBuffer.data
                    if (byteBuffer != null) {
                        byteBuffer.flip()
                        val buffer = ByteArray(byteBuffer.remaining())
                        byteBuffer[buffer]
                        //Log.d("Test", "Queued " + decoderInputBuffer.timeUs + " leadUs: " + (decoderInputBuffer.timeUs - positionUs));
                        decodeRunnable = DecodeRunnable(decoderInputBuffer.timeUs, buffer)
                        threadPoolExecutor.execute(decodeRunnable)
                    }
                    return
                }
                C.RESULT_NOTHING_READ -> return
                C.RESULT_FORMAT_READ -> {
                }
            }
        }
    }

    @Throws(ExoPlaybackException::class)
    override fun handleMessage(messageType: Int, message: Any?) {
        if (messageType == MSG_SET_VIDEO_OUTPUT) {
            surface = if (message is Surface) {
                message
            } else {
                null
            }
        }
        super.handleMessage(messageType, message)
    }

    override fun isReady(): Boolean {
        return surface != null
    }

    override fun isEnded(): Boolean {
        return decoderInputBuffer.isEndOfStream
    }

    @Throws(ExoPlaybackException::class)
    override fun supportsFormat(format: Format): Int {
        //Technically could support any format BitmapFactory supports
        return if (MimeTypes.VIDEO_MJPEG == format.sampleMimeType) {
            RendererCapabilities.create(C.FORMAT_HANDLED)
        } else RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
    }

    @WorkerThread
    fun renderBitmap(bitmap: Bitmap) {
        val surface = surface ?: return
        //Log.d(TAG, "Drawing: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        try {
            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE
            ) surface.lockHardwareCanvas() else surface.lockCanvas(null)
            renderBitmap(bitmap, canvas)
            surface.unlockCanvasAndPost(canvas)
        } catch (e: IllegalStateException) {
            // For some reason Samsung devices running 12 crash sometimes.
            eventDispatcher.videoCodecError(e)
            return
        }
        val decoderCounters = decoderCounters
        if (decoderCounters != null) {
            decoderCounters.renderedOutputBufferCount++
        }
        if (!firstFrameRendered) {
            firstFrameRendered = true
            eventDispatcher.renderedFirstFrame(surface)
        }
    }

    @WorkerThread
    @VisibleForTesting
    fun renderBitmap(bitmap: Bitmap, canvas: Canvas) {
        val videoSize = VideoSize(bitmap.width, bitmap.height)
        if (videoSize != lastVideoSize) {
            lastVideoSize = videoSize
            eventDispatcher.videoSizeChanged(videoSize)
        }
        rect[0, 0, canvas.width] = canvas.height
        canvas.drawBitmap(bitmap, null, rect, null)
    }

    internal class DecodeRunnable(val timeUs: Long, private val buffer: ByteArray) : Runnable {
        companion object {
            val options = BitmapFactory.Options()

            init {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    options.inPreferredConfig = Bitmap.Config.HARDWARE
                }
            }
        }

        @Volatile
        private var bitmap: Bitmap? = null

        @Volatile
        private var exception: Exception? = null
        override fun run() {
            try {
                bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size, options)
                if (bitmap == null) {
                    exception = NullPointerException("Decode bytes failed")
                }
            } catch (e: Exception) {
                exception = e
            }
        }

        /**
         *
         * @return the Bitmap if processing complete or null if still running
         * @throws Exception if processing failed with an exception or produced a null Bitmap
         */
        @Throws(Exception::class)
        fun getBitmap(): Bitmap? {
            return when {
                bitmap != null -> {
                    bitmap
                }
                exception != null -> {
                    throw exception as Exception
                }
                else -> {
                    null
                }
            }
        }

        override fun toString(): String {
            return "DecodeRunnable{" +
                    "timeUs=" + timeUs +
                    ", bitmap=" + bitmap +
                    ", exception=" + exception +
                    '}'
        }
    }

    companion object {
        const val TAG = "BitmapFactoryRenderer"
        private var threadId = 0
        private fun isFrameLate(earlyUs: Long): Boolean {
            // Class a buffer as late if it should have been presented more than 30 ms ago.
            return earlyUs < -30000
        }
    }

}