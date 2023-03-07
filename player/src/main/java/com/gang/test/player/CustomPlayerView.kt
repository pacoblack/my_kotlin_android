package com.gang.test.player

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.util.AttributeSet
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.GestureDetectorCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gang.test.player.Utils.adjustVolume
import com.gang.test.player.Utils.dpToPx
import com.gang.test.player.Utils.formatMilis
import com.gang.test.player.Utils.formatMilisSign
import com.gang.test.player.Utils.isTvBox
import com.gang.test.player.Utils.isVolumeMax
import com.gang.test.player.Utils.normalizeScaleFactor
import com.gang.test.player.Utils.pxToDp
import com.gang.test.player.Utils.showText
import kotlin.math.abs

@UnstableApi
open class CustomPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr), GestureDetector.OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {
    private val mDetector: GestureDetectorCompat
    private var gestureOrientation = Orientation.UNKNOWN
    private var gestureScrollY = 0f
    private var gestureScrollX = 0f
    private var handleTouch = false
    private var seekStart: Long = 0
    private var seekChange: Long = 0
    private var seekMax: Long = 0
    private var seekLastPosition: Long = 0
    var seekProgress = false
    private var canBoostVolume = false
    private var canSetAutoBrightness = false
    private val IGNORE_BORDER = dpToPx(24).toFloat()
    private val SCROLL_STEP = dpToPx(16).toFloat()
    private val SCROLL_STEP_SEEK = dpToPx(8).toFloat()
    private val SEEK_STEP: Long = 1000
    private var restorePlayState = false
    private var canScale = true
    private var isHandledLongPress = false
    var keySeekStart: Long = -1
    var volumeUpsInRow = 0
    private val mScaleDetector: ScaleGestureDetector
    private var mScaleFactor = 1f
    private var mScaleFactorFit = 0f
    var systemGestureExclusionRect = Rect()
    val textClearRunnable = Runnable {
        setCustomErrorMessage(null)
        clearIcon()
        keySeekStart = -1
    }
    private val mAudioManager: AudioManager
    private var brightnessControl: BrightnessControl? = null
    private val exoErrorMessage: TextView
    private val exoProgress: View
    fun clearIcon() {
        exoErrorMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        setHighlight(false)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (PlayerActivity.restoreControllerTimeout) {
            controllerShowTimeoutMs = PlayerActivity.CONTROLLER_TIMEOUT
            PlayerActivity.restoreControllerTimeout = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gestureOrientation == Orientation.UNKNOWN) mScaleDetector.onTouchEvent(
            ev
        )
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleTouch =
                if (PlayerActivity.snackbar != null && PlayerActivity.snackbar!!.isShown) {
                    PlayerActivity.snackbar!!.dismiss()
                    false
                } else {
                    removeCallbacks(textClearRunnable)
                    true
                }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (handleTouch) {
                if (gestureOrientation == Orientation.HORIZONTAL) {
                    setCustomErrorMessage(null)
                } else {
                    postDelayed(
                        textClearRunnable,
                        (if (isHandledLongPress) MESSAGE_TIMEOUT_LONG else MESSAGE_TIMEOUT_TOUCH.toLong()) as Long
                    )
                }
                if (restorePlayState) {
                    restorePlayState = false
                    if (PlayerActivity.player != null) {
                        PlayerActivity.player!!.play()
                    }
                }
                controllerAutoShow = true
                if (seekProgress) {
                    seekProgress = false
                    hideControllerImmediately()
                }
            }
        }
        if (handleTouch) mDetector.onTouchEvent(ev)

