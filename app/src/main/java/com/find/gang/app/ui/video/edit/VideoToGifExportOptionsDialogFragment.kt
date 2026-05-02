package com.find.gang.app.ui.video.edit

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.find.gang.app.R
import com.find.gang.app.databinding.DialogFragmentVideoToGifExportOptionsBinding
import com.find.gang.app.toolbox.FFmpegKitExtensions.executeFFmpeg
import com.find.gang.app.toolbox.FileTools.resetDirectory
import com.find.gang.app.toolbox.MediaTools.getVideoSingleFrame
import com.find.gang.app.toolbox.MediaTools.gifsicleLossy
import com.find.gang.app.toolbox.MediaTools.saveToPng
import com.find.gang.app.toolbox.Toolbox.backgroundColor
import com.find.gang.app.toolbox.Toolbox.colorIntToHex
import com.find.gang.app.toolbox.Toolbox.constraintBy
import com.find.gang.app.toolbox.Toolbox.joinToStringSpecial
import com.find.gang.app.toolbox.Toolbox.logRed
import com.find.gang.app.toolbox.Toolbox.onClick
import com.find.gang.app.toolbox.Toolbox.toast
import com.find.gang.app.toolbox.Toolbox.visibleIf
import com.find.gang.app.ui.video.edit.MyVideoConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN
import com.find.gang.app.ui.video.edit.MyVideoConstants.VIDEO_TO_GIF_PREVIEW_CACHE_DIR
import com.find.gang.app.ui.video.edit.task.TaskBuilderVideoToGif
import com.find.gang.app.ui.video.edit.task.TaskBuilderVideoToGifForPreview
import com.find.gang.app.ui.video.todo.EditVideoPerformActivity
import com.find.gang.app.ui.video.todo.VideoToGifPerformerActivity
import com.find.gang.app.widget.TextRender
import kotlin.math.min

class VideoToGifExportOptionsDialogFragment : DialogFragment() {
  private var _binding: DialogFragmentVideoToGifExportOptionsBinding? = null
  private val binding get() = _binding!!
  private val previewBitmapMap = mutableMapOf<TaskBuilderVideoToGifForPreview, Bitmap>()
  private val vtgActivity get() = activity as VideoToGifActivity
  private lateinit var frame: Bitmap

  /** Determining whether a Key exists in a Map/Set is fast, while determining whether a file exists is much slower */
  private val fileExistsCache = mutableSetOf<String>()

