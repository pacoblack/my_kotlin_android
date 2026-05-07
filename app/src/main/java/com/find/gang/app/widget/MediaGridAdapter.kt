package com.find.gang.app.widget

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.videoFrameMillis
import com.find.gang.app.databinding.ItemGridMediaBinding
import com.find.gang.app.toolbox.Toolbox.logRed

class MediaGridAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onItemLongClick: (Int) -> Unit
) : RecyclerView.Adapter<MediaGridAdapter.ViewHolder>() {

    private var items: List<Pair<Uri, Int>> = emptyList()   // Pair<uri, order>，order=-1即未选中

    fun submitList(newList: List<Pair<Uri, Int>>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGridMediaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (uri, order) = items[position]
        holder.bind(uri, order)
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
        // 长按（返回 true 表示消费事件，避免再触发点击）
        holder.itemView.setOnLongClickListener {
            onItemLongClick(position)
            true
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemGridMediaBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri, order: Int) {
            // 加载视频第一帧缩略图
            logRed("MediaGridAdapter","加载缩略图 $order $uri")
            binding.ivThumbnail.load(uri) {
                videoFrameMillis(1000)   // 取第1秒的帧
                crossfade(true)
            }

            // 显示/隐藏序号
            if (order > 0) {
                binding.tvOrder.visibility = View.VISIBLE
                binding.tvOrder.text = order.toString()
            } else {
                binding.tvOrder.visibility = View.GONE
            }
        }
    }
}