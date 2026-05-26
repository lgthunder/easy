package com.lei.save_box

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.invalidateOptionsMenu
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.lei.save_box.adapter.FileAdapter
import com.lei.save_box.databinding.ActivityVaultBinding
import com.lei.save_box.manager.FileManager
import com.lei.save_box.manager.SortMode
import com.lei.save_box.model.FileItem
import java.io.File

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private lateinit var fileManager: FileManager
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

        fileManager = FileManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.vault_title)

        setupRecyclerView()
        setupFab()
        loadFiles()
    }

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            onItemClick = { item -> openFile(item) },
            onSelectionChanged = { count ->
                supportActionBar?.apply {
                    if (count > 0) {
                        title = getString(R.string.files_count, count)
                    } else {
                        title = getString(R.string.vault_title)
                    }
                }
                invalidateOptionsMenu()
            }
        )

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter
    }

    private fun setupFab() {
        binding.fabImport.setOnClickListener {
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

    private fun openFile(item: FileItem) {
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

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_vault_sort, menu)

        val isSelectionMode = adapter.isSelectionMode
        menu.findItem(R.id.action_sort).isVisible = !isSelectionMode
        menu.findItem(R.id.action_delete).isVisible = isSelectionMode
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                deleteSelectedFiles()
                true
            }
            R.id.action_sort_name -> {
                currentSortMode = if (currentSortMode == SortMode.NAME_ASC)
                    SortMode.NAME_DESC else SortMode.NAME_ASC
                loadFiles()
                true
            }
            R.id.action_sort_date -> {
                currentSortMode = if (currentSortMode == SortMode.DATE_ASC)
                    SortMode.DATE_DESC else SortMode.DATE_ASC
                loadFiles()
                true
            }
            R.id.action_sort_size -> {
                currentSortMode = if (currentSortMode == SortMode.SIZE_ASC)
                    SortMode.SIZE_DESC else SortMode.SIZE_ASC
                loadFiles()
                true
            }
            R.id.action_sort_type -> {
                currentSortMode = if (currentSortMode == SortMode.TYPE_ASC)
                    SortMode.TYPE_DESC else SortMode.TYPE_ASC
                loadFiles()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (adapter.isSelectionMode) {
            adapter.exitSelectionMode()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.exit_vault)
            .setMessage(R.string.exit_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
