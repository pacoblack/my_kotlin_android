package com.gang.test.player.dtpv.youtube

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.gang.test.player.PlayerActivity
import com.gang.test.player.R
import com.gang.test.player.dtpv.DoubleTapPlayerView
import com.gang.test.player.dtpv.PlayerDoubleTapListener
import com.gang.test.player.dtpv.SeekListener
import com.gang.test.player.dtpv.youtube.views.CircleClipTapView
import com.gang.test.player.dtpv.youtube.views.SecondsView

@UnstableApi
/**
 * Overlay for [DoubleTapPlayerView] to create a similar UI/UX experience like the official
 * YouTube Android app.
 *
 * The overlay has the typical YouTube scaling circle animation and provides some configurations
 * which can't be accomplished with the regular Android Ripple (I didn't find any options in the
 * documentation ...).
 */
class YouTubeOverlay(context: Context, private val attrs: AttributeSet?) :
    ConstraintLayout(context, attrs), PlayerDoubleTapListener {
    constructor(context: Context) : this(context, null) {
        // Hide overlay initially when added programmatically
        visibility = View.INVISIBLE
    }

    private var playerViewRef: Int

    // Player behaviors
    private var playerView: DoubleTapPlayerView? = null
    private var player: ExoPlayer? = null

    /**
     * Sets all optional XML attributes and defaults
     */
    private fun initializeAttributes() {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs,
                R.styleable.YouTubeOverlay, 0, 0
            )

            // PlayerView => see onAttachToWindow
            playerViewRef = a.getResourceId(R.styleable.YouTubeOverlay_yt_playerView, -1)

            // Durations
            animationDuration = a.getInt(
                R.styleable.YouTubeOverlay_yt_animationDuration, 650
            ).toLong()
            seekSeconds = a.getInt(
                R.styleable.YouTubeOverlay_yt_seekSeconds, 10
            )
            iconAnimationDuration = a.getInt(
                R.styleable.YouTubeOverlay_yt_iconAnimationDuration, 750
            ).toLong()

            // Arc size
            arcSize = a.getDimensionPixelSize(
                R.styleable.YouTubeOverlay_yt_arcSize,
                context.resources
                    .getDimensionPixelSize(R.dimen.dtpv_yt_arc_size)
            ).toFloat()

            // Colors
            tapCircleColor = a.getColor(
                R.styleable.YouTubeOverlay_yt_tapCircleColor,
                ContextCompat.getColor(context, R.color.dtpv_yt_tap_circle_color)
            )
            circleBackgroundColor = a.getColor(
                R.styleable.YouTubeOverlay_yt_backgroundCircleColor,
                ContextCompat.getColor(context, R.color.dtpv_yt_background_circle_color)
            )

            // Seconds TextAppearance
            textAppearance = a.getResourceId(
                R.styleable.YouTubeOverlay_yt_textAppearance,
                R.style.YTOSecondsTextAppearance
            )
            icon = a.getResourceId(
                R.styleable.YouTubeOverlay_yt_icon,
                R.drawable.ic_play_triangle
            )
            a.recycle()
        } else {
            // Set defaults
            arcSize = context.resources.getDimensionPixelSize(R.dimen.dtpv_yt_arc_size)
                .toFloat()
            tapCircleColor = ContextCompat.getColor(context, R.color.dtpv_yt_tap_circle_color)
            circleBackgroundColor =
                ContextCompat.getColor(context, R.color.dtpv_yt_background_circle_color)
            animationDuration = 650L
            iconAnimationDuration = 750L
            seekSeconds = 10
            textAppearance = R.style.YTOSecondsTextAppearance
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // If the PlayerView is set by XML then call the corresponding setter method
        if (playerViewRef != -1) playerView((parent as View).findViewById<View>(playerViewRef) as DoubleTapPlayerView)
    }

    /**
     * Obligatory call if playerView is not set via XML!
     *
     * Links the DoubleTapPlayerView to this view for recognizing the tapped position.
     *
     * @param playerView PlayerView which triggers the event
     */
    fun playerView(playerView: DoubleTapPlayerView?): YouTubeOverlay {
        this.playerView = playerView
        return this
    }

    /**
     * Obligatory call! Needs to be called whenever the Player changes.
     *
     * Performs seekTo-calls on the ExoPlayer's Player instance.
     *
     * @param player PlayerView which triggers the event
     */
    fun player(player: ExoPlayer?): YouTubeOverlay {
        this.player = player
        return this
    }

    /*
        Properties
     */
    private var seekListener: SeekListener? = null

    /**
     * Optional: Sets a listener to observe whether double tap reached the start / end of the video
     */
    fun seekListener(listener: SeekListener?): YouTubeOverlay {
        seekListener = listener
        return this
    }

    private var performListener: PerformListener? = null

    /**
     * Sets a listener to execute some code before and after the animation
     * (for example UI changes (hide and show views etc.))
     */
    fun performListener(listener: PerformListener?): YouTubeOverlay {
        performListener = listener
        return this
    }

    /**
     * Forward / rewind duration on a tap in seconds.
     */
    var seekSeconds = 0
        private set

    fun seekSeconds(seconds: Int): YouTubeOverlay {
        seekSeconds = seconds
        return this
    }

    /**
     * Color of the scaling circle on touch feedback.
     */
    var tapCircleColor: Int
        get() = (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).circleColor
        private set(value) {
            (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).circleColor =
                value
        }

    fun tapCircleColorRes(@ColorRes resId: Int): YouTubeOverlay {
        tapCircleColor = ContextCompat.getColor(context, resId)
        return this
    }

    fun tapCircleColorInt(@ColorInt color: Int): YouTubeOverlay {
        tapCircleColor = color
        return this
    }

    /**
     * Color of the clipped background circle
     */
    var circleBackgroundColor: Int
        get() = (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).circleBackgroundColor
        private set(value) {
            (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).circleBackgroundColor =
                value
        }

    fun circleBackgroundColorRes(@ColorRes resId: Int): YouTubeOverlay {
        circleBackgroundColor = ContextCompat.getColor(context, resId)
        return this
    }

    fun circleBackgroundColorInt(@ColorInt color: Int): YouTubeOverlay {
        circleBackgroundColor = color
        return this
    }

    /**
     * Duration of the circle scaling animation / speed in milliseconds.
     * The overlay keeps visible until the animation finishes.
     */
    var animationDuration: Long
        get() = (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).animationDuration
        private set(value) {
            (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).animationDuration =
                value
        }

    fun animationDuration(duration: Long): YouTubeOverlay {
        animationDuration = duration
        return this
    }

    /**
     * Size of the arc which will be clipped from the background circle.
     * The greater the value the more roundish the shape becomes
     */
    var arcSize: Float
        get() = (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).arcSize
        private set(value) {
            (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).arcSize =
                value
        }

    fun arcSize(@DimenRes resId: Int): YouTubeOverlay {
        arcSize = context.resources.getDimension(resId)
        return this
    }

    fun arcSize(px: Float): YouTubeOverlay {
        arcSize = px
        return this
    }

    /**
     * Duration the icon animation (fade in + fade out) for a full cycle in milliseconds.
     */
    var iconAnimationDuration: Long = 750
        get() = (findViewById<View>(R.id.seconds_view) as SecondsView).cycleDuration
        private set(value) {
            (findViewById<View>(R.id.seconds_view) as SecondsView).cycleDuration = value
            field = value
        }

    fun iconAnimationDuration(duration: Long): YouTubeOverlay {
        iconAnimationDuration = duration
        return this
    }

    /**
     * One of the three forward icons which will be animated above the seconds indicator.
     * The rewind icon will be the 180° mirrored version.
     *
     * Keep in mind that padding on the left and right of the drawable will be rendered which
     * could result in additional space between the three icons.
     */
    var icon = 0
        get() = (findViewById<View>(R.id.seconds_view) as SecondsView).icon
        private set(value) {
            (findViewById<View>(R.id.seconds_view) as SecondsView).icon = value
            field = value
        }

    fun icon(@DrawableRes resId: Int): YouTubeOverlay {
        icon = resId
        return this
    }

    /**
     * Text appearance of the *xx seconds* text.
     */
    var textAppearance = 0
        private set(value) {
            TextViewCompat.setTextAppearance(
                (findViewById<View>(R.id.seconds_view) as SecondsView).textView,
                value
            )
            field = value
        }

    fun textAppearance(@StyleRes resId: Int): YouTubeOverlay {
        textAppearance = resId
        return this
    }

    /**
     * TextView view for *xx seconds*.
     *
     * In case of you'd like to change some specific attributes of the TextView in runtime.
     */
    val secondsTextView: TextView
        get() = (findViewById<View>(R.id.seconds_view) as SecondsView).textView

    @UnstableApi override fun onDoubleTapStarted(posX: Float, posY: Float) {
        if (PlayerActivity.locked) return
        if ((player != null) && (player!!.currentPosition >= 0L) && (playerView != null) && (playerView!!.width > 0)) {
            if (posX >= playerView!!.width * 0.35 && posX <= playerView!!.width * 0.65) {
                if (player!!.isPlaying) {
                    player!!.pause()
                } else {
                    player!!.play()
                    if (playerView!!.isControllerFullyVisible) playerView!!.hideController()
                }
                return
            }
        }

        //super.onDoubleTapStarted(posX, posY);
    }

    override fun onDoubleTapProgressUp(posX: Float, posY: Float) {
        if (PlayerActivity.locked) return

        // Check first whether forwarding/rewinding is "valid"
        if ((player == null) || (player!!.mediaItemCount < 1) || (player!!.currentPosition < 0) || (playerView == null) || (playerView!!.width < 0)) return
        val current = player!!.currentPosition
        // Rewind and start of the video (+ 0.5 sec tolerance)
        if (posX < playerView!!.width * 0.35 && current <= 500) return

        // Forward and end of the video (- 0.5 sec tolerance)
        if (posX > playerView!!.width * 0.65 && current >= (player!!.duration - 500)) return

        // YouTube behavior: show overlay on MOTION_UP
        // But check whether the first double tap is in invalid area
        if (visibility != View.VISIBLE) {
            if (posX < playerView!!.width * 0.35 || posX > playerView!!.width * 0.65) {
                if (performListener != null) performListener!!.onAnimationStart()
                val secondsView: SecondsView = findViewById(R.id.seconds_view)
                secondsView.visibility = View.VISIBLE
                secondsView.start()
            } else return
        }
        if (posX < playerView!!.width * 0.35) {

            // First time tap or switched
            val secondsView: SecondsView = findViewById(R.id.seconds_view)
            if (secondsView.isForward) {
                changeConstraints(false)
                secondsView.isForward = false
                secondsView.seconds = 0
            }

            // Cancel ripple and start new without triggering overlay disappearance
            // (resetting instead of ending)
            (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).resetAnimation(
                Runnable {
                    (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).updatePosition(
                        posX,
                        posY
                    )
                })
            rewinding()
        } else if (posX > playerView!!.width * 0.65) {

            // First time tap or switched
            val secondsView: SecondsView = findViewById(R.id.seconds_view)
            if (!secondsView.isForward) {
                changeConstraints(true)
                secondsView.isForward = true
                secondsView.seconds = 0
            }

            // Cancel ripple and start new without triggering overlay disappearance
            // (resetting instead of ending)
            (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).resetAnimation {
                (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).updatePosition(
                    posX,
                    posY
                )
            }
            forwarding()
        } else {
            // Middle area tapped: do nothing
            //
            // playerView?.cancelInDoubleTapMode()
            // circle_clip_tap_view.endAnimation()
            // triangle_seconds_view.stop()
        }
    }

    @UnstableApi
    /**
     * Seeks the video to desired position.
     * Calls interface functions when start reached ([SeekListener.onVideoStartReached])
     * or when end reached ([SeekListener.onVideoEndReached])
     *
     * @param newPosition desired position
     */
    private fun seekToPosition(newPosition: Long) {
        if (player == null || playerView == null) return
        player!!.setSeekParameters(SeekParameters.EXACT)

        // Start of the video reached
        if (newPosition <= 0) {
            player!!.seekTo(0)
            if (seekListener != null) seekListener!!.onVideoStartReached()
            return
        }

        // End of the video reached
        val total = player!!.duration
        if (newPosition >= total) {
            player!!.seekTo(total)
            if (seekListener != null) seekListener!!.onVideoEndReached()
            return
        }

        // Otherwise
        playerView!!.keepInDoubleTapMode()
        player!!.seekTo(newPosition)
    }

    @UnstableApi
    private fun forwarding() {
        val secondsView: SecondsView = findViewById(R.id.seconds_view)
        secondsView.seconds = secondsView.seconds + seekSeconds
        seekToPosition((if (player != null) player!!.currentPosition + (seekSeconds * 1000).toLong() else null)!!)
    }

    @UnstableApi
    private fun rewinding() {
        val secondsView: SecondsView = findViewById(R.id.seconds_view)
        secondsView.seconds = secondsView.seconds + seekSeconds
        seekToPosition((if (player != null) player!!.currentPosition - (seekSeconds * 1000).toLong() else null)!!)
    }

    private fun changeConstraints(forward: Boolean) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(findViewById<View>(R.id.root_constraint_layout) as ConstraintLayout)
        val secondsView: SecondsView = findViewById(R.id.seconds_view)
        if (forward) {
            constraintSet.clear(secondsView.id, ConstraintSet.START)
            constraintSet.connect(
                secondsView.id, ConstraintSet.END,
                ConstraintSet.PARENT_ID, ConstraintSet.END
            )
        } else {
            constraintSet.clear(secondsView.id, ConstraintSet.END)
            constraintSet.connect(
                secondsView.id, ConstraintSet.START,
                ConstraintSet.PARENT_ID, ConstraintSet.START
            )
        }
        //secondsView.start();
        constraintSet.applyTo(findViewById<View>(R.id.root_constraint_layout) as ConstraintLayout)
    }

    interface PerformListener {
        fun onAnimationStart()
        fun onAnimationEnd()
    }

    init {
        playerViewRef = -1
        LayoutInflater.from(context).inflate(R.layout.yt_overlay, this, true)

        // Initialize UI components
        initializeAttributes()
        (findViewById<View>(R.id.seconds_view) as SecondsView).isForward = true
        changeConstraints(true)

        // This code snippet is executed when the circle scale animation is finished
        (findViewById<View>(R.id.circle_clip_tap_view) as CircleClipTapView).performAtEnd =
            Runnable {
                if (performListener != null) performListener!!.onAnimationEnd()
                val secondsView: SecondsView = findViewById(R.id.seconds_view)
                secondsView.visibility = View.INVISIBLE
                secondsView.seconds = 0
                secondsView.stop()
            }
    }
}