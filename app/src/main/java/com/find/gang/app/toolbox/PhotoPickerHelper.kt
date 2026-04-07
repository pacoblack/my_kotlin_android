package com.find.gang.app.toolbox

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/**
 * Photo Picker 封装类，提供图片/视频的单选/多选接口
 *
 * 使用前需要在 Activity/Fragment 中注册对应的 launcher，
 * 本类采用函数式回调方式简化使用。
 */
class PhotoPickerHelper(activity: ComponentActivity,
                        maxPicSelect: Int = 5,
                       maxVideoSelect: Int = 5
) {

    private var onImageSingleResult: ((Uri?) -> Unit)? = null
    private var onImageMultipleResult: ((List<Uri>) -> Unit)? = null
    private var onVideoSingleResult: ((Uri?) -> Unit)? = null
    private var onVideoMultipleResult: ((List<Uri>) -> Unit)? = null

    // 单选图片 Launcher
    private val singleImageLauncher: ActivityResultLauncher<PickVisualMediaRequest> = activity.registerForActivityResult(PickVisualMedia()) { uri ->
        onImageSingleResult?.invoke(uri)
        onImageSingleResult = null
    }

    // 多选图片 Launcher
    private val multipleImageLauncher: ActivityResultLauncher<PickVisualMediaRequest> = activity.registerForActivityResult(PickMultipleVisualMedia(maxPicSelect)) { uris ->
        onImageMultipleResult?.invoke(uris)
        onImageMultipleResult = null
    }

    // 单选视频 Launcher
    private val singleVideoLauncher: ActivityResultLauncher<PickVisualMediaRequest> = activity.registerForActivityResult(PickVisualMedia()) { uri ->
        onVideoSingleResult?.invoke(uri)
        onVideoSingleResult = null
    }

    // 多选视频 Launcher
    private val multipleVideoLauncher: ActivityResultLauncher<PickVisualMediaRequest> = activity.registerForActivityResult(PickMultipleVisualMedia(maxVideoSelect)) { uris ->
        onVideoMultipleResult?.invoke(uris)
        onVideoMultipleResult = null
    }

    /**
     * 单选图片
     * @param callback 回调，返回选中的 Uri，未选择则为 null
     */
    fun pickSingleImage(callback: (Uri?) -> Unit) {
        onImageSingleResult = callback
        singleImageLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    /**
     * 多选图片
     * @param callback 回调，返回选中的 Uri 列表（可能为空）
     */
    fun pickMultipleImages(callback: (List<Uri>) -> Unit) {
        onImageMultipleResult = callback
        multipleImageLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    /**
     * 单选视频
     * @param callback 回调，返回选中的 Uri，未选择则为 null
     */
    fun pickSingleVideo(callback: (Uri?) -> Unit) {
        onVideoSingleResult = callback
        singleVideoLauncher.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
    }

    /**
     * 多选视频
     * @param callback 回调，返回选中的 Uri 列表（可能为空）
     */
    fun pickMultipleVideos(callback: (List<Uri>) -> Unit) {
        onVideoMultipleResult = callback
        multipleVideoLauncher.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
    }
}