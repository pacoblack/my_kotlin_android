package com.find.gang.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.AttributeSet
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.find.gang.app.databinding.ItemTimelineThumbnailBinding
import com.find.gang.app.databinding.ViewTimelineVideoBinding
import kotlinx.coroutines.*
import java.io.File
import androidx.core.graphics.scale
import androidx.core.net.toUri

/**
 * 视频缩略图时间轴组件
 *
 * 功能：
 * - 根据视频时长和指定间隔自动生成预览图时间轴
 * - 支持点击缩略图跳转回调
 * - 支持外部同步当前播放进度（高亮对应缩略图）
 * - 内置内存+磁盘二级缓存
 *
 * 使用示例：
 * ```
 * val timeline = findViewById<VideoTimelineView>(R.id.timeline)
 * timeline.setVideoPath(videoPath, frameIntervalMs = 10000L)
 * timeline.setOnThumbnailClickListener { timeMs, position ->
 *     player.seekTo(timeMs)
 * }
 * // 播放时同步进度
 * timeline.setCurrentPosition(player.currentPosition)
 * ```
 */
class VideoTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private lateinit var binding: ViewTimelineVideoBinding

    // UI 组件
    private lateinit var recyclerView: RecyclerView
    private var adapter: TimelineAdapter? = null

    // 视频信息
    private var videoPath: String = ""
    private var totalDurationMs: Long = 0L
    private var frameIntervalMs: Long = 10000L  // 默认10秒

    // 缩略图尺寸（在第一次布局时计算）
    private var thumbnailWidth = 0
    private var thumbnailHeight = 0

    // 回调
    private var onThumbnailClickListener: ((timeMs: Long, position: Int) -> Unit)? = null

    // 协程作用域（用于缩略图异步加载）
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        initView()
    }

    private fun initView() {
        // 加载布局（需要先创建布局文件 timeline_view.xml）
        binding = ViewTimelineVideoBinding.inflate(LayoutInflater.from(context), this)
        binding.timelineRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView = binding.timelineRecyclerView
    }

    /**
     * 设置视频路径并开始构建时间轴
     * @param path 视频文件绝对路径
     * @param intervalMs 截图间隔（毫秒），默认10秒
     * @param onReady 时间轴准备就绪回调（可选）
     */
    fun setVideoPath(path: String, intervalMs: Long = 10000L, onReady: (() -> Unit)? = null) {
        this.videoPath = path
        this.frameIntervalMs = intervalMs

        // 异步获取视频时长（避免阻塞 UI）
        scope.launch(Dispatchers.IO) {
            val duration = getVideoDuration(path)
            withContext(Dispatchers.Main) {
                if (duration > 0) {
                    totalDurationMs = duration
                    setupAdapter()
                    onReady?.invoke()
                } else {
                    // 视频无效或无法获取时长
                }
            }
        }
    }

    /**
     * 获取视频时长（毫秒）
     */
    private fun getVideoDuration(path: String): Long {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, path.toUri())
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever?.release()
        }
    }

    /**
     * 初始化适配器
     */
    private fun setupAdapter() {
        // 计算缩略图目标尺寸（如果尚未计算）
        if (thumbnailWidth == 0 || thumbnailHeight == 0) {
            post {
                thumbnailWidth = recyclerView.height * 16 / 9  // 假设宽高比16:9，可调整
                thumbnailHeight = recyclerView.height
                adapter = TimelineAdapter(videoPath, totalDurationMs, frameIntervalMs, thumbnailWidth, thumbnailHeight)
                adapter?.onThumbnailClickListener = onThumbnailClickListener
                recyclerView.adapter = adapter
            }
        } else {
            adapter = TimelineAdapter(videoPath, totalDurationMs, frameIntervalMs, thumbnailWidth, thumbnailHeight)
            adapter?.onThumbnailClickListener = onThumbnailClickListener
            recyclerView.adapter = adapter
        }
    }

    /**
     * 设置缩略图点击回调
     */
    fun setOnThumbnailClickListener(listener: (timeMs: Long, position: Int) -> Unit) {
        this.onThumbnailClickListener = listener
        adapter?.onThumbnailClickListener = listener
    }

    /**
     * 更新当前播放进度（用于高亮对应缩略图）
     */
    fun setCurrentPosition(positionMs: Int) {
        adapter?.updateCurrentProgress(positionMs)
    }

    /**
     * 滚动到当前播放位置对应的缩略图
     * @param smooth 是否平滑滚动
     */
    fun scrollToCurrentPosition(smooth: Boolean = true) {
        val currentPos = adapter?.getCurrentSelectedPosition() ?: return
        if (smooth) {
            recyclerView.smoothScrollToPosition(currentPos)
        } else {
            recyclerView.scrollToPosition(currentPos)
        }
    }

    /**
     * 清理缓存（在 Activity onDestroy 时可选调用）
     */
    fun clearCache() {
        ThumbnailLoader.clearCache()
    }

    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        adapter = null
    }

    // ==================== 内部适配器 ====================

    private class TimelineAdapter(
        private val videoPath: String,
        private val totalDurationMs: Long,
        private val frameIntervalMs: Long,
        private val targetWidth: Int,
        private val targetHeight: Int,
    ) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

        private val frameTimes: List<Long> by lazy { generateFrameTimes() }
        private var selectedPosition: Int = 0
        var onThumbnailClickListener: ((Long, Int) -> Unit)? = null

        lateinit var binding : ItemTimelineThumbnailBinding

        private fun generateFrameTimes(): List<Long> {
            val times = mutableListOf<Long>()
            var currentTime = 0L
            while (currentTime < totalDurationMs) {
                times.add(currentTime)
                currentTime += frameIntervalMs
            }
            if (times.last() < totalDurationMs) {
                times.add(totalDurationMs)
            }
            return times
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            binding = ItemTimelineThumbnailBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val timeMs = frameTimes[position]

            // 加载缩略图
            ThumbnailLoader.loadThumbnail(
                context = binding.root.context,
                videoPath = videoPath,
                timeMs = timeMs,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                onResult = { bitmap ->
                    holder.thumbnailImage.setImageBitmap(bitmap)
                }
            )

            holder.timeLabel.text = formatTime(timeMs)
            holder.selectedOverlay.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                setSelectedPosition(position)
                onThumbnailClickListener?.invoke(timeMs, position)
            }
        }

        override fun getItemCount(): Int = frameTimes.size

        fun setSelectedPosition(position: Int) {
            val oldPos = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
        }

        fun updateCurrentProgress(currentPositionMs: Int) {
            if (frameTimes.isEmpty()) return
            var newPos = 0
            for (i in frameTimes.indices) {
                if (frameTimes[i] <= currentPositionMs) newPos = i
                else break
            }
            if (newPos != selectedPosition) {
                selectedPosition = newPos
                notifyDataSetChanged() // 生产环境可优化为仅更新两个item
            }
        }

        fun getCurrentSelectedPosition(): Int = selectedPosition

        private fun formatTime(timeMs: Long): String {
            val seconds = timeMs / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return String.format("%02d:%02d", minutes, remainingSeconds)
        }

        class ViewHolder(private val binding: ItemTimelineThumbnailBinding) :
            RecyclerView.ViewHolder(binding.root){
            val thumbnailImage: ImageView = binding.thumbnailImage
            val selectedOverlay: View = binding.selectedOverlay
            val timeLabel: TextView = binding.timeLabel
        }
    }

    // ==================== 内部缩略图加载器 ====================

    private object ThumbnailLoader {
        private const val MEMORY_CACHE_SIZE = 50
        private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
            override fun sizeOf(key: String?, value: Bitmap?): Int = 1
        }
        private lateinit var diskCacheDir: File

        fun init(context: Context) {
            diskCacheDir = File(context.cacheDir, "video_timeline_thumbnails")
            if (!diskCacheDir.exists()) diskCacheDir.mkdirs()
        }

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun loadThumbnail(
            context: Context,
            videoPath: String,
            timeMs: Long,
            targetWidth: Int,
            targetHeight: Int,
            onResult: (Bitmap?) -> Unit,
        ) {
            val cacheKey = "$videoPath@$timeMs@${targetWidth}x${targetHeight}"
            memoryCache.get(cacheKey)?.let {
                onResult(it)
                return
            }

            scope.launch {
                val bitmap = loadFromDiskOrGenerate(context, videoPath, timeMs, targetWidth, targetHeight, cacheKey)
                withContext(Dispatchers.Main) {
                    onResult(bitmap)
                }
            }
        }

        private suspend fun loadFromDiskOrGenerate(
            context: Context,
            videoPath: String,
            timeMs: Long,
            targetWidth: Int,
            targetHeight: Int,
            cacheKey: String,
        ): Bitmap? = withContext(Dispatchers.IO) {
            val diskFile = File(diskCacheDir, "${cacheKey.hashCode()}.jpg")
            if (diskFile.exists()) {
                BitmapFactory.decodeFile(diskFile.absolutePath)?.also {
                    memoryCache.put(cacheKey, it)
                }
            } else {
                generateThumbnail(context, videoPath, timeMs, targetWidth, targetHeight)?.also {
                    saveToDisk(diskFile, it)
                    memoryCache.put(cacheKey, it)
                }
            }
        }

        private fun generateThumbnail(
            context: Context,
            videoPath: String,
            timeMs: Long,
            targetWidth: Int,
            targetHeight: Int,
        ): Bitmap? {
            var retriever: MediaMetadataRetriever? = null
            return try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoPath.toUri())
                val frame = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                frame?.scale(targetWidth, targetHeight)
            } catch (e: Exception) {
                null
            } finally {
                retriever?.release()
            }
        }

        private fun saveToDisk(file: File, bitmap: Bitmap) {
            try {
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            } catch (e: Exception) { /* ignore */ }
        }

        fun clearCache() {
            memoryCache.evictAll()
            scope.launch(Dispatchers.IO) {
                diskCacheDir.deleteRecursively()
                diskCacheDir.mkdirs()
            }
        }
    }

    companion object {
        init {
            // 确保缓存目录在使用前初始化（在 Application 中调用一次即可）
            // 为避免依赖 Context，使用者需在 Application 中调用 VideoTimelineView.initCache(context)
        }

        fun initCache(context: Context) {
            ThumbnailLoader.init(context.applicationContext)
        }
    }
}