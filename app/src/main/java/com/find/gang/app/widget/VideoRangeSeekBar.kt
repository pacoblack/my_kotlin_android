package com.find.gang.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class VideoRangeSeekBar(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var bgPaint: Paint? = null
    private var rangePaint: Paint? = null
    private var thumbPaint: Paint? = null
    private var leftThumbX = 0f
    private var rightThumbX = 0f
    private var totalWidth = 0f
    private val thumbRadius = 30f
    private val padding = 20f
    private var listener: OnRangeChangeListener? = null
    private var downX = 0f
    private var activeThumb = -1 // 0-left, 1-right
    private var durationUs: Long = 0 // 视频总时长（微秒）

    init {
        init()
    }

    private fun init() {
        bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint!!.setColor(0x33000000)
        rangePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        rangePaint!!.setColor(-0x55994496) // 绿色半透明
        thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        thumbPaint!!.setColor(-0x1)
        thumbPaint!!.setShadowLayer(4f, 0f, 0f, -0x56000000)
    }

    fun setDuration(durationUs: Long) {
        this.durationUs = durationUs
        // 初始化滑块位置：默认选取中间20%～80%
        leftThumbX = padding + (totalWidth - 2 * padding) * 0.2f
        rightThumbX = padding + (totalWidth - 2 * padding) * 0.8f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        totalWidth = w.toFloat()
        if (durationUs > 0) {
            leftThumbX = padding + (totalWidth - 2 * padding) * 0.2f
            rightThumbX = padding + (totalWidth - 2 * padding) * 0.8f
        } else {
            leftThumbX = padding
            rightThumbX = totalWidth - padding
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 背景矩形
        val bgRect = RectF(padding, height / 2f - 20, totalWidth - padding, height / 2f + 20)
        canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint!!)
        // 选中区域矩形
        val rangeRect = RectF(leftThumbX, height / 2f - 20, rightThumbX, height / 2f + 20)
        canvas.drawRoundRect(rangeRect, 20f, 20f, rangePaint!!)
        // 左右滑块圆点
        canvas.drawCircle(leftThumbX, height / 2f, thumbRadius, thumbPaint!!)
        canvas.drawCircle(rightThumbX, height / 2f, thumbRadius, thumbPaint!!)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        when (event.action) {
            MotionEvent.ACTION_DOWN -> if (abs(x - leftThumbX) <= thumbRadius) {
                activeThumb = 0
            } else if (abs(x - rightThumbX) <= thumbRadius) {
                activeThumb = 1
            } else if (x in leftThumbX..rightThumbX) {
                // 按在中间区域，记录位置用于整体移动
                activeThumb = -2
                downX = x
            }

            MotionEvent.ACTION_MOVE -> if (activeThumb == 0) {
                leftThumbX = min(rightThumbX - 20, max(padding, x))
                invalidate()
                notifyRangeChanged()
            } else if (activeThumb == 1) {
                rightThumbX = max(leftThumbX + 20, min(totalWidth - padding, x))
                invalidate()
                notifyRangeChanged()
            } else if (activeThumb == -2) {
                val delta = x - downX
                val newLeft = leftThumbX + delta
                val newRight = rightThumbX + delta
                if (newLeft >= padding && newRight <= totalWidth - padding) {
                    leftThumbX = newLeft
                    rightThumbX = newRight
                    downX = x
                    invalidate()
                    notifyRangeChanged()
                }
            }

            MotionEvent.ACTION_UP -> activeThumb = -1
        }
        return true
    }

    private fun notifyRangeChanged() {
        if (listener != null && durationUs > 0) {
            val leftPercent = (leftThumbX - padding) / (totalWidth - 2 * padding)
            val rightPercent = (rightThumbX - padding) / (totalWidth - 2 * padding)
            val startUs = (durationUs * leftPercent).toLong()
            val endUs = (durationUs * rightPercent).toLong()
            listener!!.onRangeChanged(startUs, endUs)
        }
    }

    fun setOnRangeChangeListener(listener: OnRangeChangeListener?) {
        this.listener = listener
    }

    interface OnRangeChangeListener {
        fun onRangeChanged(startUs: Long, endUs: Long)
    }
}