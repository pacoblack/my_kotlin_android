package com.find.gang.video.lib.trim

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.find.gang.video.lib.R
import com.find.gang.video.lib.toolbox.VideoTrimmerUtil

class VideoTrimmerAdapter(context: Context?) : RecyclerView.Adapter<VideoTrimmerAdapter.TrimmerViewHolder>() {
    private val mBitmaps: MutableList<Bitmap?> = ArrayList()
    private val mInflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrimmerViewHolder {
        return TrimmerViewHolder(mInflater.inflate(R.layout.video_thumb_item_layout, parent, false))
    }

    override fun onBindViewHolder(holder: TrimmerViewHolder, position: Int) {
        holder.thumbImageView.setImageBitmap(mBitmaps[position])
    }

    override fun getItemCount(): Int {
        return mBitmaps.size
    }

    fun addBitmaps(bitmap: Bitmap?) {
        mBitmaps.add(bitmap)
        notifyDataSetChanged()
    }

    class TrimmerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var thumbImageView: ImageView = itemView.findViewById(R.id.thumb)

        init {
            val layoutParams = thumbImageView.layoutParams as LinearLayout.LayoutParams
            layoutParams.width =
                VideoTrimmerUtil.VIDEO_FRAMES_WIDTH / VideoTrimmerUtil.MAX_COUNT_RANGE
            thumbImageView.setLayoutParams(layoutParams)
        }
    }
}
