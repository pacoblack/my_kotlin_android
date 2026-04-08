package com.find.gang.video.lib.toolbox

import android.content.Context

object VideoTrimmerUtil {
    private val TAG: String = VideoTrimmerUtil::class.java.getSimpleName()
    const val MIN_SHOOT_DURATION: Long = 3000L // 最小剪辑时间3s
    const val VIDEO_MAX_TIME: Int = 10 // 10秒
    val MAX_SHOOT_DURATION: Long = VIDEO_MAX_TIME * 1000L //视频最多剪切多长时间10s

    const val MAX_COUNT_RANGE: Int = 10 //seekBar的区域内一共有多少张图片
    private val SCREEN_WIDTH_FULL: Int = 0
    val RECYCLER_VIEW_PADDING: Int = 0
    val VIDEO_FRAMES_WIDTH: Int = SCREEN_WIDTH_FULL - RECYCLER_VIEW_PADDING * 2
    val THUMB_WIDTH: Int = (SCREEN_WIDTH_FULL - RECYCLER_VIEW_PADDING * 2) / VIDEO_MAX_TIME
    private val THUMB_HEIGHT: Int = 0

    fun getScreenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    fun getRecyclerPadding(context: Context): Int {
        return UnitConverter.dpToPx(35, context)
    }

    fun getThumbHeight(context: Context): Int {
        return UnitConverter.dpToPx(50, context)
    }
}