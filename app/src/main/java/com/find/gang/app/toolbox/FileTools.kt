package com.find.gang.app.toolbox

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Environment.DIRECTORY_PICTURES
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat.getExternalFilesDirs
import com.find.gang.app.MyApplication
import com.find.gang.app.R
import com.find.gang.app.ui.video.edit.MyVideoConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.find.gang.app.toolbox.Toolbox.keepNDecimalPlaces
import com.find.gang.app.toolbox.Toolbox.logRed
import com.find.gang.app.toolbox.Toolbox.toEmptyStringIf
import com.find.gang.app.toolbox.Toolbox.toast
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.nextUp


object FileTools {

  private const val BUFFER_SIZE = 131072

  enum class FileSizeUnit(val unitName: String, val multiple: Double) {
    B("B", 1.0), KB("KB", 1024.0), MB("MB", 1048576.0), GB("GB", 1073741824.0);
  }

  class FileName(private val filePathOrName: String) {
    constructor(uri: Uri) : this(when (uri.scheme) {
      "content" -> {
        MyApplication.appContext.contentResolver.query(uri, null, null, null, null)!!.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          cursor.moveToFirst()
          cursor.getString(nameIndex)
        }
      }

      "file" -> uri.lastPathSegment
      else -> throw IllegalArgumentException("uri.scheme = $uri.scheme")
    }!!)

    /**
     * "/sdcard/video.mp4" -> "video.mp4"
     * "video.mp4" -> "video.mp4"
     **/
    val name get() = filePathOrName.substringAfterLast('/')

    /** "/sdcard/video.mp4" -> "mp4" */
    val extension get() = name.substringAfterLast('.')

