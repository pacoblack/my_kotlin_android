package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import com.test.gang.cmake.Hello
//import com.test.gang.cmake.NativeDemo

class MainActivity2 : AppCompatActivity() {
    init {
        System.loadLibrary("native-lib")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WaterFallLayout.init(this)
        findViewById<View>(R.id.jni_btn)
            .setOnClickListener {
//                Toast.makeText(this@MainActivity2, NativeDemo.helloFromJNI(), Toast.LENGTH_LONG)
//                    .show()
//                val hello = Hello()
//                hello.test()
            }
    }
}
