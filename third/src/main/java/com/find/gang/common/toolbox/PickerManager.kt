package com.find.gang.common.toolbox

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * 图片 / 视频 / 任意文件 单选 & 多选 封装
 * 需要在 Activity 或 Fragment 的 onCreate 中调用 register()
 */
typealias MyLauncher = ActivityResultLauncher<String>
class PickerManager {

    // 回调接口
    interface Callback {
        fun onImagePicked(uri: Uri) {}
        fun onImagesPicked(uris: List<Uri>) {}
        fun onVideoPicked(uri: Uri) {}
        fun onVideosPicked(uris: List<Uri>) {}
        fun onFilePicked(uri: Uri) {}
        fun onFilesPicked(uris: List<Uri>) {}
    }

    private var callback: Callback? = null

    private lateinit var pickImageLauncher:       MyLauncher
    private lateinit var pickImagesLauncher:      MyLauncher
    private lateinit var pickVideoLauncher:       MyLauncher
    private lateinit var pickVideosLauncher:      MyLauncher
    private lateinit var pickFileLauncher:        MyLauncher
    private lateinit var pickFilesLauncher:       MyLauncher

    // ------ 注册到 Activity ------
    fun register(activity: ComponentActivity, callback: Callback) {
        this.callback = callback
        pickImageLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { this.callback?.onImagePicked(it) } }

        pickImagesLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris -> if (uris.isNotEmpty()) this.callback?.onImagesPicked(uris) }

        pickVideoLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { this.callback?.onVideoPicked(it) } }

        pickVideosLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris -> if (uris.isNotEmpty()) this.callback?.onVideosPicked(uris) }

        pickFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { this.callback?.onFilePicked(it) } }

        pickFilesLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris -> if (uris.isNotEmpty()) this.callback?.onFilesPicked(uris) }
    }

    // ------ 注册到 Fragment ------
    fun register(fragment: Fragment, callback: Callback) {
        this.callback = callback
        pickImageLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { this.callback?.onImagePicked(it) } }

        pickImagesLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris -> if (uris.isNotEmpty()) this.callback?.onImagesPicked(uris) }

        pickVideoLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { this.callback?.onVideoPicked(it) } }

        pickVideosLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris -> if (uris.isNotEmpty()) this.callback?.onVideosPicked(uris) }

        pickFileLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { this.callback?.onFilePicked(it) } }

        pickFilesLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris -> if (uris.isNotEmpty()) this.callback?.onFilesPicked(uris) }
    }

    // ------ 触发选择的方法 ------
    fun pickSingleImage()   { pickImageLauncher.launch("image/*") }
    fun pickMultipleImages(){ pickImagesLauncher.launch("image/*") }
    fun pickSingleVideo()   { pickVideoLauncher.launch("video/*") }
    fun pickMultipleVideos(){ pickVideosLauncher.launch("video/*") }
    fun pickSingleFile()    { pickFileLauncher.launch("*/*") }
    fun pickMultipleFiles() { pickFilesLauncher.launch("*/*") }

}