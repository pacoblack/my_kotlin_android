package com.test.gang.lib.player

import android.content.Context

abstract class AbstractMediaPlayer(protected var context: Context) :
    IMediaPlayer {
    protected val TAG = AbstractMediaPlayer::class.java.simpleName
    protected var mListener //播放器监听器
            : OnMediaEventListener? = null

    override fun setMediaEventListener(listener: OnMediaEventListener?) {
        mListener = listener
    }

    override fun release() {
        mListener = null
    }
}