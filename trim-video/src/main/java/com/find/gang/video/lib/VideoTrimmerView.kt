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
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.find.gang.video.lib.databinding.VideoTrimmerViewBinding
import com.find.gang.video.lib.interfaces.IVideoTrimmerView
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.MAX_COUNT_RANGE
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.MAX_SHOOT_DURATION
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.RECYCLER_VIEW_PADDING
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil.VIDEO_FRAMES_WIDTH
import com.find.gang.video.lib.widget.LVideoView
import com.find.gang.video.lib.widget.RangeSeekBarView
import java.lang.String
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.collections.plus
import kotlin.compareTo
import kotlin.div
import kotlin.math.abs
import kotlin.plus
import kotlin.sequences.plus
import kotlin.text.trim
import kotlin.times
import kotlin.unaryMinus

class VideoTrimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), IVideoTrimmerView {

    private lateinit var binding: VideoTrimmerViewBinding
    private val mMaxWidth: Int = VIDEO_FRAMES_WIDTH
    private var mContext: Context? = null
    private var mLinearVideo: RelativeLayout? = null
    private var mVideoView: LVideoView? = null
    private var mPlayView: ImageView? = null
    private var mVideoThumbRecyclerView: RecyclerView? = null
    private var mRangeSeekBarView: RangeSeekBarView? = null
    private var mSeekBarLayout: LinearLayout? = null
    private var mRedProgressIcon: ImageView? = null
    private var mVideoShootTipTv: TextView? = null
    private var mAverageMsPx = 0f //每毫秒所占的px
    private var averagePxMs = 0f //每px所占用的ms毫秒
    private var mSourceUri: Uri? = null
    private var mOnTrimVideoListener: VideoTrimListener? = null
    private var mDuration = 0
    private var mVideoThumbAdapter: VideoTrimmerAdapter? = null
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
        this.mContext = context
        binding = VideoTrimmerViewBinding.inflate(LayoutInflater.from(context), this, false)
        LayoutInflater.from(context).inflate(R.layout.video_trimmer_view, this, true)

        mLinearVideo = binding.layoutSurfaceView
        mVideoView = binding.videoLoader
        mPlayView = binding.iconVideoPlay
        mSeekBarLayout = binding.seekBarLayout
        mRedProgressIcon = binding.positionIcon
        mVideoShootTipTv = binding.videoShootTip
        mVideoThumbRecyclerView = binding.videoFramesRecyclerView
        mVideoThumbRecyclerView!!.setLayoutManager(
            LinearLayoutManager(
                mContext,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        )
        mVideoThumbAdapter = VideoTrimmerAdapter(mContext)
        mVideoThumbRecyclerView!!.setAdapter(mVideoThumbAdapter)
        mVideoThumbRecyclerView!!.addOnScrollListener(mOnScrollListener)
        setUpListeners()
    }

    private fun initRangeSeekBarView() {
        if (mRangeSeekBarView != null) return
        mLeftProgressPos = 0
        if (mDuration <= MAX_SHOOT_DURATION) {
            mThumbsTotalCount = MAX_COUNT_RANGE
            mRightProgressPos = mDuration.toLong()
        } else {
            mThumbsTotalCount =
                (mDuration * 1.0f / (MAX_SHOOT_DURATION * 1.0f) * MAX_COUNT_RANGE) as Int
            mRightProgressPos = MAX_SHOOT_DURATION
        }
        mVideoThumbRecyclerView!!.addItemDecoration(
            SpacesItemDecoration2(
                RECYCLER_VIEW_PADDING,
                mThumbsTotalCount
            )
        )
        mRangeSeekBarView = RangeSeekBarView(mContext, mLeftProgressPos, mRightProgressPos)
        mRangeSeekBarView!!.selectedMinValue = mLeftProgressPos
        mRangeSeekBarView!!.selectedMaxValue = mRightProgressPos
        mRangeSeekBarView!!.setStartEndTime(mLeftProgressPos, mRightProgressPos)
        mRangeSeekBarView!!.setMinShootTime(VideoTrimmerUtil.MIN_SHOOT_DURATION)
        mRangeSeekBarView!!.isNotifyWhileDragging = true
        mRangeSeekBarView!!.setOnRangeSeekBarChangeListener(mOnRangeSeekBarChangeListener)
        mSeekBarLayout!!.addView(mRangeSeekBarView)
        if (mThumbsTotalCount - MAX_COUNT_RANGE > 0) {
            mAverageMsPx =
                (mDuration - MAX_SHOOT_DURATION) / (mThumbsTotalCount - MAX_COUNT_RANGE) as Float
        } else {
            mAverageMsPx = 0f
        }
        averagePxMs = (mMaxWidth * 1.0f / (mRightProgressPos - mLeftProgressPos))
    }

