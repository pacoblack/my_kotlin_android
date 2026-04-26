package com.find.gang.app.ui.gallery

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.find.gang.app.R
import com.find.gang.app.base.BaseActivity
import com.find.gang.app.databinding.ActivityGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.core.content.edit

class GalleryActivity : BaseActivity<ActivityGalleryBinding, GalleryViewModel>(R.layout.activity_gallery) {
    override fun getViewModelClass(): Class<GalleryViewModel> = GalleryViewModel::class.java

    private val directories = mutableListOf<DirectoryItem>()
    private var currentDirectoryIndex = -1
    private val allMedia = mutableListOf<MediaItem>()
    private var filterMode = FilterMode.ALL  // ALL, IMAGE, VIDEO

    private lateinit var directoryAdapter: DirectoryAdapter
    private lateinit var mediaAdapter: MediaAdapter

    private val openTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // 2. 手动持久化权限（必须）
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            // 这里保存或使用 uri
        }
    }

    override fun initView() {
        setupToolbar()
        setupDirectoriesRecycler()
        setupMediaRecycler()
        loadDirectories()
        setupListeners()
        updateUI()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.filter_image -> { filterMode = FilterMode.IMAGE; refreshMediaGrid() }
                R.id.filter_video -> { filterMode = FilterMode.VIDEO; refreshMediaGrid() }
                R.id.filter_all -> { filterMode = FilterMode.ALL; refreshMediaGrid() }
            }
            true
        }
    }

    private fun setupDirectoriesRecycler() {
        directoryAdapter = DirectoryAdapter(
            onDeleteClick = { position -> deleteDirectory(position) },
            onItemClick = { position -> selectDirectory(position) }
        )
        binding.drawerIncludeContent.rvDirectories.layoutManager = LinearLayoutManager(this)
        binding.drawerIncludeContent.rvDirectories.adapter = directoryAdapter
    }

    private fun setupMediaRecycler() {
        mediaAdapter = MediaAdapter()
        binding.rvMedia.layoutManager = GridLayoutManager(this, 5)
        binding.rvMedia.adapter = mediaAdapter
    }

    private fun loadDirectories() {
        // 从 SharedPreferences 读取目录列表及持久化 URI
        val prefs = getSharedPreferences("dirs", MODE_PRIVATE)
        val dataSet = prefs.getStringSet("dir_set", emptySet()) ?: emptySet()
        directories.clear()
        dataSet.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                val name = parts[0]
                val uri = parts[1].toUri()
                // 尝试再次获取持久化权限（通常已有）
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    directories.add(DirectoryItem(name, uri))
                } catch (_: SecurityException) {
                    // 权限丢失，跳过
                }
            }
        }
        directoryAdapter.submitList(directories.toList())
        if (directories.isNotEmpty() && currentDirectoryIndex == -1) {
            selectDirectory(0)
        }
    }

    private fun saveDirectories() {
        val prefs = getSharedPreferences("dirs", MODE_PRIVATE)
        val set = mutableSetOf<String>()
        directories.forEach { set.add("${it.name}|${it.uri}") }
        prefs.edit { putStringSet("dir_set", set) }
    }

    private fun addDirectory() {
        openTreeLauncher.launch(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_TREE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = getDirectoryName(uri)
                directories.add(DirectoryItem(name, uri))
                directoryAdapter.submitList(directories.toList())
                saveDirectories()
                if (currentDirectoryIndex == -1) selectDirectory(0)
            }
        }
    }

    private fun getDirectoryName(uri: Uri): String {
        val doc = DocumentFile.fromTreeUri(this, uri)
        return doc?.name ?: uri.lastPathSegment ?: "未知目录"
    }

    private fun selectDirectory(index: Int) {
        if (index < 0 || index >= directories.size) return
        currentDirectoryIndex = index
        binding.toolbar.title = directories[index].name
        scanMediaFiles(directories[index].uri)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun scanMediaFiles(rootUri: Uri) {
        // 异步扫描，这里用协程示意
        lifecycleScope.launch(Dispatchers.IO) {
            val media = mutableListOf<MediaItem>()
            val doc = DocumentFile.fromTreeUri(this@GalleryActivity, rootUri)
            doc?.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val name = file.name ?: return@forEach
                    when {
                        name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".gif", true) ->
                            media.add(MediaItem(file.uri, name, MediaType.IMAGE))
                        name.endsWith(".mp4", true) || name.endsWith(".mkv", true) || name.endsWith(".3gp", true) ->
                            media.add(MediaItem(file.uri, name, MediaType.VIDEO))
                    }
                }
            }
            withContext(Dispatchers.Main) {
                allMedia.clear()
                allMedia.addAll(media)
                refreshMediaGrid()
            }
        }
    }

    private fun refreshMediaGrid() {
        val filtered = when (filterMode) {
            FilterMode.ALL -> allMedia.toList()
            FilterMode.IMAGE -> allMedia.filter { it.type == MediaType.IMAGE }
            FilterMode.VIDEO -> allMedia.filter { it.type == MediaType.VIDEO }
        }
        mediaAdapter.submitList(filtered)
        updateUI()
    }

    private fun updateUI() {
        val hasDirs = directories.isNotEmpty()
        val hasSelected = currentDirectoryIndex != -1
        binding.layoutEmpty.visibility = if (!hasDirs) View.VISIBLE else View.GONE
        binding.rvMedia.visibility = if (hasSelected) View.VISIBLE else View.GONE
    }

    private fun deleteDirectory(position: Int) {
        val item = directories[position]
        // 释放持久化权限
        try {
            contentResolver.releasePersistableUriPermission(item.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        directories.removeAt(position)
        directoryAdapter.submitList(directories.toList())
        saveDirectories()
        if (currentDirectoryIndex == position) {
            // 如果删除当前选中，自动选下一个或清空
            if (directories.isNotEmpty()) {
                selectDirectory(if (position < directories.size) position else position - 1)
            } else {
                currentDirectoryIndex = -1
                allMedia.clear()
                mediaAdapter.submitList(emptyList())
                binding.toolbar.title = "无目录"
            }
        } else if (currentDirectoryIndex > position) {
            currentDirectoryIndex--  // 修正索引
        }
        updateUI()
    }

    private fun setupListeners() {
        binding.drawerIncludeContent.btnAddDirectory.setOnClickListener { addDirectory() }
        binding.btnOpenDrawer.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
    }

    companion object {
        const val REQUEST_CODE_OPEN_TREE = 1001
    }
}