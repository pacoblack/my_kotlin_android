package com.find.gang.video.lib

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.find.gang.video.lib.databinding.VideoTrimmerViewBinding
import com.find.gang.video.lib.interfaces.IVideoTrimmerView
import com.find.gang.video.lib.interfaces.VideoTrimListener
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.MAX_COUNT_RANGE
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.MAX_SHOOT_DURATION
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.RECYCLER_VIEW_PADDING
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.THUMB_WIDTH
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.VIDEO_FRAMES_WIDTH
import com.find.gang.video.lib.trim.VideoTrimmerAdapter
import com.find.gang.video.lib.widget.RangeSeekBarView
import com.find.gang.video.lib.widget.SpacesItemDecoration2
import java.lang.String
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.apply
import kotlin.math.abs
import kotlin.text.trim
import androidx.core.view.isGone
import androidx.media3.common.util.UnstableApi

class VideoTrimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), IVideoTrimmerView {

    private lateinit var binding: VideoTrimmerViewBinding
    private val mMaxWidth: Int = VIDEO_FRAMES_WIDTH
    private lateinit var mVideoPlayer: ExoPlayer
    private lateinit var mVideoThumbRecyclerView: RecyclerView
    private lateinit var mRangeSeekBarView: RangeSeekBarView
    private lateinit var mRedProgressIcon: ImageView
    private var mAverageMsPx = 0f //每毫秒所占的px
    private var averagePxMs = 0f //每px所占用的ms毫秒
    private var mSourceUri: Uri? = null
    private var mOnTrimVideoListener: VideoTrimListener? = null
    private var mDuration:Long = 0
    private var restoreState = false

    //new
    private var mLeftProgressPos: Long = 0
    private var mRightProgressPos: Long = 0
    private var mRedProgressBarPos: Long = 0
    private var scrollPos: Long = 0
    private var mScaledTouchSlop = 0
    private var lastScrollX = 0
    private var isSeeking = false
    private var isOverScaledTouchSlop = false
    private var mThumbsTotalCount = 0
    private var mRedProgressAnimator: ValueAnimator? = null
    private val mAnimationHandler = Handler(Looper.myLooper()!!)

    private fun init(context: Context) {
        binding = VideoTrimmerViewBinding.inflate(LayoutInflater.from(context), this, false)
        LayoutInflater.from(context).inflate(R.layout.video_trimmer_view, this, true)
        setupPlayer()
        mRedProgressIcon = binding.positionIcon
        mVideoThumbRecyclerView = binding.videoFramesRecyclerView
        mVideoThumbRecyclerView.setLayoutManager(
            LinearLayoutManager(
                context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        )
        mVideoThumbRecyclerView.setAdapter(VideoTrimmerAdapter(context))
        mVideoThumbRecyclerView.addOnScrollListener(mOnScrollListener)
        setUpListeners()
    }

    private fun initRangeSeekBarView() {
        if (::mRangeSeekBarView.isInitialized) return
        mLeftProgressPos = 0
        if (mDuration <= MAX_SHOOT_DURATION) {
            mThumbsTotalCount = MAX_COUNT_RANGE
            mRightProgressPos = mDuration
        } else {
            mThumbsTotalCount =
                (mDuration * 1.0f / (MAX_SHOOT_DURATION * 1.0f) * MAX_COUNT_RANGE).toInt()
            mRightProgressPos = MAX_SHOOT_DURATION
        }
        mVideoThumbRecyclerView.addItemDecoration(
            SpacesItemDecoration2(
                RECYCLER_VIEW_PADDING,
                mThumbsTotalCount
            )
        )
        mRangeSeekBarView = RangeSeekBarView(context, mLeftProgressPos, mRightProgressPos)
        mRangeSeekBarView.selectedMinValue = mLeftProgressPos
        mRangeSeekBarView.selectedMaxValue = mRightProgressPos
        mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos)
        mRangeSeekBarView.setMinShootTime(VideoTrimmerUtil.MIN_SHOOT_DURATION)
        mRangeSeekBarView.isNotifyWhileDragging = true
        mRangeSeekBarView.setOnRangeSeekBarChangeListener(mOnRangeSeekBarChangeListener)
        binding.seekBarLayout.addView(mRangeSeekBarView)
        if (mThumbsTotalCount - MAX_COUNT_RANGE > 0) {
            mAverageMsPx =
                (mDuration - MAX_SHOOT_DURATION) / (mThumbsTotalCount - MAX_COUNT_RANGE).toFloat()
        } else {
            mAverageMsPx = 0f
        }
        averagePxMs = (mMaxWidth * 1.0f / (mRightProgressPos - mLeftProgressPos))
    }

