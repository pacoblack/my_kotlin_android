package com.gang.test.player.dtpv.youtube.views

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.Consumer
import com.gang.test.player.R

/**
 * Layout group which handles the icon animation while forwarding and rewinding.
 *
 * Since it's based on view's alpha the fading effect is more fluid (more YouTube-like) than
 * using static drawables, especially when [cycleDuration] is low.
 *
 * Used by [YouTubeOverlay][com.github.vkay94.dtpv.youtube.YouTubeOverlay].
 */
class SecondsView(context: Context, attrs: AttributeSet?) : ConstraintLayout(context, attrs) {

    private var animate: Boolean
    private var firstAnimator: ValueAnimator? = null
    private var secondAnimator: ValueAnimator?= null
    private var thirdAnimator: ValueAnimator?= null
    private var fourthAnimator: ValueAnimator?= null
    private var fifthAnimator: ValueAnimator?= null

    /**
     * Defines the duration for a full cycle of the triangle animation.
     * Each animation step takes 20% of it.
     */
    var cycleDuration = 750L
        set(value) {
            firstAnimator?.duration = value / 5.toLong()
            secondAnimator?.duration = value / 5.toLong()
            thirdAnimator?.duration = value / 5.toLong()
            fourthAnimator?.duration = value / 5.toLong()
            fifthAnimator?.duration = value / 5.toLong()
            field = value
        }

    /**
     * Sets the `TextView`'s seconds text according to the device`s language.
     */
    var seconds = 0
        set(value) {
            val textView = findViewById<TextView>(R.id.tv_seconds)
            textView.text = context.resources.getQuantityString(
                R.plurals.quick_seek_x_second, value, value
            )
            field = value
        }

    /**
     * Mirrors the triangles depending on what kind of type should be used (forward/rewind).
     */
    var isForward = true
        set(value) {
            val linearLayout = findViewById<LinearLayout>(R.id.triangle_container)
            linearLayout.rotation = if (value) 0f else 180f
            field = value
        }

    val textView: TextView
        get() = findViewById<View>(R.id.tv_seconds) as TextView


    var icon: Int = R.drawable.ic_play_triangle
        set(value) {
            if (value > 0) {
                (findViewById<View>(R.id.icon_1) as ImageView).setImageResource(value)
                (findViewById<View>(R.id.icon_2) as ImageView).setImageResource(value)
                (findViewById<View>(R.id.icon_3) as ImageView).setImageResource(value)
            }
            field = value
        }

    /**
     * Starts the triangle animation
     */
    fun start() {
        stop()
        animate = true
        firstAnimator?.start()
    }

    /**
     * Stops the triangle animation
     */
    fun stop() {
        animate = false
        firstAnimator?.cancel()
        secondAnimator?.cancel()
        thirdAnimator?.cancel()
        fourthAnimator?.cancel()
        fifthAnimator?.cancel()
        reset()
    }

    private fun reset() {
        findViewById<View>(R.id.icon_1).alpha = 0f
        findViewById<View>(R.id.icon_2).alpha = 0f
        findViewById<View>(R.id.icon_3).alpha = 0f
    }

    private inner class CustomValueAnimator(
        start: Runnable,
        update: Consumer<Float?>,
        end: Runnable
    ) : ValueAnimator() {
        init {
            duration = cycleDuration / 5.toLong()
            setFloatValues(0f, 1f)
            addUpdateListener { animation -> update.accept(animation.animatedValue as Float) }
            addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    start.run()
                }

                override fun onAnimationEnd(animation: Animator) {
                    end.run()
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }
    }

    init {
        animate = false
        LayoutInflater.from(context).inflate(R.layout.yt_seconds_view, this, true)
        firstAnimator = CustomValueAnimator(
            {
                findViewById<View>(R.id.icon_1).alpha = 0f
                findViewById<View>(R.id.icon_2).alpha = 0f
                findViewById<View>(R.id.icon_3).alpha = 0f
            },
            { aFloat ->
                findViewById<View>(R.id.icon_1).alpha = aFloat!!
            }, { if (animate) secondAnimator?.start() })

        secondAnimator = CustomValueAnimator(
            {
                findViewById<View>(R.id.icon_1).alpha = 1f
                findViewById<View>(R.id.icon_2).alpha = 0f
                findViewById<View>(R.id.icon_3).alpha = 0f
            },
            { aFloat ->
                findViewById<View>(R.id.icon_2).alpha = aFloat!!
            }, { if (animate) thirdAnimator?.start() })

        thirdAnimator = CustomValueAnimator({
            findViewById<View>(R.id.icon_1).alpha = 1f
            findViewById<View>(R.id.icon_2).alpha = 1f
            findViewById<View>(R.id.icon_3).alpha = 0f
        }, { aFloat ->
            findViewById<View>(R.id.icon_1).alpha = 1f - findViewById<View>(R.id.icon_3).alpha
            findViewById<View>(R.id.icon_3).alpha = aFloat!!
        }, { if (animate) fourthAnimator?.start() })

        fourthAnimator = CustomValueAnimator(
            {
                findViewById<View>(R.id.icon_1).alpha = 0f
                findViewById<View>(R.id.icon_2).alpha = 1f
                findViewById<View>(R.id.icon_3).alpha = 1f
            },
            { aFloat:Float? ->
                aFloat?.let { findViewById<View>(R.id.icon_2).alpha = 1f - it }
            },
            { if (animate) fifthAnimator?.start() })

        fifthAnimator = CustomValueAnimator(
            {
                 findViewById<View>(R.id.icon_1).alpha = 0f
                 findViewById<View>(R.id.icon_2).alpha = 0f
                 findViewById<View>(R.id.icon_3).alpha = 1f
             },
            { aFloat:Float? ->
                aFloat?.let { findViewById<View>(R.id.icon_3).alpha = 1f - it }
            },
            { if (animate) firstAnimator?.start() })
    }
}