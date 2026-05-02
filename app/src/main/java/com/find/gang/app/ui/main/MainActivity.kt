package com.find.gang.app.ui.main

import android.net.Uri
import android.os.Build
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.find.gang.app.R
import com.find.gang.app.base.BaseActivity
import com.find.gang.app.databinding.ActivityMainBinding
import com.find.gang.app.toolbox.FileTools.copyToInputFileDir
import com.find.gang.app.ui.dialog.InputDialogConfig
import com.find.gang.app.ui.dialog.InputDialogFragment
import com.find.gang.app.ui.gallery.GalleryActivity
import com.find.gang.app.ui.video.edit.VideoToGifActivity
import com.find.gang.common.toolbox.PickerManager

class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>(R.layout.activity_main) {

    private val navController: NavController by lazy {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController
    }
    private val manager = PickerManager()

    override fun getViewModelClass(): Class<MainViewModel> = MainViewModel::class.java

    override fun initView() {
        super.initView()

        setupToolbar()
        binding.bottomNavView.setupWithNavController(navController)

        manager.register(this, object : PickerManager.Callback {
            override fun onVideoPicked(uri: Uri) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VideoToGifActivity.start(this@MainActivity, uri.copyToInputFileDir())
                }
            }
        })
    }

    fun setupToolbar(){
        setSupportActionBar(binding.mainToolbar)   // 把自定义 Toolbar 设为 ActionBar
        binding.mainToolbar.inflateMenu(R.menu.menu_toolbar_home)
        binding.mainToolbar.setOnMenuItemClickListener { item ->
            this.onOptionsItemSelected(item)
        }
    }

    fun showBack(){
        // 如需返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun observeData() {
        super.observeData()
    }

    // 处理返回按钮事件
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    // 在 supportActionBar?.setDisplayHomeAsUpEnabled(true) 时生效
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar_home, menu)  // 你的菜单 XML 文件
        return true
    }

    // 处理菜单点击（如果需要）
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_camera -> {
                GalleryActivity.startActivity(this)
                true
            }
            R.id.action_more -> {
                val config = InputDialogConfig(
                    requestKey = "video_keyword",
                    title = "视频地址",
                    inputType = InputType.TYPE_CLASS_TEXT
                )
                InputDialogFragment.newInstance(config).show(supportFragmentManager, InputDialogFragment.TAG)
                true
            }
            R.id.action_edit_video ->{
                manager.pickSingleVideo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}