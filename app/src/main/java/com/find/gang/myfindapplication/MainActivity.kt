package com.find.gang.myfindapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.find.gang.myfindapplication.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置 ActionBar 与 Navigation 联动
        val navController = binding.navHostFragment.findNavController()
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    // 处理返回按钮事件
    override fun onSupportNavigateUp(): Boolean {
        val navController = binding.navHostFragment.findNavController()
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}