package com.find.gang.third.ui.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import kotlin.coroutines.resumeWithException

class Media3MergeUtil(private val context: Context) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun mergeMediaFiles(
        inputUris: List<Uri>,
        outputFile: File,
        durationUs: List<Long?>? = null,
    ): Flow<Float> {
        val progressChannel = Channel<Float>(Channel.CONFLATED)
        // 启动协程，其生命周期应被调用者管理，例如 viewModelScope 或 lifecycleScope
        val job = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                performMerge(inputUris, outputFile, durationUs)
                progressChannel.send(1f)
            } catch (e: Exception) {
                progressChannel.close(e)
            } finally {
                progressChannel.close()
            }
        }
        return progressChannel.receiveAsFlow()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun performMerge(
        uris: List<Uri>,
        outputFile: File,
        durationUs: List<Long?>?
    ) = withContext(Dispatchers.Main) {
        // 1. 创建一个新的 Transformer 实例，无需手动释放
        val transformer = Transformer.Builder(context)
            // .setRemoveAudio(false) // 该方法已废弃，不再需要
            .build()

        try {
            val editedItems = uris.mapIndexed { index, uri ->
                val builder = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                val duration = durationUs?.getOrNull(index)
                if (duration != null && duration > 0) {
                    builder.setDurationUs(duration)
                }
                builder.build()
            }

            val sequence = EditedMediaItemSequence(ImmutableList.copyOf(editedItems))
            val composition = Composition.Builder(ImmutableList.of(sequence)).build()

            // 2. 使用 suspendCancellableCoroutine 桥接回调与协程
            suspendCancellableCoroutine<Unit> { continuation ->
                var isCancelled = false

                // 3. 添加 Listener 以接收进度和完成事件
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (!isCancelled) {
                            continuation.resume(Unit) { cause, _, _ -> }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (!isCancelled) {
                            continuation.resumeWithException(exportException)
                        }
                    }

                }

                transformer.addListener(listener)
                transformer.start(composition, outputFile.absolutePath)

                // 4. 如果协程被取消，则取消 Transformer 任务，资源随之自动释放
                continuation.invokeOnCancellation {
                    isCancelled = true
                    transformer.cancel()
                }
            }
        } finally {

        }
    }
}