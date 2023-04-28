package com.test.gang.lib.player

import android.content.Context

abstract class MediaFactory<M : AbstractMediaPlayer?> {
    /**
     * 构造播放器解码器
     * @param context 上下文
     * @return 继承自AbstractMediaPlayer的解码器
     */
    abstract fun createPlayer(context: Context): M
}