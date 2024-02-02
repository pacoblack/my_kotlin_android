package com.example.myapplication

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView


class HomeActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var pagerAdapter: ScreenSlidePagerAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        viewPager = findViewById(R.id.view_pager)
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // 初始化Fragment适配器
        pagerAdapter = ScreenSlidePagerAdapter(this)
        viewPager.adapter = pagerAdapter

        // 设置底部导航栏与ViewPager联动
        bottomNavigationView.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    viewPager.setCurrentItem(0, true)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_dashboard -> {
                    viewPager.setCurrentItem(1, true)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_notifications -> {
                    viewPager.setCurrentItem(2, true)
                    return@setOnItemSelectedListener true
                }

                else -> return@setOnItemSelectedListener false
            }
        }

        // 默认显示第一个页面
        viewPager.currentItem = 0
    }

    // ViewPager的Fragment适配器
    inner class ScreenSlidePagerAdapter(fa: FragmentActivity?) : FragmentStateAdapter(fa!!) {

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CommonFragment.newInstance()
                1 -> CommonFragment.newInstance()
                2 -> CommonFragment.newInstance()
                else -> throw IllegalStateException("Unexpected position $position")
            }
        }

        override fun getItemCount(): Int {
            return 3 // 根据实际Tab数量调整
        }
    }
}