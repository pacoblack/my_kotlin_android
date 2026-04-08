package com.find.gang.video.lib.widget

import android.content.Context
import android.util.AttributeSet
import androidx.media3.ui.PlayerView

class LVideoView: PlayerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )
}