    fun initVideoByURI(videoURI: Uri) {
        mSourceUri = videoURI

        binding.videoLoader.requestFocus()
        binding.videoShootTip.text = String.format(
            context.resources.getString(R.string.video_shoot_tip),
            VideoTrimmerUtil.VIDEO_MAX_TIME
        )
    }

    private fun setupPlayer() {
        val view = binding.videoLoader
        mVideoPlayer = ExoPlayer.Builder(context).build().apply {
            view.player = this
            addListener(object : Player.Listener {
                @OptIn(UnstableApi::class)
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {

                        }
                        Player.STATE_READY -> {
                            mVideoPlayer.videoScalingMode = MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                            videoPrepared()
                        }
                        Player.STATE_ENDED -> {
                            videoCompleted()
                        }

                        Player.STATE_IDLE -> {

                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    videoSizeChange(videoSize)
                }
            })
        }
    }

    private fun startShootVideoThumbs(
        context: Context?,
        videoUri: Uri?,
        totalThumbsCount: Int,
        startPosition: Long,
        endPosition: Long,
    ) {
        // TODO:
//        VideoTrimmerUtil.shootVideoThumbInBackground(
//            context, videoUri, totalThumbsCount, startPosition, endPosition,
//            { bitmap, interval ->
//                if (bitmap != null) {
//                    UiThreadExecutor.runTask("", object : Runnable {
//                        override fun run() {
//                            mVideoThumbAdapter.addBitmaps(bitmap)
//                        }
//                    }, 0L)
//                }
//            })
    }

    private fun onCancelClicked() {
        mOnTrimVideoListener.onCancel()
    }

    private fun videoSizeChange(videoSize: VideoSize) {
        val lp: LayoutParams = binding.videoLoader.layoutParams as LayoutParams
        val videoWidth = videoSize.width
        val videoHeight = videoSize.height

        val screenWidth = binding.layoutSurfaceView.width
        val screenHeight = binding.layoutSurfaceView.height

        if (videoHeight > videoWidth) {
            lp.width = screenWidth
            lp.height = screenHeight
        } else {
            lp.width = screenWidth
            val r = videoHeight / videoWidth.toFloat()
            lp.height = (lp.width * r).toInt()
        }
        binding.videoLoader.setLayoutParams(lp)
    }

    private fun videoPrepared() {

        mDuration = mVideoPlayer.duration
        if (!this.restoreState) {
            seekTo(mRedProgressBarPos.toInt().toLong())
        } else {
            this.restoreState = false
            seekTo(mRedProgressBarPos.toInt().toLong())
        }
        initRangeSeekBarView()
        startShootVideoThumbs(context, mSourceUri, mThumbsTotalCount, 0, mDuration)
    }

    private fun videoCompleted() {
        seekTo(mLeftProgressPos)
        setPlayPauseViewIcon(false)
    }

    private fun onVideoReset() {
        mVideoPlayer.pause()
        setPlayPauseViewIcon(false)
    }

    private fun playVideoOrPause() {
        mRedProgressBarPos = mVideoPlayer.currentPosition
        if (mVideoPlayer.isPlaying) {
            mVideoPlayer.pause()
            pauseRedProgressAnimation()
        } else {
            mVideoPlayer.play()
            playingRedProgressAnimation()
        }
        setPlayPauseViewIcon(mVideoPlayer.isPlaying)
    }