    fun initVideoByURI(videoURI: Uri) {
        mSourceUri = videoURI
        mVideoView.setVideoURI(videoURI)
        mVideoView.requestFocus()
        mVideoShootTipTv!!.setText(
            String.format(
                mContext!!.getResources().getString(R.string.video_shoot_tip),
                VideoTrimmerUtil.VIDEO_MAX_TIME
            )
        )
    }

    private fun startShootVideoThumbs(
        context: Context?,
        videoUri: Uri?,
        totalThumbsCount: Int,
        startPosition: Long,
        endPosition: Long,
    ) {
        VideoTrimmerUtil.shootVideoThumbInBackground(
            context, videoUri, totalThumbsCount, startPosition, endPosition,
            { bitmap, interval ->
                if (bitmap != null) {
                    UiThreadExecutor.runTask("", object : Runnable {
                        override fun run() {
                            mVideoThumbAdapter.addBitmaps(bitmap)
                        }
                    }, 0L)
                }
            })
    }

    private fun onCancelClicked() {
        mOnTrimVideoListener.onCancel()
    }

    private fun videoPrepared(mp: MediaPlayer) {
        val lp: LayoutParams = mVideoView.getLayoutParams()
        val videoWidth = mp.getVideoWidth()
        val videoHeight = mp.getVideoHeight()

        val screenWidth = mLinearVideo!!.getWidth()
        val screenHeight = mLinearVideo!!.getHeight()

        if (videoHeight > videoWidth) {
            lp.width = screenWidth
            lp.height = screenHeight
        } else {
            lp.width = screenWidth
            val r = videoHeight / videoWidth.toFloat()
            lp.height = (lp.width * r).toInt()
        }
        mVideoView.setLayoutParams(lp)
        mDuration = mVideoView.getDuration()
        if (!this.restoreState) {
            seekTo(mRedProgressBarPos.toInt().toLong())
        } else {
            this.restoreState = false
            seekTo(mRedProgressBarPos.toInt().toLong())
        }
        initRangeSeekBarView()
        startShootVideoThumbs(mContext, mSourceUri, mThumbsTotalCount, 0, mDuration.toLong())
    }

    private fun videoCompleted() {
        seekTo(mLeftProgressPos)
        setPlayPauseViewIcon(false)
    }

    private fun onVideoReset() {
        mVideoView.pause()
        setPlayPauseViewIcon(false)
    }

    private fun playVideoOrPause() {
        mRedProgressBarPos = mVideoView.getCurrentPosition()
        if (mVideoView.isPlaying()) {
            mVideoView.pause()
            pauseRedProgressAnimation()
        } else {
            mVideoView.start()
            playingRedProgressAnimation()
        }
        setPlayPauseViewIcon(mVideoView.isPlaying())
    }

    fun onVideoPause() {
        if (mVideoView.isPlaying()) {
            seekTo(mLeftProgressPos) //复位
            mVideoView.pause()
            setPlayPauseViewIcon(false)
            mRedProgressIcon!!.setVisibility(GONE)
        }
    }

    fun setOnTrimVideoListener(onTrimVideoListener: VideoTrimListener) {
        mOnTrimVideoListener = onTrimVideoListener
    }

    private fun setUpListeners() {
        findViewById<View?>(R.id.cancelBtn).setOnClickListener(OnClickListener { view: View? -> onCancelClicked() })

        findViewById<View?>(R.id.finishBtn).setOnClickListener(OnClickListener { view: View? -> onSaveClicked() })
        mVideoView.setOnPreparedListener({ mp ->
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            videoPrepared(mp)
        })
        mVideoView.setOnCompletionListener({ mp -> videoCompleted() })
        mPlayView!!.setOnClickListener(OnClickListener { v: View? -> playVideoOrPause() })
    }

    private fun onSaveClicked() {
        if (mRightProgressPos - mLeftProgressPos < VideoTrimmerUtil.MIN_SHOOT_DURATION) {
            Toast.makeText(mContext, "视频长不足3秒,无法上传", Toast.LENGTH_SHORT).show()
        } else {
            mVideoView.pause()
            VideoTrimmerUtil.trim(
                mContext,
                mSourceUri!!.getPath(),
                StorageUtil.getCacheDir(),
                mLeftProgressPos,
                mRightProgressPos,
                mOnTrimVideoListener
            )
        }
    }

    private fun seekTo(msec: Long) {
        mVideoView.seekTo(msec.toInt())
        Log.d(TAG, "seekTo = " + msec)
    }

    private fun setPlayPauseViewIcon(isPlaying: Boolean) {
        mPlayView!!.setImageResource(if (isPlaying) R.drawable.ic_video_pause_black else R.drawable.ic_video_play_black)
    }

