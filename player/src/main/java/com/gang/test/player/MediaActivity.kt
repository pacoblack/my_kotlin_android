package com.gang.test.player

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView

@UnstableApi class MediaActivity : PlayerActivity() {
    private var buttonOpen: ImageButton? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initOpenButton()
    }

    private fun initOpenButton(){
        buttonOpen = ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom)
        buttonOpen!!.setImageResource(R.drawable.ic_folder_open_24dp)
        buttonOpen!!.id = View.generateViewId()
        buttonOpen!!.contentDescription = getString(R.string.button_open)
        buttonOpen!!.setOnClickListener {
            openFile(
                mPrefs!!.mediaUri
            )
        }
        buttonOpen!!.setOnLongClickListener {
            if (!isTvBox && mPrefs!!.askScope) {
                askForScope(true, false)
            } else {
                loadSubtitleFile(mPrefs!!.mediaUri)
            }
            true
        }
    }

    override fun addViewInControls(controls: ViewGroup) {
        controls.addView(buttonOpen)
    }

    override fun onFirstRun(){
        TapTargetView.showFor(this@MediaActivity,
            TapTarget.forView(
                buttonOpen, getString(R.string.onboarding_open_title), getString(
                    R.string.onboarding_open_description
                )
            )
                .outerCircleColor(R.color.green)
                .targetCircleColor(R.color.white)
                .titleTextSize(22)
                .titleTextColor(R.color.white)
                .descriptionTextSize(14)
                .cancelable(true),
            object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    buttonOpen!!.performClick()
                }
            })
    }

}