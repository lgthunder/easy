package com.lei.save_box

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.lei.save_box.adapter.FileAdapter
import com.lei.save_box.databinding.ActivityVaultBinding
import com.lei.save_box.manager.FileManager
import com.lei.save_box.manager.FloatingWindowManager
import com.lei.save_box.manager.SettingsManager
import com.lei.save_box.manager.SortMode
import com.lei.save_box.model.FileItem
import java.io.File

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private lateinit var fileManager: FileManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var floatingWindowManager: FloatingWindowManager
    private lateinit var adapter: FileAdapter
    private var currentSortMode = SortMode.DATE_DESC

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uris = mutableListOf<Uri?>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            }
            importFiles(uris.filterNotNull())
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importFiles(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enterFullScreen()

        fileManager = FileManager(this)
        settingsManager = SettingsManager(this)
        floatingWindowManager = FloatingWindowManager(this, binding.floatingContainer)

        setupRecyclerView()
        setupFabs()
        loadFiles()
    }

    private fun enterFullScreen() {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            onItemClick = { item -> onFileClick(item) },
            onSelectionChanged = { count -> onSelectionCountChanged(count) }
        )

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter
    }

    private fun setupFabs() {
        binding.fabImport.setOnClickListener { showImportDialog() }
        binding.fabMenu.setOnClickListener { showMenuDialog() }
        binding.fabDelete.setOnClickListener { deleteSelectedFiles() }
    }

    private fun onSelectionCountChanged(count: Int) {
        if (count > 0) {
            binding.chipSelectionCount.visibility = View.VISIBLE
            binding.chipSelectionCount.text = getString(R.string.selected_count, count)
            binding.fabImport.visibility = View.GONE
            binding.fabMenu.visibility = View.GONE
            binding.fabDelete.visibility = View.VISIBLE
        } else {
            binding.chipSelectionCount.visibility = View.GONE
            binding.fabImport.visibility = View.VISIBLE
            binding.fabMenu.visibility = View.VISIBLE
            binding.fabDelete.visibility = View.GONE
        }
    }

    private fun showMenuDialog() {
        val items = arrayOf(
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_date),
            getString(R.string.sort_by_size),
            getString(R.string.sort_by_type),
            getString(R.string.settings),
            getString(R.string.exit_app)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.menu)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleSort(SortMode.NAME_ASC, SortMode.NAME_DESC)
                    1 -> toggleSort(SortMode.DATE_ASC, SortMode.DATE_DESC)
                    2 -> toggleSort(SortMode.SIZE_ASC, SortMode.SIZE_DESC)
                    3 -> toggleSort(SortMode.TYPE_ASC, SortMode.TYPE_DESC)
                    4 -> showSettingsDialog()
                    5 -> showExitDialog()
                }
            }
            .show()
    }

    private fun toggleSort(asc: SortMode, desc: SortMode) {
        currentSortMode = if (currentSortMode == asc) desc else asc
        loadFiles()
    }

    private fun showImportDialog() {
        val items = arrayOf(
            getString(R.string.import_from_album),
            getString(R.string.import_from_file)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.import_file)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openImagePicker()
                    1 -> openFilePicker()
                }
            }
            .show()
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_app)
            .setMessage(R.string.exit_app_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                floatingWindowManager.closeAll()
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        imagePickerLauncher.launch(intent)
    }

    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun importFiles(uris: List<Uri>) {
        var successCount = 0
        for (uri in uris) {
            if (fileManager.copyToVault(uri)) {
                successCount++
            }
        }
        if (successCount > 0) {
            Toast.makeText(this, "成功导入 $successCount 个文件", Toast.LENGTH_SHORT).show()
            loadFiles()
        }
    }

    private fun loadFiles() {
        val files = fileManager.listFiles(currentSortMode)
        adapter.submitList(files)
        updateEmptyView(files.isEmpty())
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.layoutEmpty.root.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvFiles.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun onFileClick(item: FileItem) {
        if (item.isImage) {
            floatingWindowManager.openImage(item.path)
        } else if (item.isVideo) {
            floatingWindowManager.openVideo(item.path)
        } else {
            openFileExternally(item)
        }
    }

    private fun openFileExternally(item: FileItem) {
        val file = File(item.path)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val mimeType = fileManager.getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettingsDialog() {
        val switchView = SwitchCompat(this).apply {
            text = getString(R.string.biometric_toggle)
            isChecked = settingsManager.isBiometricEnabled
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.isBiometricEnabled = isChecked
            }
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setMessage(R.string.biometric_toggle_desc)
            .setView(switchView)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun deleteSelectedFiles() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_message, selectedItems.size))
            .setPositiveButton(R.string.confirm) { _, _ ->
                val paths = selectedItems.map { it.path }
                val deletedCount = fileManager.deleteFiles(paths)
                if (deletedCount > 0) {
                    Toast.makeText(this, "已删除 $deletedCount 个文件", Toast.LENGTH_SHORT).show()
                }
                adapter.exitSelectionMode()
                loadFiles()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onBackPressed() {
        if (adapter.isSelectionMode) {
            adapter.exitSelectionMode()
            return
        }

        showExitDialog()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterFullScreen()
        }
    }
}
