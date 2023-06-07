package com.gang.lib.torrent.extensions

import com.frostwire.jlibtorrent.TorrentInfo
import com.gang.lib.torrent.extensions.getLargestFileIndex


/**
 * Get the largest file index of the [TorrentInfo].
 */
internal fun TorrentInfo.getLargestFileIndex(): Int = files().getLargestFileIndex()
