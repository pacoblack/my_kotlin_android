package com.test.gang.lib.video

import android.app.Activity
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView

class OnlyPlayActivity : AppCompatActivity() {
    private lateinit var playerView: StyledPlayerView
    private lateinit var exoPlayPause: View
    private lateinit var mAudioManager: AudioManager
    private lateinit var coordinatorLayout: CoordinatorLayout
    private val moviesFolderUri: Uri = Uri.EMPTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_only)

        if (Build.VERSION.SDK_INT >= 31) {
            val window = window
            if (window != null) {
                window.setDecorFitsSystemWindows(false)
                val windowInsetsController = window.insetsController
                if (windowInsetsController != null) {
                    // On Android 12 BEHAVIOR_DEFAULT allows system gestures without visible system bars
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_DEFAULT
                }
            }
        }

        val launchIntent = intent
        val action = launchIntent.action
        val type = launchIntent.type
        if ("com.test.gang.lib.video.action.SHORTCUT_VIDEOS" == action) {
            openFile(moviesFolderUri)
        }

        coordinatorLayout = findViewById(R.id.player_root)
        mAudioManager = getSystemService(Activity.AUDIO_SERVICE) as AudioManager
        exoPlayPause = findViewById(R.id.exo_play_pause)
        playerView = findViewById(R.id.video_view)

        initPlayerView()
    }

    private fun initPlayerView(){
        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)
        playerView.setShowFastForwardButton(false)
        playerView.setShowRewindButton(false)
        playerView.setRepeatToggleModes(Player.REPEAT_MODE_ONE)
        playerView.controllerHideOnTouch = false
        playerView.controllerAutoShow = true
    }

    protected fun openFile(pickerInitialUri: Uri?) {

    }
}