        // Handle all events to avoid conflict with internal handlers
        return true
    }

    override fun onDown(motionEvent: MotionEvent): Boolean {
        gestureScrollY = 0f
        gestureScrollX = 0f
        gestureOrientation = Orientation.UNKNOWN
        isHandledLongPress = false
        return false
    }

    override fun onShowPress(motionEvent: MotionEvent) {}
    override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
        return false
    }

    fun tap(): Boolean {
        if (PlayerActivity.locked) {
            showText(this, "", MESSAGE_TIMEOUT_LONG.toLong())
            setIconLock(true)
            return true
        }
        if (!PlayerActivity.controllerVisibleFully) {
            showController()
            return true
        } else if (PlayerActivity.haveMedia && PlayerActivity.player != null && PlayerActivity.player!!.isPlaying) {
            hideController()
            return true
        }
        return false
    }

    override fun onScroll(
        motionEvent: MotionEvent,
        motionEvent1: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (mScaleDetector.isInProgress || PlayerActivity.player == null || PlayerActivity.locked) return false

        // Exclude edge areas
        if (motionEvent.y < IGNORE_BORDER || motionEvent.x < IGNORE_BORDER || motionEvent.y > height - IGNORE_BORDER || motionEvent.x > width - IGNORE_BORDER) return false
        if (gestureScrollY == 0f || gestureScrollX == 0f) {
            gestureScrollY = 0.0001f
            gestureScrollX = 0.0001f
            return false
        }
        if (gestureOrientation == Orientation.HORIZONTAL || gestureOrientation == Orientation.UNKNOWN) {
            gestureScrollX += distanceX
            if (Math.abs(gestureScrollX) > SCROLL_STEP || gestureOrientation == Orientation.HORIZONTAL && Math.abs(
                    gestureScrollX
                ) > SCROLL_STEP_SEEK
            ) {
                // Do not show controller if not already visible
                controllerAutoShow = false
                if (gestureOrientation == Orientation.UNKNOWN) {
                    if (PlayerActivity.player!!.isPlaying) {
                        restorePlayState = true
                        PlayerActivity.player!!.pause()
                    }
                    clearIcon()
                    seekStart = PlayerActivity.player!!.currentPosition
                    seekLastPosition = seekStart
                    seekChange = 0L
                    seekMax = PlayerActivity.player!!.duration
                    if (!isControllerFullyVisible) {
                        seekProgress = true
                        showProgress()
                    }
                }
                gestureOrientation = Orientation.HORIZONTAL
                var position: Long = 0
                val distanceDiff =
                    0.5f.coerceAtLeast(abs(pxToDp(distanceX) / 4).coerceAtMost(10f))
                if (PlayerActivity.haveMedia) {
                    if (gestureScrollX > 0) {
                        if (seekStart + seekChange - SEEK_STEP * distanceDiff >= 0) {
                            PlayerActivity.player!!.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                            seekChange -= (SEEK_STEP * distanceDiff).toLong()
                            position = seekStart + seekChange
                            PlayerActivity.player!!.seekTo(position)
                        }
                    } else {
                        PlayerActivity.player!!.setSeekParameters(SeekParameters.NEXT_SYNC)
                        if (seekMax == C.TIME_UNSET) {
                            seekChange += (SEEK_STEP * distanceDiff).toLong()
                            position = seekStart + seekChange
                            PlayerActivity.player!!.seekTo(position)
                        } else if (seekStart + seekChange + SEEK_STEP < seekMax) {
                            seekChange += (SEEK_STEP * distanceDiff).toLong()
                            position = seekStart + seekChange
                            PlayerActivity.player!!.seekTo(position)
                        }
                    }
                    for (start in PlayerActivity.chapterStarts) {
                        if (start in (seekLastPosition + 1)..position || start in position until seekLastPosition) {
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                    seekLastPosition = position
                    var message = formatMilisSign(seekChange)
                    if (!isControllerFullyVisible) {
                        message += """
                            
                            ${formatMilis(position)}
                            """.trimIndent()
                    }
                    setCustomErrorMessage(message)
                    gestureScrollX = 0.0001f
                }
            }
        }

        // LEFT = Brightness  |  RIGHT = Volume
        if (gestureOrientation == Orientation.VERTICAL || gestureOrientation == Orientation.UNKNOWN) {
            gestureScrollY += distanceY
            if (Math.abs(gestureScrollY) > SCROLL_STEP) {
                if (gestureOrientation == Orientation.UNKNOWN) {
                    canBoostVolume = isVolumeMax(mAudioManager)
                    canSetAutoBrightness = brightnessControl!!.currentBrightnessLevel <= 0
                }
                gestureOrientation = Orientation.VERTICAL
                if (motionEvent.x < (width / 2).toFloat()) {
                    brightnessControl!!.changeBrightness(
                        this,
                        gestureScrollY > 0,
                        canSetAutoBrightness
                    )
                } else {
                    adjustVolume(
                        context,
                        mAudioManager,
                        this,
                        gestureScrollY > 0,
                        canBoostVolume,
                        false
                    )
                }
                gestureScrollY = 0.0001f
            }
        }
        return true
    }

    override fun onLongPress(motionEvent: MotionEvent) {
        if (PlayerActivity.locked || player != null && player!!.isPlaying) {
            PlayerActivity.locked = !PlayerActivity.locked
            isHandledLongPress = true
            showText(this, "", MESSAGE_TIMEOUT_LONG.toLong())
            setIconLock(PlayerActivity.locked)
            if (PlayerActivity.locked && PlayerActivity.controllerVisible) {
                hideController()
            }
        }
    }

    override fun onFling(
        motionEvent: MotionEvent,
        motionEvent1: MotionEvent,
        v: Float,
        v1: Float
    ): Boolean {
        return false
    }

    override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
        if (PlayerActivity.locked) return false
        if (canScale) {
            val factor = scaleGestureDetector.scaleFactor
            mScaleFactor *= factor + (1 - factor) / 3 * 2
            mScaleFactor = normalizeScaleFactor(mScaleFactor, mScaleFactorFit)
            setScale(mScaleFactor)
            restoreSurfaceView()
            clearIcon()
            setCustomErrorMessage("%" + ((mScaleFactor * 100).toInt()))
            return true
        }
        return false
    }

    override fun onScaleBegin(scaleGestureDetector: ScaleGestureDetector): Boolean {
        if (PlayerActivity.locked) return false
        mScaleFactor = videoSurfaceView!!.scaleX
        if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            canScale = false
            setAspectRatioListener { _: Float, _: Float, _: Boolean ->
                setAspectRatioListener(null)
                mScaleFactorFit = scaleFit
                mScaleFactor = mScaleFactorFit
                canScale = true
            }
            videoSurfaceView!!.alpha = 0f
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            mScaleFactorFit = scaleFit
            canScale = true
        }
        val buttonAspectRatio = findViewById<ImageButton>(Int.MAX_VALUE - 100)
        buttonAspectRatio.setImageResource(R.drawable.ic_fit_screen_24dp)
        hideController()
        return true
    }

    override fun onScaleEnd(scaleGestureDetector: ScaleGestureDetector) {
        if (PlayerActivity.locked) return
        if (mScaleFactor - mScaleFactorFit < 0.001) {
            setScale(1f)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            val buttonAspectRatio = findViewById<ImageButton>(Int.MAX_VALUE - 100)
            buttonAspectRatio.setImageResource(R.drawable.ic_aspect_ratio_24dp)
        }
        if (PlayerActivity.player != null && !PlayerActivity.player!!.isPlaying) {
            showController()
        }
        restoreSurfaceView()
    }

    private fun restoreSurfaceView() {
        if (videoSurfaceView!!.alpha != 1f) {
            videoSurfaceView!!.alpha = 1f
        }
    }

    val scaleFit: Float
        get() = (height.toFloat() / videoSurfaceView!!.height.toFloat()).coerceAtMost(width.toFloat() / videoSurfaceView!!.width.toFloat())

    private enum class Orientation {
        HORIZONTAL, VERTICAL, UNKNOWN
    }

    fun setIconVolume(volumeActive: Boolean) {
        exoErrorMessage.setCompoundDrawablesWithIntrinsicBounds(
            if (volumeActive) R.drawable.ic_volume_up_24dp else R.drawable.ic_volume_off_24dp,
            0,
            0,
            0
        )
    }

    fun setHighlight(active: Boolean) {
        if (active) exoErrorMessage.background.setTint(Color.RED) else exoErrorMessage.background.setTintList(
            null
        )
    }

    fun setIconBrightness() {
        exoErrorMessage.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_brightness_medium_24,
            0,
            0,
            0
        )
    }

    fun setIconBrightnessAuto() {
        exoErrorMessage.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_brightness_auto_24dp,
            0,
            0,
            0
        )
    }

    fun setIconLock(locked: Boolean) {
        exoErrorMessage.setCompoundDrawablesWithIntrinsicBounds(
            if (locked) R.drawable.ic_lock_24dp else R.drawable.ic_lock_open_24dp,
            0,
            0,
            0
        )
    }

    fun setScale(scale: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val videoSurfaceView = videoSurfaceView
            try {
                videoSurfaceView!!.scaleX = scale
                videoSurfaceView.scaleY = scale
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
            //videoSurfaceView.animate().setStartDelay(0).setDuration(0).scaleX(scale).scaleY(scale).start();
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= 29) {
            exoProgress.getGlobalVisibleRect(systemGestureExclusionRect)
            systemGestureExclusionRect.left = left
            systemGestureExclusionRect.right = right
            systemGestureExclusionRects = listOf(systemGestureExclusionRect)
        }
    }

    open fun setBrightnessControl(brightnessControl: BrightnessControl) {
        this.brightnessControl = brightnessControl
    }

    companion object {
        const val MESSAGE_TIMEOUT_TOUCH = 400
        const val MESSAGE_TIMEOUT_KEY = 800
        const val MESSAGE_TIMEOUT_LONG = 1400
    }

    init {
        mDetector = GestureDetectorCompat(context, this)
        mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        exoErrorMessage = findViewById(R.id.exo_error_message)
        exoProgress = findViewById(R.id.exo_progress)
        mScaleDetector = ScaleGestureDetector(context, this)
        if (!isTvBox(getContext())) {
            exoErrorMessage.setOnClickListener { v: View? ->
                if (PlayerActivity.locked) {
                    PlayerActivity.locked = false
                    showText(this@CustomPlayerView, "", MESSAGE_TIMEOUT_LONG.toLong())
                    setIconLock(false)
                }
            }
        }
    }
}