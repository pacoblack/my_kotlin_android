package com.test.gang.lib.exo

import android.content.Context
import com.test.gang.lib.player.MediaFactory

class ExoPlayerFactory : MediaFactory<ExoMediaPlayer?>() {
    override fun createPlayer(context: Context): ExoMediaPlayer {
        return ExoMediaPlayer(context)
    }

    companion object {
        fun create(): ExoPlayerFactory {
            return ExoPlayerFactory()
        }
    }
}