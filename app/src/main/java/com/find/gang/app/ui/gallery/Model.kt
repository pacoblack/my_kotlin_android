package com.find.gang.app.ui.gallery

import android.net.Uri

data class DirectoryItem(val name: String, val uri: Uri)

enum class FilterMode { ALL, IMAGE, VIDEO }