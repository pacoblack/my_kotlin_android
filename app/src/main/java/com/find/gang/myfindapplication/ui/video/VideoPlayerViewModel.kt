package com.find.gang.myfindapplication.ui.video

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.find.gang.myfindapplication.base.BaseViewModel

/**
 * 视频播放页面的 ViewModel
 * 管理视频源、播放状态和进度等信息
 */
class VideoPlayerViewModel : BaseViewModel() {

    // 当前播放的视频地址（网络或本地文件路径）
    private val _videoUrl = MutableLiveData<String>()
    val videoUrl: LiveData<String> = _videoUrl

    // 是否为本地文件
    private val _isLocal = MutableLiveData<Boolean>()
    val isLocal: LiveData<Boolean> = _isLocal

    // 是否正在播放
    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    // 当前播放进度（毫秒）
    private val _currentPosition = MutableLiveData<Long>()
    val currentPosition: LiveData<Long> = _currentPosition

    // 视频总时长（毫秒）
    private val _duration = MutableLiveData<Long>()
    val duration: LiveData<Long> = _duration

    // 播放器状态（对应 ExoPlayer 的 STATE_* 常量）
    private val _playbackState = MutableLiveData<Int>()
    val playbackState: LiveData<Int> = _playbackState

    // 缓冲百分比（0-100）
    private val _bufferedPercentage = MutableLiveData<Int>()
    val bufferedPercentage: LiveData<Int> = _bufferedPercentage

    // 错误信息
    private val _error = MutableLiveData<String?>()
    override val error: LiveData<String?> = _error

    init {
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _playbackState.value = 0  // STATE_IDLE
        _bufferedPercentage.value = 0
    }

    /**
     * 设置视频源
     */
    fun setVideoSource(url: String, isLocal: Boolean = false) {
        _videoUrl.value = url
        _isLocal.value = isLocal
        clearError()
    }

    /**
     * 更新播放状态（由 Fragment 中的播放器监听器调用）
     */
    fun updatePlaybackState(state: Int) {
        _playbackState.value = state
        // 根据状态更新 isPlaying
        _isPlaying.value = (state == 3)  // 3 = STATE_READY 且实际播放中，但这里简化
    }

    /**
     * 更新播放进度
     */
    fun updateCurrentPosition(position: Long) {
        _currentPosition.value = position
    }

    /**
     * 更新视频总时长
     */
    fun updateDuration(duration: Long) {
        _duration.value = duration
    }

    /**
     * 更新缓冲百分比
     */
    fun updateBufferedPercentage(percentage: Int) {
        _bufferedPercentage.value = percentage
    }

    /**
     * 播放/暂停
     * 实际控制由 Fragment 执行，ViewModel 仅记录状态或发送命令
     */
    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    /**
     * 记录错误信息
     */
    fun postError(message: String?) {
        _error.value = message
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * 重置所有状态（用于切换视频前）
     */
    fun reset() {
        _currentPosition.value = 0L
        _duration.value = 0L
        _playbackState.value = 0
        _bufferedPercentage.value = 0
        _isPlaying.value = false
        clearError()
    }
}