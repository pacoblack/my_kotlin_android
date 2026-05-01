package com.find.gang.app.toolbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.annotation.IntRange
import androidx.core.net.toUri
import com.find.gang.app.MyApplication
import com.find.gang.app.toolbox.FFmpegKitExtensions.getVideoSingleFrameWithFFmpeg
import com.find.gang.app.toolbox.Toolbox.logRed
import com.find.gang.app.toolbox.Toolbox.toEmptyStringIf
import com.find.gang.app.ui.video.edit.MyVideoConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset


/**
 * 当能够访问媒体文件的绝对路径时，请勿将其通转换为 Uri 传入给 FFmpegKit，
 * 因为这样传入时，文件将不带有文件后辍名，可能导致 FFmpeg 无法正确读取媒体文件！
 */
object MediaTools {

  /**
   * a function to generate a transparent [Bitmap] with the given width and height.
   * @param w The width of the [Bitmap].
   * @param h The height of the [Bitmap].
   * @return The generated transparent [Bitmap].
   */
  fun generateTransparentBitmap(w: Int, h: Int) =
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }

  fun getVideoDurationByAndroidSystem(context: Context, path: String) = with(MediaMetadataRetriever()) {
    try {
      setDataSource(context, path.toUri())
      val duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toInt()
      if (duration == 0) null else duration
    } catch (e: Exception) {
      null
    } finally {
      release()
    }
  }

  fun getVideoSingleFrame(path: String, timestamp_ms: Long) = with(MediaMetadataRetriever()) {
    try {
      setDataSource(path)
      getFrameAtTime(
        timestamp_ms * 1000L,
        MediaMetadataRetriever.OPTION_CLOSEST_SYNC // OPTION_CLOSEST is slower and may cause NullPointerException, avoid using it.
      )!!
    } catch (e: Exception) {
      /**
       * Even if it is set to OPTION_CLOSEST_SYNC, the getFrameAtTime() method still has the probability of NullPointerException.
       * Therefore, use FFmpeg as a fallback method.
       **/
      /**
       * Even if it is set to OPTION_CLOSEST_SYNC, the getFrameAtTime() method still has the probability of NullPointerException.
       * Therefore, use FFmpeg as a fallback method.
       **/
      getVideoSingleFrameWithFFmpeg(
        path, timestamp_ms, 5, MyVideoConstants.GET_VIDEO_SINGLE_FRAME_WITH_FFMPEG_TEMP_PATH
      )
      BitmapFactory.decodeFile(MyVideoConstants.GET_VIDEO_SINGLE_FRAME_WITH_FFMPEG_TEMP_PATH)
        .copy(Bitmap.Config.ARGB_8888, true)!!
    } finally {
      release()
    }
  }

  fun Bitmap.saveToPng(path: String) = FileOutputStream(path).use { compress(Bitmap.CompressFormat.PNG, 100, it) }

  fun Bitmap.saveToJpg(path: String, @IntRange(0, 100) quality: Int) =
    FileOutputStream(path).use { compress(Bitmap.CompressFormat.JPEG, quality, it) }

  fun getRotationFromProperties(properties: JSONObject) = try {
    var rotation = 0
    val sideDataListJSONArray = (properties.get("side_data_list") as JSONArray)
    (0 until sideDataListJSONArray.length()).forEach {
      try {
        rotation = -sideDataListJSONArray.getJSONObject(it).getInt("rotation")
      } catch (_: Exception) {
      }
    }
    if (rotation % 90 != 0) {
      logRed("rotation = $rotation", "rotation % 90 != 0")
      rotation = 0
    }
    while (rotation < 0) {
      rotation += 360
    }
    while (rotation >= 360) {
      rotation -= 360
    }
    rotation
  } catch (_: Exception) {
    0
  }

  fun getImageWidthHeight(path: String) = with(BitmapFactory.Options()) {
    this.inJustDecodeBounds = true
    BitmapFactory.decodeFile(path, this)
    Pair(this.outWidth, this.outHeight)
  }

  /**
   * lossy should >= 0 .
   * return true when succeed, false when failed.
   * if outputGifPath is null, then output will overwrite input file.
   */
  fun gifsicleLossy(
    lossy: Int,
    inputGifPath: String,
    outputGifPath: String?,
    enableO3: Boolean,
  ): Boolean {
    val nativeLibraryDir = MyApplication.appContext.applicationInfo.nativeLibraryDir
    val gifsiclePath = "${nativeLibraryDir}/libgifsicle.so"
    val gifsicleEnvp = arrayOf("LD_LIBRARY_PATH=${nativeLibraryDir}")
    val gifsicleCmd =
      if (outputGifPath == null)
        "$gifsiclePath -b ${"-O3".toEmptyStringIf { !enableO3 }} --lossy=$lossy $inputGifPath"
      else
        "$gifsiclePath ${"-O3".toEmptyStringIf { !enableO3 }} --lossy=$lossy --output $outputGifPath $inputGifPath"
    return try {
      (Runtime.getRuntime().exec(gifsicleCmd, gifsicleEnvp).waitFor() == 0)
    } catch (e: Exception) {
      logRed("gifsicleLossy() failed", e.message)
      false
    }
  }

  fun extractVideoFromMvimg(mvimg: String, video: String): Boolean {
    try {
      val byteArray = File(mvimg).readBytes()
      val index = byteArray.toString(Charset.forName("ISO-8859-1")).indexOf("ftypmp42") - 4
      if (index == -5) return false
      FileOutputStream(video).use {
        it.write(byteArray, index, byteArray.size - index)
      }
      return true
    } catch (e: Exception) {
      e.printStackTrace()
      return false
    }
  }

}