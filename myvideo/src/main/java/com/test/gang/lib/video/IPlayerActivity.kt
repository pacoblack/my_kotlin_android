package com.test.gang.lib.video

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.android.iplayer.base.AbstractMediaPlayer
import com.android.iplayer.controller.VideoController
import com.android.iplayer.interfaces.IRenderView
import com.android.iplayer.listener.OnPlayerEventListener
import com.android.iplayer.media.core.ExoPlayerFactory
import com.android.iplayer.model.PlayerState
import com.android.iplayer.utils.PlayerUtils
import com.android.iplayer.video.cache.VideoCache
import com.android.iplayer.widget.VideoPlayer
import com.android.iplayer.widget.controls.*
import com.android.iplayer.widget.view.MediaTextureView

class IPlayerActivity : AppCompatActivity {
    constructor():super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    companion object{
        fun startActivity(activity: Activity, url:String){
            val intent: Intent = Intent(activity, IPlayerActivity::class.java)
            intent.putExtra("videoUrl", url)
            activity.startActivity(intent)
        }
    }

    private var mVideoPlayer: VideoPlayer? = null
    private var videoUrl: String? = null

    //是否正在关闭Activity
    private var isFinish = false

    //是否转场播放
    private var mIsChange = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iplayer)
        initView()
    }

    fun initView(){
        mIsChange = intent.getBooleanExtra("is_change", false)
        val playerParent = findViewById<FrameLayout>(R.id.player_container_parent)
        playerParent.removeAllViews()
        PlayerManager.instance.videoPlayer = VideoPlayer(this)
        if (mIsChange) {
            //接收转场播放任务
            mVideoPlayer = PlayerManager.instance.videoPlayer!!
            setPlayerConfig()
            //重要！！！这里存在一种情况，假设用户在列表界面开始播放视频，点击列表后跳转到详情继续播放视频，这时候全屏功能是无效的。所以要更重置上下文为自己
            mVideoPlayer!!.parentContext = this
        } else {
            videoUrl = intent.getStringExtra("videoUrl")
            mVideoPlayer = VideoPlayer(this)
            setPlayerConfig()
            val playUrl = VideoCache.getInstance().getPlayUrl(videoUrl)
            if (TextUtils.isEmpty(playUrl)) {
                mVideoPlayer!!.setDataSource(videoUrl)
            } else {
                mVideoPlayer!!.setDataSource(playUrl)
            }
            mVideoPlayer!!.prepareAsync() //准备播放
        }

        playerParent.addView(mVideoPlayer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER))
        PlayerUtils.getInstance()
            .setOutlineProvider(mVideoPlayer, 0F)
    }

    /**
     * 设置播放器
     */
    private fun setPlayerConfig() {
        //使用SDK自带控制器+各UI交互组件
        val toolBarView = ControlToolBarView(this) //标题栏，返回按钮、视频标题、功能按钮、系统时间、电池电量等组件
        val controller = VideoController(mVideoPlayer!!.context) //创建一个默认控制器
        mVideoPlayer!!.controller = controller //将播放器绑定到控制器
        mVideoPlayer!!.setLandscapeWindowTranslucent(true) //全屏沉浸样式
        val functionBarView = ControlFunctionBarView(this) //底部时间、seek、静音、全屏功能栏

        functionBarView.showSoundMute(false, false) //启用静音功能交互\默认不静音
        mVideoPlayer!!.isSoundMute = false
        mVideoPlayer!!.setPlayCompletionRestoreDirection(false) //横屏播放完，禁止回到竖屏
        val gestureView = ControlGestureView(this) //手势控制屏幕亮度、系统音量、快进、快退UI交互
        controller.setGestureEnabled(false)
        val completionView = ControlCompletionView(this) //播放完成、重试
        val statusView = ControlStatusView(this) //移动网络播放提示、播放失败、试看完成
        val loadingView = ControlLoadingView(this) //加载中、开始播放
        controller.addControllerWidget(toolBarView,
            functionBarView,
            gestureView,
            completionView,
            statusView,
            loadingView)
        addListener()
    }

    private fun addListener() {
        if (null != mVideoPlayer) {
            /**
             * 重要！！！这里存在一种情况，假设用户在列表界面开始播放视频，点击列表后跳转到详情继续播放视频，这时候全屏功能是无效的。所以要更重置上下文为自己
             */
            mVideoPlayer!!.parentContext = this
            //无论是新创建的播放器还是转场过来的播放器,监听事件都必须在当前界面设置
            mVideoPlayer!!.setOnPlayerActionListener(object : OnPlayerEventListener() {
                /**
                 * 创建一个自定义的播放器,返回null,则内部自动创建一个默认的解码器
                 * @return
                 */
                override fun createMediaPlayer(): AbstractMediaPlayer {
                    return ExoPlayerFactory.create().createPlayer(this@IPlayerActivity)
                }

                override fun createRenderView(): IRenderView {
                    return MediaTextureView(this@IPlayerActivity)
                }

                override fun onPlayerState(state: PlayerState, message: String) {}
            })
        }
    }

    override fun onResume() {
        super.onResume()
        mVideoPlayer?.onResume()
    }

    override fun onPause() {
        super.onPause()
        //转场播放生效中,当界面不可见时不要调用生命周期，此时正在关闭activity并将播放器交给上一级界面
        mVideoPlayer?.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            onBackPressed();
            true
        } else super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (null != mVideoPlayer) {
            mVideoPlayer!!.parentContext = null
            if (mVideoPlayer!!.isBackPressed) {
                isFinish = true
                close(true)
            }
            return
        }
        isFinish = true
        close(true)
    }

    /**
     * 转场的播放返回时设置setResult,其它情况不用设置setResult
     *
     * @param isChange
     */
    private fun close(isChange: Boolean) {
        if (isChange) {
            val intent = Intent()
            setResult(101, intent)
        } else {
            mIsChange = false
            PlayerManager.instance.videoPlayer = null
        }
        finish()
    }


    override fun onDestroy() {
        super.onDestroy()
        if (!mIsChange) {
            //非转场播放时销毁播放器
            mVideoPlayer?.onDestroy()
        }
    }
}