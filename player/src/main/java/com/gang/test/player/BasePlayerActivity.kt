package com.gang.test.player

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.text.TextUtils
import android.util.Base64
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.gang.test.player.dtpv.DoubleTapPlayerView
import com.homesoft.exo.extractor.AviExtractorsFactory
import java.lang.reflect.InvocationTargetException
import java.util.*

@UnstableApi class BasePlayerActivity:AppCompatActivity() {
    private var mAudioManager: AudioManager? = null
    private var mediaSession: MediaSession? = null
    private var trackSelector: DefaultTrackSelector? = null
    var playerView: CustomPlayerView? = null

    private var exoSettings: ImageButton? = null
    private var exoPlayPause: ImageButton? = null
    private var controlView: PlayerControlView? = null
    private var exoTimeBar: CustomDefaultTimeBar? = null

    private var titleView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        playerView = findViewById(R.id.video_view)

        playerView?.let {
            initPlayerView(it)
        }
        exoPlayPause = findViewById(R.id.exo_play_pause)
        exoPlayPause?.setOnClickListener { dispatchPlayPause() }
        // Prevent double tap actions in controller
        findViewById<View>(R.id.exo_bottom_bar).setOnTouchListener { _: View?, _: MotionEvent? -> true }

        // init basic controller
        val exoBasicControls = playerView?.findViewById<LinearLayout>(R.id.exo_basic_controls)
        val exoSubtitle = exoBasicControls?.findViewById<ImageButton>(R.id.exo_subtitle)
        exoBasicControls?.removeView(exoSubtitle)
        exoSettings = exoBasicControls?.findViewById(R.id.exo_settings)
        exoBasicControls?.removeView(exoSettings)
        val exoRepeat = exoBasicControls?.findViewById<ImageButton>(R.id.exo_repeat_toggle)
        exoBasicControls?.removeView(exoRepeat)

        mAudioManager = getSystemService(Activity.AUDIO_SERVICE) as AudioManager
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    fun initializePlayer(){
        val url = ""
        val isNetworkUri = Utils.isSupportedNetworkUri(Uri.parse(url))
        if (player != null) {
//            player!!.removeListener()
            player!!.clearMediaItems()
            player!!.release()
            player = null
        }
        trackSelector = DefaultTrackSelector(this)

        // https://github.com/google/ExoPlayer/issues/8571
        val aviExtractorsFactory = AviExtractorsFactory()
        aviExtractorsFactory.defaultExtractorsFactory
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
            .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)
        @SuppressLint("WrongConstant") val renderersFactory: RenderersFactory =
            DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setMapDV7ToHevc(false)
        val playerBuilder = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector!!)

        if (isNetworkUri) {
            val headers = HashMap<String, String>()
            headers["Authorization"] =
                "Basic " + Base64.encodeToString("".toByteArray(), Base64.NO_WRAP)
            val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            defaultHttpDataSourceFactory.setDefaultRequestProperties(headers)
            playerBuilder.setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    defaultHttpDataSourceFactory,
                    aviExtractorsFactory
                )
            )
        }
        player = playerBuilder.build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        player!!.setAudioAttributes(audioAttributes, true)
//        youTubeOverlay!!.player(player)
        playerView!!.player = player


        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url)
            .setMimeType("")

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .build()
        mediaItemBuilder.setMediaMetadata(mediaMetadata)

        player!!.setMediaItem(mediaItemBuilder.build(), 0)
        player!!.prepare()
    }

    private fun initPlayerView(playerView: PlayerView){
        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)
        playerView.setShowFastForwardButton(false)
        playerView.setShowRewindButton(false)
        playerView.setRepeatToggleModes(Player.REPEAT_MODE_ONE)
        playerView.controllerHideOnTouch = false
        playerView.controllerAutoShow = true
        (playerView as DoubleTapPlayerView?)!!.isDoubleTapEnabled = false

        // init time bar
        exoTimeBar = playerView.findViewById(R.id.exo_progress)
        exoTimeBar?.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {

            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {

            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {

            }
        })
        exoTimeBar?.let{
            it.setAdMarkerColor(Color.argb(0x00, 0xFF, 0xFF, 0xFF))
            it.setPlayedAdMarkerColor(Color.argb(0x98, 0xFF, 0xFF, 0xFF))
            try {
                val customDefaultTrackNameProvider = CustomDefaultTrackNameProvider(resources)
                val field = PlayerControlView::class.java.getDeclaredField("trackNameProvider")
                field.isAccessible = true
                field[controlView] = customDefaultTrackNameProvider
            } catch (e: NoSuchFieldException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        addTitle()

    }

    fun addTitle(){
        // init title
        val titleViewPaddingHorizontal = Utils.dpToPx(14)
        val titleViewPaddingVertical =
            resources.getDimensionPixelOffset(R.dimen.exo_styled_bottom_bar_time_padding)
        val centerView = playerView?.findViewById<FrameLayout>(R.id.exo_controls_background)
        titleView = TextView(this)
        titleView!!.setBackgroundResource(R.color.ui_controls_background)
        titleView!!.setTextColor(Color.WHITE)
        titleView!!.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleView!!.setPadding(
            titleViewPaddingHorizontal,
            titleViewPaddingVertical,
            titleViewPaddingHorizontal,
            titleViewPaddingVertical
        )
        titleView!!.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        titleView!!.visibility = View.GONE
        titleView!!.maxLines = 1
        titleView!!.ellipsize = TextUtils.TruncateAt.END
        titleView!!.textDirection = View.TEXT_DIRECTION_LOCALE
        centerView?.addView(titleView)
    }

    private fun dispatchPlayPause() {
        if (player == null) return
        val state = player!!.playbackState
        val methodName: String = if (state == Player.STATE_IDLE || state == Player.STATE_ENDED || !player!!.playWhenReady) {
            "dispatchPlay"
        } else {
            "dispatchPause"
        }
        try {
            val method =
                PlayerControlView::class.java.getDeclaredMethod(methodName, Player::class.java)
            method.isAccessible = true
            method.invoke(controlView, player as Player?)
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

    @JvmOverloads
    fun releasePlayer() {
        if (player != null) {

//            mediaSession.setActive(false);
            if (mediaSession != null) {
                mediaSession!!.release()
            }

            player!!.clearMediaItems()
            player!!.release()
            player = null
        }
        titleView!!.visibility = View.GONE

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Utils.adjustVolume(
                    this,
                    mAudioManager!!,
                    playerView!!,
                    keyCode == KeyEvent.KEYCODE_VOLUME_UP,
                    event.repeatCount == 0,
                    true
                )
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        @JvmField
        var player: ExoPlayer? = null
    }
}