    fun onVideoPause() {
        if (mVideoPlayer.isPlaying) {
            seekTo(mLeftProgressPos) //复位
            mVideoPlayer.pause()
            setPlayPauseViewIcon(false)
            mRedProgressIcon.setVisibility(GONE)
        }
    }

    fun setOnTrimVideoListener(onTrimVideoListener: VideoTrimListener) {
        mOnTrimVideoListener = onTrimVideoListener
    }

    private fun setUpListeners() {
        binding.cancelBtn.setOnClickListener { _: View? -> onCancelClicked() }

        binding.finishBtn.setOnClickListener { _: View? -> onSaveClicked() }

        binding.iconVideoPlay.setOnClickListener { _: View? -> playVideoOrPause() }
    }

    private fun onSaveClicked() {
        if (mRightProgressPos - mLeftProgressPos < VideoTrimmerUtil.MIN_SHOOT_DURATION) {
            Toast.makeText(context, "视频长不足3秒,无法上传", Toast.LENGTH_SHORT).show()
        } else {
            mVideoPlayer.pause()
            VideoTrimmerUtil.trim(
                context,
                mSourceUri!!.path,
                StorageUtil.getCacheDir(),
                mLeftProgressPos,
                mRightProgressPos,
                mOnTrimVideoListener
            )
        }
    }

    private fun seekTo(msec: Long) {
        mVideoPlayer.seekTo(msec.toInt())
        Log.d(TAG, "seekTo = " + msec)
    }

    private fun setPlayPauseViewIcon(isPlaying: Boolean) {
        binding.iconVideoPlay.setImageResource(if (isPlaying) R.drawable.ic_video_pause_black else R.drawable.ic_video_play_black)
    }

