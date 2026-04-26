package com.find.gang.app.ui.gallery

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.find.gang.app.R
import com.find.gang.app.databinding.ItemDirectoryGalleryBinding
import com.find.gang.app.databinding.ItemMediaGalleryBinding
import com.find.gang.third.ui.image.ImagePreviewActivity
import com.find.gang.third.ui.image.MediaItem
import com.find.gang.third.ui.image.MediaType
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
        val bind = ItemDirectoryGalleryBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
        )
        return VH(bind)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class VH(private val binding: ItemDirectoryGalleryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DirectoryItem) {
            binding.tvDirName.text = item.name
            binding.btnDelete.setOnClickListener {
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
        val bind = ItemMediaGalleryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(bind)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount() = items.size

    inner class VH(private val binding: ItemMediaGalleryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem, position: Int) {
            when (item.type) {
                MediaType.IMAGE -> {
                    binding.ivTypeIcon.setImageResource(R.drawable.ic_image)
                    Glide.with(itemView.context)
                        .load(item.uri)
                        .centerCrop()
                        .into(binding.ivThumbnail)
                    binding.ivThumbnail.setOnClickListener {
                        ImagePreviewActivity.start(binding.root.context, items, position)
                    }
                }
                MediaType.VIDEO -> {
                    binding.ivTypeIcon.setImageResource(R.drawable.ic_player)
                    // 使用自定义缩略图加载（见下方说明）
                    loadVideoThumbnail(item.uri, binding.ivThumbnail)
                }

                else -> {}
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
                } catch (_: Exception) {
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