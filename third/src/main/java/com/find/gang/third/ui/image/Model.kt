package com.find.gang.third.ui.image

import android.content.Context
import android.net.Uri
import android.provider.MediaStore


enum class MediaType { IMAGE, VIDEO, GIF, UNKNOWN }
data class MediaItem(val uri: Uri, val name: String, val type: MediaType){
    fun isVideo(): Boolean {
        return type == MediaType.VIDEO
    }
    fun isGif(): Boolean {
        return type == MediaType.GIF
    }

    fun isImage(): Boolean {
        return type == MediaType.IMAGE || type == MediaType.GIF
    }

    companion object {
        /**
         * 根据 Uri 创建 MediaItem，自动解析文件名和类型
         * @param context 用于访问 ContentResolver
         * @param uri 内容提供者 URI (content://) 或 文件 URI (file://)
         * @return MediaItem，解析失败时返回 null
         */
        fun fromUri(context: Context, uri: Uri): MediaItem {
            var displayName = ""
            var mimeType: String? = null

            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeIndex = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    if (nameIndex >= 0) displayName = it.getString(nameIndex) ?: ""
                    if (mimeIndex >= 0) mimeType = it.getString(mimeIndex)
                }
            }

            if (displayName.isBlank()) {
                displayName = uri.lastPathSegment ?: "unknown"
            }

            // 使用安全调用，避免智能转换错误
            val mediaType = mimeType?.let { mimeTypeToMediaType(it) }
                ?: extensionToMediaType(displayName)

            return MediaItem(uri, displayName, mediaType)
        }

        // MIME 类型映射
        private fun mimeTypeToMediaType(mimeType: String): MediaType {
            return when {
                mimeType.startsWith("image/gif") -> MediaType.GIF
                mimeType.startsWith("image/") -> MediaType.IMAGE
                mimeType.startsWith("video/") -> MediaType.VIDEO
                else -> MediaType.UNKNOWN
            }
        }

        // 文件扩展名映射（当没有 MIME 时使用）
        private fun extensionToMediaType(fileName: String): MediaType {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "gif" -> MediaType.GIF
                "jpg", "jpeg", "png", "bmp", "webp" -> MediaType.IMAGE
                "mp4", "mkv", "webm", "3gp", "avi" -> MediaType.VIDEO
                else -> MediaType.UNKNOWN
            }
        }
    }
}