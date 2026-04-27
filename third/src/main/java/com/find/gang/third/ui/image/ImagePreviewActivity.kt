package com.find.gang.third.ui.image

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.find.gang.common.BaseActivity
import com.find.gang.third.R
import com.find.gang.third.databinding.ActivityImageBinding
import com.find.gang.third.databinding.ItemImagePreviewBinding
import com.find.gang.third.ui.video.VideoWatchActivity

class ImagePreviewActivity : BaseActivity<ActivityImageBinding>(R.layout.activity_image) {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ImageAdapter

    private val imageUrls by lazy { intent.getStringArrayListExtra("image_urls")  }
    private var mediaItems = mutableListOf<MediaItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val startPosition = intent.getIntExtra("start_position", 0)
        imageUrls?.let {
            it.forEach { url ->
                try {
                    mediaItems.add(MediaItem.fromUri(this@ImagePreviewActivity, url.toUri()))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (mediaItems.isEmpty()) {
            showToast("图片列表为空，请重新选择")
            finish()
            return
        }

        // 关闭按钮
        binding.btnClose.setOnClickListener { finish() }

        // 初始化视图
        viewPager = binding.viewPager

        // 设置适配器
        adapter = ImageAdapter(this, mediaItems)
        viewPager.setAdapter(adapter)
        viewPager.setCurrentItem(startPosition, false)

        // 设置标题
        updateTitle(startPosition)

        // 初始化指示器
        binding.dotsIndicator.attachTo(viewPager)

        // 设置页面变化监听
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateTitle(position)
            }
        })

    }

    private fun updateTitle(position: Int) {
        binding.tvTitle.text = "图片预览(${position + 1}/${mediaItems.size})"
    }

    // 图片适配器
    internal class ImageAdapter(val context: Context, private val mediaItems: MutableList<MediaItem>) :
        RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            return ImageViewHolder(
                ItemImagePreviewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ))
        }

        @UnstableApi
        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            if (mediaItems[position].isVideo()) {
                Glide.with(context)
                    .load(mediaItems[position].uri)
                    .apply(
                        RequestOptions()
                            .frame(1000) // 获取第一秒的帧作为缩略图
                            .centerCrop()
                    )
                    .into(holder.photoView)
                holder.photoView.setOnClickListener { VideoWatchActivity.start(context, mediaItems[position].uri)}
            } else if (mediaItems[position].isGif()){
                Glide.with(context)
                    .asGif()
                    .load(mediaItems[position].uri)
                    .placeholder(R.drawable.media_placeholder)
                    .error(R.drawable.media_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.photoView)
            } else  {
                // 使用Glide加载图片（支持网络图片和本地资源）
                Glide.with(context)
                    .load(mediaItems[position].uri)
                    .placeholder(R.drawable.media_placeholder)
                    .error(R.drawable.media_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.photoView)
            }
        }

        override fun getItemCount(): Int {
            return mediaItems.size
        }

        internal class ImageViewHolder(val binding: ItemImagePreviewBinding) : RecyclerView.ViewHolder(binding.root) {
            var photoView: ImageView = binding.photoView
        }
    }

    companion object {
        // 启动预览Activity的方法
        fun start(context: Context, imageUrls: ArrayList<String>, startPosition: Int) {
            val intent = Intent(context, ImagePreviewActivity::class.java)
            intent.putStringArrayListExtra("image_urls", imageUrls)
            intent.putExtra("start_position", startPosition)
            context.startActivity(intent)
        }

        fun start(context: Context, items: List<MediaItem>, startPosition: Int) {
            val list = ArrayList<String>(items.size)
            items.forEach { item -> list.add(item.uri.toString()) }
            start(context, list, startPosition)
        }
    }
}
