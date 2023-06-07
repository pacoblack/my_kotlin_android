package com.example.myapplication.torrent.fragments

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.torrent.adapters.TabFragmentPagerAdapter
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentStatus
import com.gang.lib.torrent.TorrentSession
import com.gang.lib.torrent.TorrentSessionOptions
import com.gang.lib.torrent.contracts.TorrentSessionListener
import com.gang.lib.torrent.models.TorrentSessionStatus
import java.lang.ref.WeakReference

class TorrentFragment : Fragment()
        , TabFragmentPagerAdapter.TabFragment<TorrentSessionStatus>
        , TorrentSessionListener {

    private lateinit var torrentSession: TorrentSession

    private var torrentPiecesFragment: TorrentPiecesFragment? = null
    private var buttonPauseResume: AppCompatButton? = null

    private var torrentSessionStatus: TorrentSessionStatus? = null
    private var startDownloadTask: DownloadTask? = null

    private var tabIndex: Int = 0

    companion object {

        private const val Tag = "TorrentFragment"

        fun newInstance(
                context: Context
                , tabIndex: Int
                , magnetUri: Uri
                , torrentSessionOptions: TorrentSessionOptions
        ): TorrentFragment = TorrentFragment().apply {
            this.tabIndex = tabIndex

            torrentSession = TorrentSession(torrentSessionOptions)
            torrentSession.listener = this

            startDownloadTask = DownloadTask(context, torrentSession, magnetUri)
            startDownloadTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater
            , container: ViewGroup?
            , savedInstanceState: Bundle?
    ): View = inflater.inflate(
            R.layout.torrent_fragment
            , container
            , false
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViewComponents()
        subscribeToViewComponents()
        setPauseResumeButtonText()
    }

    private fun bindViewComponents() {
        torrentPiecesFragment = childFragmentManager.findFragmentById(R.id.fragment_torrent_pieces) as TorrentPiecesFragment
        buttonPauseResume = requireView().findViewById(R.id.button_pause_resume)
    }

    private fun subscribeToViewComponents() {
        buttonPauseResume?.setOnClickListener {
            if (torrentSession.isPaused) {
                torrentSession.resume()
                buttonPauseResume?.text = "PAUSE"
            } else {
                torrentSession.pause()
                buttonPauseResume?.text = "RESUME"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        startDownloadTask?.cancel(true)
        torrentSession.listener = null
        torrentSession.stop()
    }

    override fun configure(model: TorrentSessionStatus) {
    }

    override fun getTitle(): String = "Torrent: $tabIndex"

    override fun onBlockUploaded(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onBlockUploaded", torrentSessionStatus)

    override fun onAddTorrent(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onAddTorrent", torrentSessionStatus)

    override fun onTorrentRemoved(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onTorrentRemoved", torrentSessionStatus)

    override fun onTorrentDeleted(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onTorrentRemoved", torrentSessionStatus)

    override fun onTorrentDeleteFailed(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onTorrentDeleteFailed", torrentSessionStatus)

    override fun onTorrentError(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onTorrentError", torrentSessionStatus)

    override fun onTorrentResumed(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onTorrentResumed", torrentSessionStatus)

    override fun onTorrentPaused(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onTorrentPaused", torrentSessionStatus)

    override fun onTorrentFinished(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onTorrentFinished", torrentSessionStatus)

    override fun onPieceFinished(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onPieceFinished", torrentSessionStatus)

    override fun onMetadataFailed(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onMetadataFailed", torrentSessionStatus)

    override fun onMetadataReceived(
            torrentHandle: TorrentHandle
            , torrentSessionStatus: TorrentSessionStatus
    ) = configure("onMetadataReceived", torrentSessionStatus)

    private fun setPauseResumeButtonText() {
        if (torrentSessionStatus?.state == TorrentStatus.State.SEEDING) {
            buttonPauseResume?.setText("SEEDING")
            buttonPauseResume?.isEnabled = false
            return
        }

        if (torrentSessionStatus?.state == TorrentStatus.State.FINISHED) {
            buttonPauseResume?.setText("FINISHED")
            buttonPauseResume?.isEnabled = false
            return
        }

        if (torrentSession.isPaused) {
            buttonPauseResume?.setText("RESUME")
        } else {
            buttonPauseResume?.setText("PAUSE")
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        setPauseResumeButtonText()

        if (torrentSessionStatus != null) {
            torrentPiecesFragment?.configure(torrentSessionStatus!!)
        }
    }

    private fun configure(
            tag: String
            , torrentSessionStatus: TorrentSessionStatus
    ) {
        Log.d(tag, torrentSessionStatus.toString())

        this.torrentSessionStatus = torrentSessionStatus

        torrentPiecesFragment?.configure(torrentSessionStatus)

        setPauseResumeButtonText()
    }

    private class DownloadTask : AsyncTask<Void, Void, Unit> {

        private val context: WeakReference<Context>
        private val torrentSession: WeakReference<TorrentSession>
        val magnetUri: Uri

        @Suppress("ConvertSecondaryConstructorToPrimary")
        constructor(
                context: Context
                , torrentSession: TorrentSession
                , magnetUri: Uri
        ) : super() {
            this.context = WeakReference(context)
            this.torrentSession = WeakReference(torrentSession)
            this.magnetUri = magnetUri
        }

        override fun doInBackground(vararg args: Void) {
            try {
                torrentSession
                        .get()
                        ?.start(context.get()!!, magnetUri)
            } catch (ex: Exception) {
                Log.e(Tag, "Failed to start torrent", ex)
            }
        }
    }

}