    private val mOnRangeSeekBarChangeListener: RangeSeekBarView.OnRangeSeekBarChangeListener =
        object : RangeSeekBarView.OnRangeSeekBarChangeListener {
            public override fun onRangeSeekBarValuesChanged(
                bar: RangeSeekBarView?, minValue: Long, maxValue: Long, action: Int, isMin: Boolean,
                pressedThumb: RangeSeekBarView.Thumb?,
            ) {
                Log.d(TAG, "-----minValue----->>>>>>$minValue")
                Log.d(TAG, "-----maxValue----->>>>>>$maxValue")
                mLeftProgressPos = minValue + scrollPos
                mRedProgressBarPos = mLeftProgressPos
                mRightProgressPos = maxValue + scrollPos
                Log.d(TAG, "-----mLeftProgressPos----->>>>>>$mLeftProgressPos")
                Log.d(TAG, "-----mRightProgressPos----->>>>>>$mRightProgressPos")
                when (action) {
                    MotionEvent.ACTION_DOWN -> isSeeking = false
                    MotionEvent.ACTION_MOVE -> {
                        isSeeking = true
                        seekTo(
                            (if (pressedThumb === RangeSeekBarView.Thumb.MIN) mLeftProgressPos else mRightProgressPos).toInt()
                                .toLong()
                        )
                    }

                    MotionEvent.ACTION_UP -> {
                        isSeeking = false
                        seekTo(mLeftProgressPos.toInt().toLong())
                    }

                    else -> {}
                }

                mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos)
            }
        }

    private val mOnScrollListener: RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                Log.d(TAG, "newState = $newState")
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                isSeeking = false
                val scrollX = calcScrollXDistance()
                //达不到滑动的距离
                if (abs(lastScrollX - scrollX) < mScaledTouchSlop) {
                    isOverScaledTouchSlop = false
                    return
                }
                isOverScaledTouchSlop = true
                //初始状态,why ? 因为默认的时候有35dp的空白！
                if (scrollX == -RECYCLER_VIEW_PADDING) {
                    scrollPos = 0
                    mLeftProgressPos = mRangeSeekBarView.selectedMinValue + scrollPos
                    mRightProgressPos = mRangeSeekBarView.selectedMaxValue + scrollPos
                    Log.d(TAG, "onScrolled >>>> mLeftProgressPos = $mLeftProgressPos")
                    mRedProgressBarPos = mLeftProgressPos
                } else {
                    isSeeking = true
                    scrollPos =
                        (mAverageMsPx * (RECYCLER_VIEW_PADDING + scrollX) / THUMB_WIDTH) as Long
                    mLeftProgressPos = mRangeSeekBarView.selectedMinValue + scrollPos
                    mRightProgressPos = mRangeSeekBarView.selectedMaxValue + scrollPos
                    Log.d(TAG, "onScrolled >>>> mLeftProgressPos = $mLeftProgressPos")
                    mRedProgressBarPos = mLeftProgressPos
                    if (mVideoPlayer.isPlaying) {
                        mVideoPlayer.pause()
                        setPlayPauseViewIcon(false)
                    }
                    mRedProgressIcon.setVisibility(GONE)
                    seekTo(mLeftProgressPos)
                    mRangeSeekBarView.setStartEndTime(mLeftProgressPos, mRightProgressPos)
                    mRangeSeekBarView.invalidate()
                }
                lastScrollX = scrollX
            }
        }

    /**
     * 水平滑动了多少px
     */
    private fun calcScrollXDistance(): Int {
        val layoutManager = mVideoThumbRecyclerView.layoutManager as LinearLayoutManager?
        val position = layoutManager!!.findFirstVisibleItemPosition()
        val firstVisibleChildView = layoutManager.findViewByPosition(position)
        val itemWidth = firstVisibleChildView!!.width
        return (position) * itemWidth - firstVisibleChildView.left
    }

    private fun playingRedProgressAnimation() {
        pauseRedProgressAnimation()
        playingAnimation()
        mAnimationHandler.post(mAnimationRunnable)
    }

    private fun playingAnimation() {
        if (mRedProgressIcon.isGone) {
            mRedProgressIcon.setVisibility(VISIBLE)
        }
        val params = mRedProgressIcon.layoutParams as LayoutParams
        val start = (RECYCLER_VIEW_PADDING + (mRedProgressBarPos - scrollPos) * averagePxMs).toInt()
        val end = (RECYCLER_VIEW_PADDING + (mRightProgressPos - scrollPos) * averagePxMs).toInt()
        mRedProgressAnimator = ValueAnimator.ofInt(start, end)
            .setDuration((mRightProgressPos - scrollPos) - (mRedProgressBarPos - scrollPos))
        mRedProgressAnimator!!.interpolator = LinearInterpolator()
        mRedProgressAnimator!!.addUpdateListener(AnimatorUpdateListener { animation: ValueAnimator? ->
            params.leftMargin = animation!!.getAnimatedValue() as Int
            mRedProgressIcon.setLayoutParams(params)
            Log.d(TAG, "----onAnimationUpdate--->>>>>>>$mRedProgressBarPos")
        })
        mRedProgressAnimator!!.start()
    }

    private fun pauseRedProgressAnimation() {
        mRedProgressIcon.clearAnimation()
        if (mRedProgressAnimator != null && mRedProgressAnimator!!.isRunning) {
            mAnimationHandler.removeCallbacks(mAnimationRunnable)
            mRedProgressAnimator!!.cancel()
        }
    }

    private val mAnimationRunnable = Runnable { this.updateVideoProgress() }

    init {
        init(context)
    }

    private fun updateVideoProgress() {
        val currentPosition: Long = mVideoPlayer.currentPosition
        Log.d(TAG, "updateVideoProgress currentPosition = $currentPosition")
        if (currentPosition >= (mRightProgressPos)) {
            mRedProgressBarPos = mLeftProgressPos
            pauseRedProgressAnimation()
            onVideoPause()
        } else {
            mAnimationHandler.post(mAnimationRunnable)
        }
    }

    /**
     * Cancel trim thread execut action when finish
     */
    public override fun onDestroy() {
        BackgroundExecutor.cancelAll("", true)
        UiThreadExecutor.cancelAll("")
    }

    companion object {
        private val TAG: kotlin.String = VideoTrimmerView::class.java.getSimpleName()
    }
}