    private val mOnRangeSeekBarChangeListener: RangeSeekBarView.OnRangeSeekBarChangeListener =
        object : RangeSeekBarView.OnRangeSeekBarChangeListener() {
            public override fun onRangeSeekBarValuesChanged(
                bar: RangeSeekBarView?, minValue: Long, maxValue: Long, action: Int, isMin: Boolean,
                pressedThumb: RangeSeekBarView.Thumb?,
            ) {
                Log.d(TAG, "-----minValue----->>>>>>" + minValue)
                Log.d(TAG, "-----maxValue----->>>>>>" + maxValue)
                mLeftProgressPos = minValue + scrollPos
                mRedProgressBarPos = mLeftProgressPos
                mRightProgressPos = maxValue + scrollPos
                Log.d(TAG, "-----mLeftProgressPos----->>>>>>" + mLeftProgressPos)
                Log.d(TAG, "-----mRightProgressPos----->>>>>>" + mRightProgressPos)
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

                mRangeSeekBarView!!.setStartEndTime(mLeftProgressPos, mRightProgressPos)
            }
        }

    private val mOnScrollListener: RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                Log.d(TAG, "newState = " + newState)
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
                    Log.d(TAG, "onScrolled >>>> mLeftProgressPos = " + mLeftProgressPos)
                    mRedProgressBarPos = mLeftProgressPos
                } else {
                    isSeeking = true
                    scrollPos =
                        (mAverageMsPx * (RECYCLER_VIEW_PADDING + scrollX) / THUMB_WIDTH) as Long
                    mLeftProgressPos = mRangeSeekBarView.selectedMinValue + scrollPos
                    mRightProgressPos = mRangeSeekBarView.selectedMaxValue + scrollPos
                    Log.d(TAG, "onScrolled >>>> mLeftProgressPos = " + mLeftProgressPos)
                    mRedProgressBarPos = mLeftProgressPos
                    if (mVideoView.isPlaying()) {
                        mVideoView.pause()
                        setPlayPauseViewIcon(false)
                    }
                    mRedProgressIcon!!.setVisibility(GONE)
                    seekTo(mLeftProgressPos)
                    mRangeSeekBarView!!.setStartEndTime(mLeftProgressPos, mRightProgressPos)
                    mRangeSeekBarView!!.invalidate()
                }
                lastScrollX = scrollX
            }
        }

    /**
     * 水平滑动了多少px
     */
    private fun calcScrollXDistance(): Int {
        val layoutManager = mVideoThumbRecyclerView!!.getLayoutManager() as LinearLayoutManager?
        val position = layoutManager!!.findFirstVisibleItemPosition()
        val firstVisibleChildView = layoutManager.findViewByPosition(position)
        val itemWidth = firstVisibleChildView!!.getWidth()
        return (position) * itemWidth - firstVisibleChildView.getLeft()
    }

    private fun playingRedProgressAnimation() {
        pauseRedProgressAnimation()
        playingAnimation()
        mAnimationHandler.post(mAnimationRunnable)
    }

    private fun playingAnimation() {
        if (mRedProgressIcon!!.getVisibility() == GONE) {
            mRedProgressIcon!!.setVisibility(VISIBLE)
        }
        val params = mRedProgressIcon!!.getLayoutParams() as LayoutParams
        val start = (RECYCLER_VIEW_PADDING + (mRedProgressBarPos - scrollPos) * averagePxMs) as Int
        val end = (RECYCLER_VIEW_PADDING + (mRightProgressPos - scrollPos) * averagePxMs) as Int
        mRedProgressAnimator = ValueAnimator.ofInt(start, end)
            .setDuration((mRightProgressPos - scrollPos) - (mRedProgressBarPos - scrollPos))
        mRedProgressAnimator!!.setInterpolator(LinearInterpolator())
        mRedProgressAnimator!!.addUpdateListener(AnimatorUpdateListener { animation: ValueAnimator? ->
            params.leftMargin = animation!!.getAnimatedValue() as Int
            mRedProgressIcon!!.setLayoutParams(params)
            Log.d(TAG, "----onAnimationUpdate--->>>>>>>" + mRedProgressBarPos)
        })
        mRedProgressAnimator!!.start()
    }

    private fun pauseRedProgressAnimation() {
        mRedProgressIcon!!.clearAnimation()
        if (mRedProgressAnimator != null && mRedProgressAnimator!!.isRunning()) {
            mAnimationHandler.removeCallbacks(mAnimationRunnable)
            mRedProgressAnimator!!.cancel()
        }
    }

    private val mAnimationRunnable = Runnable { this.updateVideoProgress() }

    init {
        init(context)
    }

    private fun updateVideoProgress() {
        val currentPosition: Long = mVideoView.getCurrentPosition()
        Log.d(TAG, "updateVideoProgress currentPosition = " + currentPosition)
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
