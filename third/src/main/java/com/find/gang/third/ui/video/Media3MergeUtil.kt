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

/**
 * 媒体合并与截取工具
 *
 * 使用示例 (在 Activity/Fragment 中)：
 * ```
 * val mergeUtil = MediaMergeUtil(applicationContext)
 * val job = lifecycleScope.launch {
 *     mergeUtil.merge(fragments, outputFile)
 *         .collect { progress -> updateProgressBar(progress) }
 * }
 * ```
 */
class MediaMergeUtil(private val context: Context) {

    /**
     * 片段参数
     *
     * @param uri          媒体 URI
     * @param startUs      裁剪开始时间（微秒），null 表示不裁剪开头
     * @param endUs        裁剪结束时间（微秒），null 表示不裁剪结尾
     *                     如果同时指定 startUs/endUs，最终片段时长 = endUs - startUs
     * @param durationUs   强制片段的输出时长（微秒），>0 时生效
     *                     如果片段原时长或裁剪后时长与 durationUs 不同，会进行变速或填充（默认黑帧）
     */
    data class Fragment(
        val uri: Uri,
        val startUs: Long? = null,
        val endUs: Long? = null,
        val durationUs: Long? = null,
    )

    /**
     * 执行合并与截取，返回进度流 [0, 1]
     *
     * @param fragments   要处理的片段列表（按顺序拼接）
     * @param outputFile  输出文件
     * @return Flow<Float> 进度，完成时发送 1.0 并关闭
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun merge(
        fragments: List<Fragment>,
        outputFile: File,
    ): Flow<Float> {
        val progressChannel = Channel<Float>(Channel.CONFLATED)
        // 启动协程，调用者应在合适的 scope 中收集（如 lifecycleScope）
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                performMerge(fragments, outputFile)
                progressChannel.send(1f)        // 确保最终为 1.0
            } catch (e: Exception) {
                progressChannel.close(e)
            } finally {
                progressChannel.close()
            }
        }
        return progressChannel.receiveAsFlow()
    }

    /**
     * 便捷方法：单独截取一个视频（不拼接其他片段）
     *
     * @param inputUri  输入视频 URI
     * @param startMs   开始时间（毫秒），可选
     * @param endMs     结束时间（毫秒），可选
     * @param outputFile 输出文件
     * @return Flow<Float> 进度
     */
    fun trim(
        inputUri: Uri,
        startMs: Long? = null,
        endMs: Long? = null,
        outputFile: File,
    ): Flow<Float> {
        val startUs = startMs?.let { it * 1000 }
        val endUs = endMs?.let { it * 1000 }
        val fragment = Fragment(uri = inputUri, startUs = startUs, endUs = endUs)
        return merge(listOf(fragment), outputFile)
    }

    // 实际执行合并的核心逻辑
    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun performMerge(
        fragments: List<Fragment>,
        outputFile: File
    ) = withContext(Dispatchers.Main) {
        // Transformer 必须在主线程创建和启动
        val transformer = Transformer.Builder(context).build()
        try {
            // 构建 EditedMediaItem 列表
            val editedItems = fragments.map { frag ->
                val builder = EditedMediaItem.Builder(buildMediaItem(frag))
                // 设置强制时长
                if (frag.durationUs != null && frag.durationUs > 0) {
                    builder.setDurationUs(frag.durationUs)
                }
                builder.build()
            }

            // 所有片段放入同一个 Sequence 中，实现顺序拼接
            val sequence = EditedMediaItemSequence(ImmutableList.copyOf(editedItems))
            val composition = Composition.Builder(ImmutableList.of(sequence)).build()

            // 桥接回调到协程
            suspendCancellableCoroutine<Unit> { cont ->
                var isCancelled = false

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (!isCancelled) {
                            cont.resume(Unit) { _, _, _ -> }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (!isCancelled) cont.resumeWithException(exportException)
                    }

                }

                transformer.addListener(listener)
                transformer.start(composition, outputFile.absolutePath)

                cont.invokeOnCancellation {
                    isCancelled = true
                    transformer.cancel()
                }
            }
        } finally {
            // 资源在任务完成或取消后自动释放，无需 transformer.release()
        }
    }

    // 根据 Fragment 构建带裁剪配置的 MediaItem
    @UnstableApi
    private fun buildMediaItem(fragment: Fragment): MediaItem {
        val uri = fragment.uri
        val startUs = fragment.startUs
        val endUs = fragment.endUs

        if (startUs == null && endUs == null) {
            return MediaItem.fromUri(uri)
        }

        val clipConfigBuilder = MediaItem.ClippingConfiguration.Builder()
        // 注意：setStartPositionMs 期望毫秒，而我们存储的是微秒，需要转换
        startUs?.let { clipConfigBuilder.setStartPositionUs(it) }
        endUs?.let { clipConfigBuilder.setEndPositionUs(it) }

        return MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipConfigBuilder.build())
            .build()
    }
}