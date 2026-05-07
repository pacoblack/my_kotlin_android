package com.find.gang.app.widget

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MediaGridSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 创建RecyclerView并配置
    private val recyclerView: RecyclerView = RecyclerView(context).apply {
        layoutManager = GridLayoutManager(context, 3)
        // 固定高度让每个格子为正方形（通过item布局的 weight 或直接设置适配器）
    }
    private val adapter: MediaGridAdapter
    private var selectionListener: OnSelectionChangedListener? = null

    private var itemLongClickListener: OnItemLongClickListener? = null


    // 数据：视频Uri列表（最多9个）
    private var videoUris: List<Uri> = emptyList()

    // 已选中的位置（adapter position）列表，按选中顺序排列
    private val selectedPositions = mutableListOf<Int>()

    // 协程作用域，绑定到LifecycleOwner
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        adapter = MediaGridAdapter(
            onItemClick = { position -> handleItemClick(position) },
            onItemLongClick = { position ->
                val uri = videoUris.getOrNull(position) ?: return@MediaGridAdapter
                itemLongClickListener?.onItemLongClick(position, uri)
            }
        )
        recyclerView.adapter = adapter
    }

    /**
     * 设置视频Uri列表（最多9个），并刷新界面。
     */
    fun setVideoUris(uris: List<Uri>) {
        require(uris.size <= 9) { "视频数量最多为9个" }
        videoUris = uris.toList()
        selectedPositions.clear()   // 重置选择状态
        //adapter.submitList(videoUris.map { it to -1 }) // -1 表示未选中

        selectedPositions.addAll(videoUris.indices)
        updateAdapterData()
        notifySelectionChanged()
    }

    /**
     * 设置选择回调监听
     */
    fun setOnSelectionChangedListener(listener: OnSelectionChangedListener) {
        this.selectionListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener) {
        this.itemLongClickListener = listener
    }

    private fun handleItemClick(position: Int) {
        val index = selectedPositions.indexOf(position)
        if (index == -1) {
            // 添加选中：序号为当前选中数量+1
            selectedPositions.add(position)
        } else {
            // 取消选中
            selectedPositions.removeAt(index)
        }

        // 更新适配器显示
        updateAdapterData()
        // 触发回调
        notifySelectionChanged()
    }

    private fun updateAdapterData() {
        val list = videoUris.mapIndexed { index, uri ->
            val order = if (selectedPositions.contains(index)) {
                selectedPositions.indexOf(index) + 1   // 1-based序号
            } else -1
            uri to order
        }
        adapter.submitList(list)
    }

    private fun notifySelectionChanged() {
        val selected = selectedPositions.map { position ->
            val order = selectedPositions.indexOf(position) + 1
            videoUris[position] to order
        }.sortedBy { it.second }   // 按序号排序
        selectionListener?.onSelectionChanged(selected)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 绑定到最近的LifecycleOwner，自动管理协程取消
        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    // 回调接口
    interface OnSelectionChangedListener {
        /**
         * @param selected 已选中的视频，Pair<Uri, 选中序号>，按序号升序排列
         */
        fun onSelectionChanged(selected: List<Pair<Uri, Int>>)
    }

    interface OnItemLongClickListener {
        /**
         * @param position 被长按的格子位置
         * @param uri      对应的视频Uri
         */
        fun onItemLongClick(position: Int, uri: Uri)
    }

}