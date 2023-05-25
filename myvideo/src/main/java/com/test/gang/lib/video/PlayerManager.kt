package com.test.gang.lib.video

import com.android.iplayer.widget.VideoPlayer

class PlayerManager {
    var videoPlayer: VideoPlayer? = null

    companion object {
        @Volatile
        private var mInstance: PlayerManager? = null

        @get:Synchronized
        val instance: PlayerManager
            get() {
                if (null == mInstance){
                    synchronized(PlayerManager::class.java) {
                        if (null == mInstance) {
                            mInstance = PlayerManager()
                        }
                    }
                }
                return mInstance!!
            }
    }
}
