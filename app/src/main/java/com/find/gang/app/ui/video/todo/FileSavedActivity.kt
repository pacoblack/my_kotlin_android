package com.find.gang.app.ui.video.todo

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.find.gang.app.R
import com.find.gang.app.databinding.ActivityFileSavedBinding
import com.find.gang.app.toolbox.FileTools
import com.find.gang.app.toolbox.FileTools.deleteFile
import com.find.gang.app.toolbox.FileTools.fileSize
import com.find.gang.app.toolbox.FileTools.formattedFileSize
import com.find.gang.app.toolbox.FileTools.mimeType
import com.find.gang.app.toolbox.Toolbox.getExtra
import com.find.gang.app.toolbox.Toolbox.onClick
import com.find.gang.app.toolbox.Toolbox.toast
import com.find.gang.app.ui.video.edit.MyVideoConstants.EXTRA_SAVED_FILE_URI
import com.find.gang.app.ui.video.edit.MyVideoConstants.MIME_TYPE_IMAGE_GIF
import com.find.gang.app.ui.video.edit.MyVideoConstants.MIME_TYPE_VIDEO_MP4
import com.find.gang.common.BaseActivity
import java.util.Locale
import kotlin.getValue
import androidx.core.view.isVisible

class FileSavedActivity : BaseActivity<ActivityFileSavedBinding>(R.layout.activity_file_saved) {
    private val fileUri by lazy { intent.getExtra<Uri>(EXTRA_SAVED_FILE_URI) }

    override fun initView(savedInstanceState: Bundle?) {
        setFinishOnTouchOutside(false)
        binding.mtvXxxSavedToGallery.text = getString(R.string._ext__saved_to_gallery, FileTools.FileName(fileUri).extension.uppercase(Locale.ROOT))
        when (fileUri.mimeType()) {
            MIME_TYPE_IMAGE_GIF -> {
                binding.acivPreview.visibility = VISIBLE
                binding.vvPreview.visibility = GONE
                Glide.with(this).load(fileUri).fitCenter().diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true)
                    .transition(DrawableTransitionOptions.withCrossFade()).into(binding.acivPreview)
            }

            MIME_TYPE_VIDEO_MP4 -> {
                binding.acivPreview.visibility = GONE
                binding.vvPreview.apply {
                    visibility = VISIBLE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
                    }
                    setOnPreparedListener {
                        it.apply {
                            setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                            isLooping = true
                        }
                    }
                    setVideoURI(fileUri)
                    start()
                }
            }

            else -> {
                throw NotImplementedError()
            }
        }
        binding.mtvFileSize.text = getString(R.string.file_size_s, fileUri.fileSize().formattedFileSize())
        binding.mbDone.onClick { finish() }
        binding.mbBack.onClick { finish() }
        binding.mbDelete.onClick {
            fileUri.deleteFile()
            toast(R.string.file_deleted)
            finish()
        }
        binding.mbShare.onClick {
            startActivity(
                Intent.createChooser(
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        type = fileUri.mimeType()
                    }, null
                )
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (binding.vvPreview.isVisible) binding.vvPreview.pause()
    }

    override fun onResume() {
        super.onResume()
        if (binding.vvPreview.isVisible) binding.vvPreview.start()
    }

    companion object {
        fun start(context: Context, fileUri: Uri) {
            context.startActivity(
                Intent(context, FileSavedActivity::class.java).putExtra(EXTRA_SAVED_FILE_URI, fileUri)
            )
        }
    }
}