    /** "/sdcard/video.mp4" -> "video" */
    val nameWithoutExtension get() = name.substringBeforeLast('.')

  }

  fun createNewFile(fileNamePrefix: String?, fileType: String): Uri {
    val appName = Toolbox.appGetString(R.string.app_name)
    val fileName =
      ("${fileNamePrefix}_").toEmptyStringIf { fileNamePrefix.isNullOrBlank() } + "${appName}_${Toolbox.getTimeYMDHMS()}.$fileType"
    return when (fileType) {
      "mp4" -> MyApplication.appContext.contentResolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/${appName}")
          put(
            MediaStore.Video.Media.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileType)
          )
        })

      "gif", "png" -> MyApplication.appContext.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        ContentValues().apply {
          put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${appName}")
          put(
            MediaStore.Images.Media.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileType)
          )
        })

      else -> throw NotImplementedError("fileType = $fileType")
    }!!
  }

  fun copyFile(srcPath: String, destUri: Uri, deleteSrc: Boolean = false) {
    val srcFile = File(srcPath)
    MyApplication.appContext.contentResolver.openOutputStream(destUri)!!.use { outputStream ->
      FileInputStream(srcFile).use { inputStream ->
        copyStream(inputStream, outputStream)
      }
    }
    if (deleteSrc) srcFile.delete()
  }

  fun copyFile(srcUri: Uri, destPath: String) {
    FileOutputStream(File(destPath)).use { outputStream ->
      MyApplication.appContext.contentResolver.openInputStream(srcUri)!!.use { inputStream ->
        copyStream(inputStream, outputStream)
      }
    }
  }

  fun copyStream(
    inputStream: InputStream, outputStream: OutputStream, onCopy: ((bytesCopied: Long) -> Unit)? = null
  ) {
    var bytesCopied: Long = 0
    val buffer = ByteArray(BUFFER_SIZE)
    var bytes = inputStream.read(buffer)
    while (bytes >= 0) {
      outputStream.write(buffer, 0, bytes)
      bytesCopied += bytes
      if (onCopy != null) onCopy(bytesCopied)
      bytes = inputStream.read(buffer)
    }
  }

  fun Uri.copyToInputFileDir(reset:Boolean = true,fileName:String=""): String {
    if (reset) {
      resetDirectory(MyVideoConstants.INPUT_FILE_DIR)
    }
    val inputFilePath = MyVideoConstants.INPUT_FILE_DIR + fileName.ifEmpty { FileName(this).name }
    MyApplication.appContext.contentResolver.openInputStream(this)!!.use { inputStream ->
      FileOutputStream(inputFilePath).use { outputStream ->
        var toastedPleaseWait = false
        val startTime = System.nanoTime()
        copyStream(inputStream, outputStream) { totalBytesCopied ->
          if (System.nanoTime() - startTime > 1000000000L && !toastedPleaseWait) {
            toast(
              Toolbox.appGetString(
                R.string.loading_file_d_seconds_please_wait,
                ((System.nanoTime() - startTime) * fileSize() / 1000000000f / totalBytesCopied).nextUp().toInt()
              )
            )
            toastedPleaseWait = true
          }
        }
      }
    }
    return inputFilePath
  }

  fun resetDirectory(vararg dirs: String) {
    for (dir in dirs) {
      File(dir).apply {
        if (exists()) deleteRecursively()
        mkdirs()
      }
    }
  }

  fun Uri.deleteFile() {
    try {
      when (this.scheme) {
        "content" -> MyApplication.appContext.contentResolver.delete(this, null, null)
        "file" -> MyApplication.appContext.contentResolver.delete(
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.DATA + "=?", arrayOf(this.path)
        )

        else -> throw IllegalArgumentException("gifUri.scheme = ${this.scheme}")
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun Uri.fileSize() = when (scheme) {
    "content" -> MyApplication.appContext.contentResolver.openAssetFileDescriptor(this, "r")!!.use { it.length }

    "file" -> File(path!!).length()
    else -> throw IllegalArgumentException("uri.scheme = $scheme")
  }

  fun Long.formattedFileSize(
    fileSizeUnit: FileSizeUnit = FileSizeUnit.KB,
    decimalPlaces: Int = 0,
    appendUnit: Boolean = true,
  ) = (this / fileSizeUnit.multiple).keepNDecimalPlaces(decimalPlaces) + if (appendUnit) {
    fileSizeUnit.unitName
  } else {
    ""
  }

  fun Uri.mimeType() = when (scheme) {
    "content" -> MyApplication.appContext.contentResolver.getType(this)
    "file" -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileName(this).extension)
    else -> null
  }

  fun getExternalDir(context: Context, dir:String): File {
    var externalDirs = getExternalFilesDirs(context, DIRECTORY_PICTURES);
    var sdCardDir:File? = null;

    externalDirs.forEach {
      if (isExternalSdCard(it)) {
        sdCardDir = it;
      }
    }

    if (sdCardDir == null) {
      sdCardDir = Environment.getExternalStorageDirectory();
    }

    val splitDir = File(sdCardDir, dir)
    if (!splitDir.exists()) splitDir.mkdirs()

    return splitDir
  }

  private fun isExternalSdCard(file: File): Boolean {
    val path = file.absolutePath
    // 常见SD卡路径标识
    val patterns = arrayOf(
      "extSdCard", "sdcard1", "external_sd",
      "ext_sd", "external", "microSd"
    )
    return patterns.any { path.contains(it, ignoreCase = true) }
  }

  suspend fun copyToExternalDir(
    sourceFile: String,
    targetDir: File,
    targetName: String
  ): Boolean = withContext(Dispatchers.IO) {
    if (File(sourceFile).isDirectory) {
      return@withContext false
    }
    val targetFile = File(targetDir, targetName)

    try {
      FileInputStream(sourceFile).use { fis ->
        BufferedInputStream(fis).use { bis ->
          FileOutputStream(targetFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
              bis.copyTo(bos, bufferSize = 8192)
              true // 拷贝成功
            }
          }
        }
      }
    } catch (e: IOException) {
      logRed("FileCopy", "Copy failed: ${e.message}")
      false
    }
  }

}