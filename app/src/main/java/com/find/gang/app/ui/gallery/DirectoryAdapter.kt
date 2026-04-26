package com.find.gang.app.ui.gallery

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.find.gang.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DirectoryAdapter(
    private val onDeleteClick: (Int) -> Unit,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<DirectoryAdapter.VH>() {

    private var items = listOf<DirectoryItem>()

    fun submitList(list: List<DirectoryItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_directory_gallery, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: DirectoryItem) {
            itemView.findViewById<TextView>(R.id.tv_dir_name).text = item.name
            itemView.findViewById<ImageButton>(R.id.btn_delete).setOnClickListener {
                onDeleteClick(getBindingAdapterPosition())
            }
            itemView.setOnClickListener { onItemClick(getBindingAdapterPosition()) }
        }
    }
}

class MediaAdapter : RecyclerView.Adapter<MediaAdapter.VH>() {

    private var items = listOf<MediaItem>()

    fun submitList(list: List<MediaItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_gallery, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val ivTypeIcon: ImageView = itemView.findViewById(R.id.iv_type_icon)

        fun bind(item: MediaItem) {
            when (item.type) {
                MediaType.IMAGE -> {
                    ivTypeIcon.setImageResource(R.drawable.ic_image)
                    Glide.with(itemView.context)
                        .load(item.uri)
                        .centerCrop()
                        .into(ivThumb)
                }
                MediaType.VIDEO -> {
                    ivTypeIcon.setImageResource(R.drawable.ic_player)
                    // 使用自定义缩略图加载（见下方说明）
                    loadVideoThumbnail(item.uri, ivThumb)
                }
            }
        }

        private fun loadVideoThumbnail(uri: Uri, imageView: ImageView) {
            // 简单起见使用 Glide 加载，但需确保支持，或自定义 Target
            // 推荐用协程 + MediaMetadataRetriever 生成 Bitmap，再 setImageBitmap
            // 也可用 Glide 的 SimpleTarget，这里给出协程版本：
            val context = imageView.context
            CoroutineScope(Dispatchers.IO).launch {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val bitmap = retriever.frameAtTime
                    withContext(Dispatchers.Main) {
                        imageView.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        imageView.setImageResource(R.drawable.ic_broken_file)
                    }
                } finally {
                    retriever.release()
                }
            }
        }
    }
}