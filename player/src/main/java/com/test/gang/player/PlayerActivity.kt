package com.test.gang.player

import androidx.appcompat.app.AppCompatActivity

class PlayerActivity(contentLayoutId: Int) : AppCompatActivity(contentLayoutId) {
    var frameRateSwitchThread: Thread? = null
    var chaptersThread: Thread? = null

    var chapterStarts: LongArray? = null
}