package com.find.gang.third.ui.video

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File

object HlsMergeManager {
    fun startMerge(context: Context, m3u8Url: String?, outputFile: File, format: String?) {
        // 确保目录存在
        val parentDir = outputFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }


        // 创建工作数据
        val inputData = Data.Builder()
            .putString(HlsMergeWorker.KEY_M3U8_URL, m3u8Url)
            .putString(HlsMergeWorker.KEY_OUTPUT_FILE, outputFile.absolutePath)
            .putString(HlsMergeWorker.KEY_OUTPUT_FORMAT, format)
            .build()


        // 创建工作约束
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()


        // 创建工作请求
        val mergeWork = OneTimeWorkRequest.Builder(HlsMergeWorker::class.java)
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()


        // 提交工作
        WorkManager.getInstance(context).enqueue(mergeWork)


        // 监听工作进度
        WorkManager.getInstance(context)
            .getWorkInfoByIdLiveData(mergeWork.id)
            .observeForever(Observer { workInfo:WorkInfo? ->
                if (workInfo != null) {
                    val progressData = workInfo.progress
                    val progress = progressData.getInt("progress", 0)

                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {}
                        WorkInfo.State.FAILED -> {}
                        WorkInfo.State.ENQUEUED -> {}
                        WorkInfo.State.RUNNING -> {}
                        WorkInfo.State.BLOCKED -> {}
                        WorkInfo.State.CANCELLED -> {}
                    }
                }
            })
    }
}