  @RequiresApi(Build.VERSION_CODES.O)
  @SuppressLint("ClickableViewAccessibility")
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View {
    _binding = DialogFragmentVideoToGifExportOptionsBinding.inflate(layoutInflater, container, false)
    clearPreviewImageCache()
    vtgActivity.videoView.pause()
    binding.mbSave.onClick {
      vtgActivity.videoView.pause()
      if (vtgActivity.convertType == 1){
        EditVideoPerformActivity.start(vtgActivity, createTaskBuilder())
      } else {
        VideoToGifPerformerActivity.start(vtgActivity, createTaskBuilder())
      }
    }
    binding.chipGroupMoreOptions.setOnCheckedStateChangeListener { _, checkedIds ->
      val chipEffectNeedsToBeViewedAfterExporting = listOf(
        binding.chipFramerate, binding.chipEnableReverse, binding.chipEnableFinalDelay
      )
      val checkedChips = chipEffectNeedsToBeViewedAfterExporting.filter { checkedIds.contains(it.id) }
      if (checkedChips.isEmpty()) {
        binding.mtvMoreOptionsTips.visibility = GONE
      } else {
        binding.mtvMoreOptionsTips.text = getString(
          R.string.effect_needs_to_be_viewed_after_exporting,
          checkedChips.map { it.text }.joinToStringSpecial(getString(R.string.language_item_separator_normal), getString(R.string.language_item_separator_last))
        )
        binding.mtvMoreOptionsTips.visibility = VISIBLE
      }
    }
    binding.acivSingleFramePreview.setOnTouchListener { _, event ->
      if (binding.chipEnableColorKey.isChecked && event.pointerCount == 1) {
        val eventXY = floatArrayOf(event.x, event.y)
        val invertMatrix = Matrix()
        binding.acivSingleFramePreview.imageMatrix.invert(invertMatrix)
        invertMatrix.mapPoints(eventXY)
        val bitmap = renderPreviewImage(createTaskBuilder().getForPreviewOnly().copy(colorKey = null))
        logRed("(v.drawable as BitmapDrawable).bitmap", "${bitmap.width}x${bitmap.height}")
        val x = eventXY[0].toInt().constraintBy(0 until bitmap.width)
        val y = eventXY[1].toInt().constraintBy(0 until bitmap.height)
        binding.viewColorKeyIndicator.backgroundColor = bitmap[x, y]
        binding.mcbColorKeyPreview.isChecked = true
        updatePreviewImage()
      }
      true
    }
    binding.mtvFramerateOver10Warning.visibleIf { createTaskBuilder().outputFps > 10 }
    binding.chipEnableColorKey.setOnCheckedChangeListener { _, isChecked ->
      binding.llcGroupColorKey.visibleIf { isChecked }
      updatePreviewImage()
    }
    binding.chipFramerate.setOnCheckedChangeListener { _, isChecked ->
      binding.llcGroupFramerate.visibleIf { isChecked }
    }
    binding.viewColorKeyIndicator.onClick { toast(R.string.click_on_the_preview_image_to_pick_an_color) }
    binding.sliderColorKeySimilarity.apply {
      setLabelFormatter { "${it.toInt()}%" }
      addOnChangeListener { _, _, _ ->
        performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        updatePreviewImage()
      }
    }
    binding.tietResolutionInputValue.doAfterTextChanged {
      updatePreviewImage()
    }
    binding.mbtgColorQuality.addOnButtonCheckedListener { group, _, isChecked ->
      if (isChecked) {
        updatePreviewImage()
        group.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
      }
    }

    binding.mbtgResolution.addOnButtonCheckedListener { group, checkedId, isChecked ->
      if (isChecked) {
        group.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
        binding.llcGroupResolutionInput.visibleIf { checkedId == binding.mbResolutionCustom.id }
        if (checkedId == binding.mbResolutionCustom.id) binding.tietResolutionInputValue.requestFocus()
        updatePreviewImage()
      }
    }
    binding.mbtgImageQuality.addOnButtonCheckedListener { group, _, isChecked ->
      if (isChecked) {
        updatePreviewImage()
        group.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
      }
    }
    binding.mbtgFramerate.addOnButtonCheckedListener { group, _, isChecked ->
      if (isChecked) {
        binding.mtvFramerateOver10Warning.visibleIf { createTaskBuilder().outputFps > 10 }
        group.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
      }
    }
    binding.mcbColorKeyPreview.setOnCheckedChangeListener { buttonView, _ ->
      buttonView.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
      updatePreviewImage()
    }
    binding.mbClose.onClick { dismiss() }
    frame = getVideoSingleFrame(
      vtgActivity.inputVideoPath, vtgActivity.videoView.currentPosition.toLong()
    )
    Canvas(frame).drawBitmap(
      TextRender.render(vtgActivity.textRender, frame.width, frame.height), 0f, 0f, null
    ) // Merge the text layer with the frame
    frame = vtgActivity.cropParams.crop(frame) // Crop
    binding.viewColorKeyIndicator.backgroundColor = vtgActivity.savedColorKeyColor ?: frame[0, 0]
    updatePreviewImage()
    return binding.root
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun createTaskBuilder() = with(vtgActivity) {
    TaskBuilderVideoToGif(
      trimTime = with(rangeSlider) {
        if ((values[0] * 100).toInt() == 0 && (values[1] * 100).toInt() == videoView.duration) null
        else ((values[0] * 100).toInt() to (values[1] * 100).toInt())
      },
      inputVideoPath = inputVideoPath,
      cropParams = cropParams,
      shortLength = getSelectedShortLength(),
      outputSpeed = playbackSpeed,
      outputFps = when (binding.mbtgFramerate.checkedButtonId) {
        binding.mbFramerate5.id -> 5
        binding.mbFramerate10.id -> 10
        binding.mbFramerate16.id -> 16
        binding.mbFramerate25.id -> 25
        binding.mbFramerate50.id -> 50
        else -> throw IllegalArgumentException()
      },
      colorQuality = when (binding.mbtgColorQuality.checkedButtonId) {
        binding.mbColorQualityLow.id -> 32
        binding.mbColorQualityMid.id -> 64
        binding.mbColorQualityHigh.id -> 128
        binding.mbColorQualityMax.id -> 256
        else -> throw IllegalArgumentException()
      },
      cycle = binding.chipEnableCycle.isChecked,
      reverse = binding.chipEnableReverse.isChecked,
      textRender = textRender,
      lossy = when (binding.mbtgImageQuality.checkedButtonId) {
        binding.mbImageQualityLow.id -> 200
        binding.mbImageQualityMid.id -> 70
        binding.mbImageQualityHigh.id -> 30
        binding.mbImageQualityMax.id -> null
        else -> throw IllegalArgumentException()
      },
      videoWH = videoWH,
      duration = videoView.duration,
      finalDelay = if (binding.chipEnableFinalDelay.isChecked) 50 else -1,
      colorKey = with(binding) {
        if (chipEnableColorKey.isChecked)
          (viewColorKeyIndicator.backgroundColor.colorIntToHex() to sliderColorKeySimilarity.value.toInt())
        else null
      })
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun renderPreviewImage(taskBuilder: TaskBuilderVideoToGifForPreview) = with(taskBuilder) {
    if (!fileExistsCache.contains(getCache_shortLength_colorKey_paletteuse())) {
      if (!fileExistsCache.contains(getCache_shortLength_colorKey_palettegen())) {
        if (!fileExistsCache.contains(getCache_shortLength_colorKey())) {
          if (!fileExistsCache.contains(getCache_shortLength())) {
            frame.scale(gifOutputWH(shortLength).first, gifOutputWH(shortLength).second).saveToPng(getCache_shortLength())
            fileExistsCache.add(getCache_shortLength())
          }
          colorKey?.let {
            val command =
              "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${getCache_shortLength()}\" -vf colorkey=#${it.first}:${it.second / 100f}:0 -y \"${getCache_shortLength_colorKey()}\""
            logRed("colorKey cmd", command)
            command.executeFFmpeg()
          }
          fileExistsCache.add(getCache_shortLength_colorKey())
        }
        "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${getCache_shortLength_colorKey()}\" -filter_complex palettegen=max_colors=$colorQuality:stats_mode=diff -y \"${getCache_shortLength_colorKey_palettegen()}\"".executeFFmpeg()
        fileExistsCache.add(getCache_shortLength_colorKey_palettegen())
      }
      "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${getCache_shortLength_colorKey()}\" -i ${getCache_shortLength_colorKey_palettegen()} -filter_complex \"[0:v][1:v] paletteuse=dither=bayer\" -y \"${getCache_shortLength_colorKey_paletteuse()}\"".executeFFmpeg()
      fileExistsCache.add(getCache_shortLength_colorKey_paletteuse())
      previewBitmapMap[this.copy(lossy = null)] = BitmapFactory.decodeFile(getCache_shortLength_colorKey_paletteuse())
    }
    if (!previewBitmapMap.containsKey(this)) {
      gifsicleLossy(
        lossy!!, getCache_shortLength_colorKey_paletteuse(), getCache_shortLength_colorKey_paletteuse_lossy(), false
      )
      fileExistsCache.add(getCache_shortLength_colorKey_paletteuse_lossy())
      previewBitmapMap[this] = BitmapFactory.decodeFile(getCache_shortLength_colorKey_paletteuse_lossy())
    }
    previewBitmapMap[this]!!
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun updatePreviewImage() {
    val taskBuilder = createTaskBuilder().getForPreviewOnly()
    binding.acivSingleFramePreview.setImageBitmap(
      renderPreviewImage(
        if (binding.chipEnableColorKey.isChecked && binding.mcbColorKeyPreview.isChecked) taskBuilder
        else taskBuilder.copy(colorKey = null)
      )
    )
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun gifOutputWH(shortLength: Int) = vtgActivity.cropParams.calcScaledResolution(shortLength)

  @RequiresApi(Build.VERSION_CODES.O)
  private fun getSelectedShortLength() =
    when (binding.mbtgResolution.checkedButtonId) {
      binding.mbResolution144p.id -> 144
      binding.mbResolution240p.id -> 240
      binding.mbResolution320p.id -> 320
      binding.mbResolutionCustom.id -> {
        val inputValue = ("0" + binding.tietResolutionInputValue.text.toString()).toInt()
        if (inputValue == 0) 240 else if (inputValue % 2 == 0) inputValue else inputValue + 1
      }

      else -> throw IllegalArgumentException()
    }.constraintBy(2..min(vtgActivity.cropParams.outW, vtgActivity.cropParams.outH))

  @RequiresApi(Build.VERSION_CODES.O)
  override fun onDestroyView() {
    vtgActivity.savedColorKeyColor = binding.viewColorKeyIndicator.backgroundColor
    super.onDestroyView()
    _binding = null
    previewBitmapMap.clear()
    resetDirectory(VIDEO_TO_GIF_PREVIEW_CACHE_DIR)
    vtgActivity.videoView.start()
  }

  private fun clearPreviewImageCache() {
    previewBitmapMap.clear()
    fileExistsCache.clear()
    resetDirectory(VIDEO_TO_GIF_PREVIEW_CACHE_DIR)
  }

  companion object {
    const val TAG = "VideoToGifExportOptionsDialogFragment"
  }
}