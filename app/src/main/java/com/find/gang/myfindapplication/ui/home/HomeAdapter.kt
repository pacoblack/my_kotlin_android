package com.find.gang.myfindapplication.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.find.gang.myfindapplication.R
import com.find.gang.myfindapplication.databinding.ItemArticleBinding

/**
 * 首页文章列表适配器
 * @param onItemClick 点击回调，参数为 ArticleItem
 */
class HomeAdapter(
    private val onItemClick: (ArticleItem) -> Unit
) : ListAdapter<ArticleItem, HomeAdapter.ArticleViewHolder>(ArticleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val binding = ItemArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ArticleViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ArticleViewHolder(
        private val binding: ItemArticleBinding,
        private val onItemClick: (ArticleItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ArticleItem) {
            binding.tvTitle.text = item.title
            binding.tvType.text = when (item.type) {
                "video" -> "视频"
                "webview" -> "网页"
                else -> "未知"
            }
            // 设置类型背景色
            binding.tvType.setBackgroundResource(
                if (item.type == "video") R.drawable.bg_video_tag else R.drawable.bg_web_tag
            )
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}

/**
 * DiffUtil 回调，优化列表更新性能
 */
class ArticleDiffCallback : DiffUtil.ItemCallback<ArticleItem>() {
    override fun areItemsTheSame(oldItem: ArticleItem, newItem: ArticleItem): Boolean {
        return oldItem.title == newItem.title && oldItem.url == newItem.url
    }

    override fun areContentsTheSame(oldItem: ArticleItem, newItem: ArticleItem): Boolean {
        return oldItem == newItem
    }
}