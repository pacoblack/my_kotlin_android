package com.example.myapplication

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WaterFallLayout.init(this)
        findViewById<View?>(R.id.jni_btn)
            .setOnClickListener(object : View.OnClickListener {
                override fun onClick(view: View?) {
                }
            })
        findViewById<View?>((R.id.video_btn))
            .setOnClickListener(object : View.OnClickListener {
                override fun onClick(view: View?) {
                }
            })
    }
}