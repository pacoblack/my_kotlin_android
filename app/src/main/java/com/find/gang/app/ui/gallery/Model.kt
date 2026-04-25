package com.find.gang.app.ui.gallery

import android.net.Uri

data class DirectoryItem(val name: String, val uri: Uri)

enum class MediaType { IMAGE, VIDEO }

data class MediaItem(val uri: Uri, val name: String, val type: MediaType)

enum class FilterMode { ALL, IMAGE, VIDEO }