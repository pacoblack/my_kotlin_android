package com.find.gang.app.ui.main

import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.find.gang.app.R
import com.find.gang.app.base.BaseActivity
import com.find.gang.app.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>(R.layout.activity_main) {

    private val navController: NavController by lazy {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController
    }

    override fun getViewModelClass(): Class<MainViewModel> = MainViewModel::class.java

    override fun initView() {
        super.initView()
        binding.bottomNavView.setupWithNavController(navController)
    }

    override fun observeData() {
        super.observeData()
    }

    // 处理返回按钮事件
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}