package com.test.gang.lib.video

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
//import com.gang.test.player.PlayerActivity
//import com.gang.test.player.Utils
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory
import com.google.android.exoplayer2.extractor.ts.TsExtractor
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import java.lang.reflect.InvocationTargetException
import java.util.*


class OnlyPlayActivity : AppCompatActivity() {
    private var frameRateMatching: Boolean = false
    private var playerListener: PlayerListener? = null
    private var subtitleUri: Uri = Uri.EMPTY

    //    private  var mediaSession: MediaSession? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var playerView: StyledPlayerView
    private lateinit var exoPlayPause: View
    private lateinit var mAudioManager: AudioManager
    private lateinit var coordinatorLayout: CoordinatorLayout
    private val moviesFolderUri: Uri = Uri.EMPTY
    private var play = false

    var displayManager: DisplayManager? = null
    var displayListener: DisplayManager.DisplayListener? = null
    private var controlView: PlayerControlView? = null

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
        mAudioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        exoPlayPause = findViewById(R.id.exo_play_pause)
        playerView = findViewById(R.id.video_view)
        controlView = playerView.findViewById(R.id.exo_controller)
        playerListener = PlayerListener()
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

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    protected fun openFile(pickerInitialUri: Uri?) {

    }

    fun initializePlayer() {
        trackSelector = DefaultTrackSelector(this)

        if (player != null) {
            player!!.removeListener(playerListener!!)
            player!!.clearMediaItems()
            player!!.release()
            player = null
        }

        // https://github.com/google/ExoPlayer/issues/8571
        val aviExtractorsFactory = AviExtractorsFactory()
        aviExtractorsFactory.defaultExtractorsFactory
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
            .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

        val renderersFactory: RenderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val playerBuilder = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, aviExtractorsFactory))

        val headers = HashMap<String, String>()
        val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
        defaultHttpDataSourceFactory.setDefaultRequestProperties(headers)
        playerBuilder.setMediaSourceFactory(
            DefaultMediaSourceFactory(
                defaultHttpDataSourceFactory,
                aviExtractorsFactory
            )
        )

        player = playerBuilder.build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        player!!.setAudioAttributes(audioAttributes, true)
        playerView.player = player

