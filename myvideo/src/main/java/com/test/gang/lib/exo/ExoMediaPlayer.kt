package com.test.gang.lib.exo

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.view.Surface
import android.view.SurfaceHolder
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.video.VideoSize
import com.test.gang.lib.player.AbstractMediaPlayer
import com.test.gang.lib.player.IMediaPlayer
import java.io.IOException

class ExoMediaPlayer(context: Context) : AbstractMediaPlayer(context),
    Player.Listener {
    val mediaPlayer: ExoPlayer?
    private var isVideoPlaying //用这个boolean标记是否首帧播放
            = false

    override fun setLooping(loop: Boolean) {
        if (null != mediaPlayer) mediaPlayer.repeatMode =
            if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        if (null != mediaPlayer) mediaPlayer.volume = (leftVolume + rightVolume) / 2
    }

    override fun setBufferTimeMax(timeSecond: Float) {
        //不支持
    }

    override fun setSurface(surface: Surface?) {
        mediaPlayer?.setVideoSurface(surface)
    }

    override fun setDisplay(surfaceHolder: SurfaceHolder?) {
//        if(null!=mMediaPlayer) mMediaPlayer.setDisplay(surfaceHolder);
        if (null != surfaceHolder) {
            setSurface(surfaceHolder.surface)
        } else {
            setSurface(null)
        }
    }

    @Throws(IOException::class,
        IllegalArgumentException::class,
        SecurityException::class,
        IllegalStateException::class)
    override fun setDataSource(dataSource: String) {
        setDataSource(dataSource, null)
    }

    @Throws(IOException::class,
        IllegalArgumentException::class,
        SecurityException::class,
        IllegalStateException::class)
    override fun setDataSource(dataSource: String, headers: Map<String, String>?) {
        mediaPlayer?.setMediaSource(ExoMediaSourceHelper.getInstance(
            context).getMediaSource(dataSource, headers))
    }

    @Throws(IOException::class, IllegalArgumentException::class, IllegalStateException::class)
    override fun setDataSource(dataSource: AssetFileDescriptor?) {
        //Exo不支持
    }

    override fun setTimeout(prepareTimeout: Long, readTimeout: Long) {
        //Exo不支持
    }

    override fun setSpeed(speed: Float) {
        if (null != mediaPlayer) {
            val parameters = PlaybackParameters(speed)
            mediaPlayer.playbackParameters = parameters
        }
    }

    @JvmOverloads
    @Throws(IllegalStateException::class)
    override fun seekTo(msec: Long, accurate: Boolean) {
        mediaPlayer?.seekTo(msec)
    }

    /**
     * 是否正在播放 ExoMediaPlayer 解码器这个方法只能在main线程中被调用,否则会报错误：java.lang.IllegalStateException: Player is accessed on the wrong thread.
     * @return 返回是否正在播放
     */
    override val isPlaying: Boolean
        get() {
            if (mediaPlayer == null) return false
            return when (mediaPlayer.playbackState) {
                Player.STATE_BUFFERING, Player.STATE_READY -> mediaPlayer.playWhenReady
                Player.STATE_IDLE, Player.STATE_ENDED -> false
                else -> false
            }
        }

    /**
     * ExoMediaPlayer 解码器这个方法只能在main线程中被调用,否则会报错误：java.lang.IllegalStateException: Player is accessed on the wrong thread.
     * @return 返回当前正在播放的位置
     */
    override val currentPosition: Long
        get() {
            if (null != mediaPlayer) {
                mListener?.onBufferUpdate(this,
                    mediaPlayer.bufferedPercentage)
                return mediaPlayer.currentPosition
            }
            return 0
        }

    /**
     * ExoMediaPlayer 解码器这个方法只能在main线程中被调用,否则会报错误：java.lang.IllegalStateException: Player is accessed on the wrong thread.
     * @return
     */
    override val duration: Long
        get() = mediaPlayer?.duration ?: 0
    override val buffer: Int
        get() {
            try {
                if (null != mediaPlayer) {
                    return mediaPlayer.bufferedPercentage
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return 0
        }

    @Throws(IOException::class, IllegalStateException::class)
    override fun prepare() {
        isVideoPlaying = false
        mediaPlayer?.prepare()
    }

    @Throws(IllegalStateException::class)
    override fun prepareAsync() {
        isVideoPlaying = false
        mediaPlayer?.prepare()
    }

    override fun start() {
        if (null != mediaPlayer) mediaPlayer.playWhenReady = true
    }

    override fun pause() {
        if (null != mediaPlayer) mediaPlayer.playWhenReady = false
    }

    override fun stop() {
        mediaPlayer?.stop()
    }

    override fun reset() {
        if (mediaPlayer != null) {
            mediaPlayer.stop()
            mediaPlayer.clearMediaItems()
            mediaPlayer.setVideoSurface(null)
        }
    }

    override fun release() {
        if (mediaPlayer != null) {
            mediaPlayer.removeListener(this)
            mediaPlayer.release()
        }
        super.release()
    }

    //==========================================EXO解码器回调=========================================
    override fun onPlaybackStateChanged(playbackState: Int) {
//        Logger.d(TAG,"onPlaybackStateChanged-->playbackState:"+playbackState+",isPlaying:"+isPlaying);
        when (playbackState) {
            Player.STATE_BUFFERING -> mListener?.onInfo(this,
                IMediaPlayer.MEDIA_INFO_BUFFERING_START,
                0)
            Player.STATE_READY -> {
                if (null != mediaPlayer) mediaPlayer.playWhenReady = true
                if (null != mListener) {
                    if (isVideoPlaying) {
                        mListener?.onInfo(this,
                            IMediaPlayer.MEDIA_INFO_BUFFERING_END,
                            0) //如果还未进行过播放,则被认为是首帧播放
                    } else {
                        mListener?.onPrepared(this)
                        mListener?.onInfo(this,
                            IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START,
                            0) //如果还未进行过播放,则被认为是首帧播放
                    }
                }
                isVideoPlaying = true
            }
            Player.STATE_ENDED -> mListener?.onCompletion(this)
            else -> mListener?.onInfo(this, playbackState, 0)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        mListener?.onError(this, error.errorCode, 0)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        mListener?.onVideoSizeChanged(this,
            videoSize.width,
            videoSize.height,
            0,
            0)
    }

    init {
        mediaPlayer = ExoPlayer.Builder(context!!,
            DefaultRenderersFactory(context),
            DefaultMediaSourceFactory(context))
            .build()
        mediaPlayer.addListener(this)
    }
}