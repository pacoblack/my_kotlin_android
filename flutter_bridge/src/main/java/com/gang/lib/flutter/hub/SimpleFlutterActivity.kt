package com.gang.lib.flutter.hub

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity


class SimpleFlutterActivity: AppCompatActivity() {

    companion object {

        fun initFlutterEngine(){
        }
        fun startActivity(activity: Activity){

        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flutter_simple)


        findViewById<View>(R.id.flutter_root).setOnClickListener {
        }
    }

}