//        if (mediaSession != null) {
//            mediaSession!!.release()
//        }
//        if (player!!.canAdvertiseSession()) {
//            try {
//                mediaSession = MediaSession.Builder(this, player!!).build()
//            } catch (e: IllegalStateException) {
//                e.printStackTrace()
//            }
//        }

        playerView.controllerShowTimeoutMs = -1

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(moviesFolderUri)
            .setMimeType(MimeTypes.BASE_TYPE_VIDEO)

        if (getVideoTitle() != null) {
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(getVideoTitle())
                .setDisplayTitle(getVideoTitle())
                .build()
            mediaItemBuilder.setMediaMetadata(mediaMetadata)
        }

        if (hasSubTitle()) {
            val subtitle = buildSubtitle(this, subtitleUri, null, true)
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitle))
        }
        player!!.setMediaItem(mediaItemBuilder.build(), 0)

        if (loudnessEnhancer != null) {
            loudnessEnhancer!!.release()
        }
        try {
            loudnessEnhancer = LoudnessEnhancer(
                player!!.audioSessionId
            )
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
        notifyAudioSessionUpdate(true)
        player!!.prepare()
    }

    fun setSelectedTracks(subtitleId: String?, audioId: String?) {
        if ("#none" == subtitleId) {
            if (trackSelector == null) {
                return
            }
            trackSelector!!.setParameters(
                trackSelector!!.buildUponParameters().setDisabledTextTrackSelectionFlags(
                    C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED
                )
            )
        }
        val subtitleGroup = getTrackGroupFromFormatId(C.TRACK_TYPE_TEXT, subtitleId)
        val audioGroup = getTrackGroupFromFormatId(C.TRACK_TYPE_AUDIO, audioId)
        val overridesBuilder = TrackSelectionParameters.Builder(this)
        var trackSelectionOverride: TrackSelectionOverride? = null
        val tracks: MutableList<Int> = ArrayList()
        tracks.add(0)
        if (subtitleGroup != null) {
            trackSelectionOverride = TrackSelectionOverride(subtitleGroup, tracks)
            overridesBuilder.addOverride(trackSelectionOverride)
        }
        if (audioGroup != null) {
            trackSelectionOverride = TrackSelectionOverride(audioGroup, tracks)
            overridesBuilder.addOverride(trackSelectionOverride)
        }
        if (player != null) {
            val trackSelectionParametersBuilder = player!!.trackSelectionParameters.buildUpon()
            if (trackSelectionOverride != null) {
                trackSelectionParametersBuilder.setOverrideForType(trackSelectionOverride)
            }
            player!!.trackSelectionParameters =
                trackSelectionParametersBuilder.build()
        }
    }

    fun getSelectedTrack(trackType: Int): String? {
        if (player == null) {
            return null
        }
        val tracks = player!!.currentTracks

        // Disabled (e.g. selected subtitle "None" - different than default)
        if (!tracks.isTypeSelected(trackType)) {
            return "#none"
        }

        // Audio track set to "Auto"
        if (trackType == C.TRACK_TYPE_AUDIO) {
            if (!hasOverrideType(C.TRACK_TYPE_AUDIO)) {
                return null
            }
        }
        for (group in tracks.groups) {
            if (group.isSelected && group.type == trackType) {
                val format = group.mediaTrackGroup.getFormat(0)
                return format.id
            }
        }
        return null
    }

    private fun getTrackGroupFromFormatId(trackType: Int, id: String?): TrackGroup? {
        if (id == null && trackType == C.TRACK_TYPE_AUDIO || player == null) {
            return null
        }
        for (group in player!!.currentTracks.groups) {
            if (group.type == trackType) {
                val trackGroup = group.mediaTrackGroup
                val format = trackGroup.getFormat(0)
                if (id == format.id) {
                    return trackGroup
                }
            }
        }
        return null
    }
    private fun dispatchPlayPause() {
        if (player == null) return
        val state = player!!.playbackState
        val methodName: String
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED || !player!!.playWhenReady) {
            methodName = "dispatchPlay"
            shortControllerTimeout = true
        } else {
            methodName = "dispatchPause"
        }
        try {
            val method =
                PlayerControlView::class.java.getDeclaredMethod(methodName, Player::class.java)
            method.isAccessible = true
//            method.invoke(controlView, PlayerActivity.player as Player?)
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        }
    }

    fun showError(error: ExoPlaybackException) {
        val errorGeneral = error.localizedMessage
        val errorDetailed: String = when (error.type) {
            ExoPlaybackException.TYPE_SOURCE -> error.sourceException.localizedMessage
            ExoPlaybackException.TYPE_RENDERER -> error.rendererException.localizedMessage
            ExoPlaybackException.TYPE_UNEXPECTED -> error.unexpectedException.localizedMessage
            ExoPlaybackException.TYPE_REMOTE -> errorGeneral
            else -> errorGeneral
        }
//        showSnack(errorGeneral, errorDetailed)
    }

    private fun hasOverrideType(trackType: Int): Boolean {
        val trackSelectionParameters = player!!.trackSelectionParameters
        for (override in trackSelectionParameters.overrides.values) {
            if (override.type == trackType) return true
        }
        return false
    }

    fun hasSubTitle():Boolean{
        return false
    }

    fun getVideoTitle():String{
        return ""
    }

    fun notifyAudioSessionUpdate(active: Boolean) {
        val intent =
            Intent(if (active) AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player!!.audioSessionId)
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        if (active) {
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
        }
        try {
            sendBroadcast(intent)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @JvmOverloads
    fun releasePlayer(save: Boolean = true) {

        if (player != null) {
            notifyAudioSessionUpdate(false)
            player!!.removeListener(playerListener!!)
            player!!.clearMediaItems()
            player!!.release()
            player = null
        }
    }
    private inner class PlayerListener : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (loudnessEnhancer != null) {
                loudnessEnhancer!!.release()
            }
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }
            notifyAudioSessionUpdate(true)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {

        }

        @SuppressLint("UnsafeOptInUsageError")
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                var switched = false
                if (frameRateMatching) {
                    if (play) {
                        if (displayManager == null) {
                            displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
                        }
                        if (displayListener == null) {
                            displayListener = object : DisplayManager.DisplayListener {
                                override fun onDisplayAdded(displayId: Int) {}
                                override fun onDisplayRemoved(displayId: Int) {}
                                override fun onDisplayChanged(displayId: Int) {
                                    if (play) {
                                        play = false
                                        displayManager!!.unregisterDisplayListener(this)
                                        if (player != null) {
                                            player!!.play()
                                        }
                                        if (playerView != null) {
                                            playerView!!.hideController()
                                        }
                                    }
                                }
                            }
                        }
                        displayManager!!.registerDisplayListener(displayListener, null)
                    }
//                    switched = Utils.switchFrameRate(this@OnlyPlayActivity, moviesFolderUri!!, play)
                }
                if (!switched) {
                    if (displayManager != null) {
                        displayManager!!.unregisterDisplayListener(displayListener)
                    }
                    if (play) {
                        play = false
                        player!!.play()
                        playerView!!.hideController()
                    }
                }
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            if (error is ExoPlaybackException) {
                if (error.type == ExoPlaybackException.TYPE_SOURCE) {
                    releasePlayer(false)
                    return
                }
            }
        }
    }

    companion object {
        var loudnessEnhancer: LoudnessEnhancer? = null

        var shortControllerTimeout = false

        @JvmField
        var player: ExoPlayer? = null

        @SuppressLint("UnsafeOptInUsageError")
        @JvmStatic
        fun buildSubtitle(
            context: Context?,
            uri: Uri,
            _subtitleName: String?,
            selected: Boolean
        ): MediaItem.SubtitleConfiguration {
            var subtitleName = _subtitleName
            val subtitleMime = SubtitleUtils.getSubtitleMime(uri)
            val subtitleLanguage = SubtitleUtils.getSubtitleLanguage(uri)
//            if (subtitleLanguage == null && subtitleName == null) subtitleName = Utils.getFileName(
//                context!!, uri
//            )
            val subtitleConfigurationBuilder = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(subtitleMime)
                .setLanguage(subtitleLanguage)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .setLabel(subtitleName)
            if (selected) {
                subtitleConfigurationBuilder.setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            }
            return subtitleConfigurationBuilder.build()
        }
    }
}