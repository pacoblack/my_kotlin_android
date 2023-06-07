package com.example.myapplication.torrent.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.gang.lib.torrent.models.TorrentSessionBuffer
import com.gang.lib.torrent.models.TorrentSessionStatus

class TorrentPieceAdapter : RecyclerView.Adapter<TorrentPieceAdapter.PieceViewHolder>() {

    class PieceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val cardView: CardView = itemView.findViewById(R.id.card_view_piece)

        fun bind(color: Int) {
            cardView.setBackgroundColor(color)
        }

    }

    private lateinit var torrentSessionBuffer: TorrentSessionBuffer

    private var isInitialized = false

    private var lastCompletedPieceCount = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PieceViewHolder =
            PieceViewHolder(LayoutInflater
                    .from(parent.context)
                    .inflate(
                            R.layout.torrent_item_piece,
                            parent,
                            false
                    ))

    override fun getItemCount(): Int = if (isInitialized) torrentSessionBuffer.pieceCount else 0

    override fun onBindViewHolder(holder: PieceViewHolder, position: Int) {
        val context = holder.itemView.context

        holder.bind(getPieceColor(context, position))
    }

    private fun getPieceColor(
            context: Context
            , position: Int
    ): Int {
        val isDownloaded = torrentSessionBuffer.isPieceDownloaded(position)
        val isHeadIndex = torrentSessionBuffer.bufferHeadIndex == position

        if (isDownloaded) {
            if (isHeadIndex) {
                return ContextCompat.getColor(context, R.color.blue)
            }

            return ContextCompat.getColor(context, R.color.green)
        }

        if (torrentSessionBuffer.bufferSize == 0) {
            return ContextCompat.getColor(context, R.color.purple)
        }

        if (isHeadIndex) {
            return ContextCompat.getColor(context, R.color.red)
        }

        if (position > torrentSessionBuffer.bufferHeadIndex
                && position <= torrentSessionBuffer.bufferTailIndex) {
            return ContextCompat.getColor(context, R.color.yellow)
        }

        return ContextCompat.getColor(context, R.color.purple)
    }

    fun configure(torrentSessionStatus: TorrentSessionStatus) {
        val downloadedPieceCount = torrentSessionStatus
                .torrentSessionBuffer
                .downloadedPieceCount

        if (downloadedPieceCount != lastCompletedPieceCount) {
            lastCompletedPieceCount = downloadedPieceCount
            torrentSessionBuffer = torrentSessionStatus.torrentSessionBuffer
            isInitialized = true

            notifyDataSetChanged()
        }
    }

}