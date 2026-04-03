package com.find.gang.myfindapplication

import android.app.Application
import com.alibaba.android.arouter.BuildConfig
import com.alibaba.android.arouter.launcher.ARouter

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ARouter初始化
        if (BuildConfig.DEBUG) {
            ARouter.openLog()
            ARouter.openDebug()
        }
        ARouter.init(this)
    }
}