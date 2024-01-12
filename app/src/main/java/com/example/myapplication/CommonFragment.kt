package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class CommonFragment:Fragment() {
    companion object {
        fun newInstance(): CommonFragment = CommonFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化工作
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_common, container, false)

//        // 示例：找到并设置按钮的点击监听器
//        rootView.findViewById<Button>(R.id.my_button).setOnClickListener {
//            // 处理按钮点击事件
//        }
        rootView.setOnClickListener {
            startActivity(Intent(context, DownloadVideoActivity::class.java))
        }

        return rootView
    }

}