package com.lei.save_box

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.lei.save_box.adapter.FileAdapter
import com.lei.save_box.databinding.ActivityVaultBinding
import com.lei.save_box.glide.FFmpegLogger
import com.lei.save_box.manager.BackgroundTaskManager
import com.lei.save_box.manager.BackupManager
import com.lei.save_box.manager.FileManager
import com.lei.save_box.manager.FloatingWindowManager
import com.lei.save_box.manager.SettingsManager
import com.lei.save_box.manager.SortMode
import com.lei.save_box.model.FileItem
import com.lei.save_box.view.ProgressDialogHelper
import com.lei.save_box.view.TaskFloatingWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VaultActivity : AppCompatActivity(), LockableActivity {

    private lateinit var binding: ActivityVaultBinding
    private lateinit var fileManager: FileManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var floatingWindowManager: FloatingWindowManager
    private lateinit var backupManager: BackupManager
    private lateinit var adapter: FileAdapter
    private var currentSortMode = SortMode.DATE_DESC
    private var taskFloatingWindow: TaskFloatingWindow? = null
    private var currentDir: File? = null
    private var backupImportUri: Uri? = null

    private val isAtRoot: Boolean
        get() = currentDir == null || currentDir == fileManager.vaultDir

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

    private val trashActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        loadFiles()
    }

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            backupImportUri = it
            showImportPasswordDialog()
        }
    }

    private val viewLogLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // nothing to do
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enterFullScreen()

        fileManager = FileManager(this)
        settingsManager = SettingsManager(this)
        floatingWindowManager = FloatingWindowManager(this, binding.floatingContainer)
        backupManager = BackupManager(this)
        currentDir = fileManager.vaultDir

        setupRecyclerView()
        setupFabs()
        setupNavigationBar()
        loadFiles()
    }

    private fun enterFullScreen() {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupNavigationBar() {
        binding.btnBack.setOnClickListener {
            navigateUp()
        }
    }

    private fun navigateUp() {
        currentDir = currentDir?.parentFile
        if (currentDir == null || currentDir!!.absolutePath == fileManager.vaultDir.absolutePath) {
            currentDir = fileManager.vaultDir
            updateNavBarVisibility()
        }
        if (currentDir!!.absolutePath.startsWith(fileManager.vaultDir.absolutePath)) {
            updateNavBarVisibility()
            adapter.exitSelectionMode()
            loadFiles()
        } else {
            currentDir = fileManager.vaultDir
            updateNavBarVisibility()
            adapter.exitSelectionMode()
            loadFiles()
        }
    }

    private fun updateNavBarVisibility() {
        if (isAtRoot) {
            binding.layoutNavBar.visibility = View.GONE
        } else {
            binding.layoutNavBar.visibility = View.VISIBLE
            val relativePath = currentDir!!.absolutePath.removePrefix(fileManager.vaultDir.absolutePath)
                .removePrefix("/").ifEmpty { "/" }
            binding.tvCurrentPath.text = relativePath
        }
    }

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            onFileClick = { item -> onFileClick(item) },
            onFolderClick = { item -> navigateToFolder(item) },
            onItemLongClick = { item -> showItemActions(item) },
            onSelectionChanged = { count -> onSelectionCountChanged(count) }
        )

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter
    }

    private fun setupFabs() {
        binding.fabImport.setOnClickListener { showImportDialog() }
        binding.fabMenu.setOnClickListener { showMenuDialog() }
        binding.fabDelete.setOnClickListener { deleteSelectedFiles() }
        binding.fabRename.setOnClickListener { renameSelectedFile() }
        binding.fabMove.setOnClickListener { moveSelectedFiles() }
    }

    private fun onSelectionCountChanged(count: Int) {
        if (count > 0) {
            binding.chipSelectionCount.visibility = View.VISIBLE
            binding.chipSelectionCount.text = getString(R.string.selected_count, count)
            binding.fabImport.visibility = View.GONE
            binding.fabMenu.visibility = View.GONE

            if (count == 1) {
                binding.fabRename.visibility = View.VISIBLE
            } else {
                binding.fabRename.visibility = View.GONE
            }
            binding.fabMove.visibility = View.VISIBLE
            binding.fabDelete.visibility = View.VISIBLE
        } else {
            binding.chipSelectionCount.visibility = View.GONE
            binding.fabImport.visibility = View.VISIBLE
            binding.fabMenu.visibility = View.VISIBLE
            binding.fabRename.visibility = View.GONE
            binding.fabMove.visibility = View.GONE
            binding.fabDelete.visibility = View.GONE
        }
    }

    private fun navigateToFolder(item: FileItem) {
        currentDir = File(item.path)
        updateNavBarVisibility()
        loadFiles()
    }

    private fun showItemActions(item: FileItem) {
        val items = mutableListOf(
            getString(R.string.rename),
            getString(R.string.move)
        )
        if (!item.isDirectory) {
            items.add(getString(R.string.delete_selected))
        } else {
            items.add(getString(R.string.delete_selected))
        }
        items.add(getString(R.string.multi_select))
        items.add(getString(R.string.share))

        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    getString(R.string.rename) -> showRenameDialog(item)
                    getString(R.string.move) -> {
                        val paths = setOf(item.path)
                        showFolderPicker(paths) { targetPath ->
                            moveFiles(listOf(item.path), targetPath)
                        }
                    }
                    getString(R.string.delete_selected) -> confirmAndMoveToTrash(listOf(item.path))
                    getString(R.string.multi_select) -> {
                        val position = adapter.currentList.indexOfFirst { it.path == item.path }
                        if (position >= 0) {
                            adapter.enterSelectionMode(position)
                        }
                    }
                    getString(R.string.share) -> {
                        shareFile(item)
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(item: FileItem) {
        val input = EditText(this).apply {
            setText(item.name)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == item.name) return@setPositiveButton
                if (newName.contains("/") || newName.contains("\\")) {
                    Toast.makeText(this, "文件名不能包含 / 或 \\", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val ok = fileManager.renameFile(item.path, newName)
                if (ok) {
                    Toast.makeText(this, R.string.rename_success, Toast.LENGTH_SHORT).show()
                    loadFiles()
                } else {
                    Toast.makeText(this, R.string.rename_exists, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renameSelectedFile() {
        val selected = adapter.getSelectedItems()
        if (selected.size != 1) return
        showRenameDialog(selected.first())
        adapter.exitSelectionMode()
    }

    private fun moveSelectedFiles() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return
        val paths = selected.map { it.path }.toSet()
        showFolderPicker(paths) { targetPath ->
            moveFiles(selected.map { it.path }, targetPath)
            adapter.exitSelectionMode()
        }
    }

    private fun moveFiles(paths: List<String>, targetDirPath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val count = fileManager.moveFiles(paths, targetDirPath)
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    Toast.makeText(this@VaultActivity, getString(R.string.move_success, count), Toast.LENGTH_SHORT).show()
                }
                loadFiles()
            }
        }
    }

    private fun showFolderPicker(excludePaths: Set<String>, onFolderSelected: (String) -> Unit) {
        val dirs = mutableListOf(getString(R.string.move_root))
        dirs.addAll(
            fileManager.listAllDirs().map { dirPath ->
                dirPath.removePrefix(fileManager.vaultDir.absolutePath).removePrefix("/").ifEmpty { "/" }
            }
        )

        val dirAbsolutePaths = listOf(fileManager.vaultDir.absolutePath) + fileManager.listAllDirs()

        val filteredIndices = mutableListOf<Int>()
        val filteredDirs = mutableListOf<String>()

        for (i in dirs.indices) {
            val absPath = dirAbsolutePaths[i]
            if (absPath !in excludePaths) {
                filteredDirs.add(dirs[i])
                filteredIndices.add(i)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.move_select_folder)
            .setItems(filteredDirs.toTypedArray()) { _, which ->
                val targetPath = dirAbsolutePaths[filteredIndices[which]]
                onFolderSelected(targetPath)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showMenuDialog() {
        val items = arrayOf(
            getString(R.string.new_folder),
            getString(R.string.trash),
            getString(R.string.backup_export),
            getString(R.string.backup_import),
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_date),
            getString(R.string.sort_by_size),
            getString(R.string.sort_by_type),
            getString(R.string.tile_horizontal),
            getString(R.string.tile_vertical),
            getString(R.string.tile_grid),
            getString(R.string.settings),
            getString(R.string.clear_cache),
            getString(R.string.view_ffmpeg_log),
            getString(R.string.exit_app)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.menu)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showCreateFolderDialog()
                    1 -> openTrash()
                    2 -> showExportPasswordDialog()
                    3 -> openBackupImport()
                    4 -> toggleSort(SortMode.NAME_ASC, SortMode.NAME_DESC)
                    5 -> toggleSort(SortMode.DATE_ASC, SortMode.DATE_DESC)
                    6 -> toggleSort(SortMode.SIZE_ASC, SortMode.SIZE_DESC)
                    7 -> toggleSort(SortMode.TYPE_ASC, SortMode.TYPE_DESC)
                    8 -> floatingWindowManager.tileHorizontal()
                    9 -> floatingWindowManager.tileVertical()
                    10 -> floatingWindowManager.tileGrid()
                    11 -> showSettingsDialog()
                    12 -> clearGlideCache()
                    13 -> viewFFmpegLog()
                    14 -> showExitDialog()
                }
            }
            .show()
    }

    private fun viewFFmpegLog() {
        val logFile = FFmpegLogger.getLogFile()
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(this, "日志文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", logFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewLogLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开日志文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.folder_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_folder)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (name.contains("/") || name.contains("\\")) {
                    Toast.makeText(this, "文件夹名不能包含 / 或 \\", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val ok = fileManager.createFolder(currentDir!!.absolutePath, name)
                if (ok) {
                    loadFiles()
                } else {
                    Toast.makeText(this, R.string.folder_exists, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExportPasswordDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_export)
            .setMessage(R.string.backup_export_desc)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val password = input.text.toString().trim()
                if (password.isEmpty()) {
                    Toast.makeText(this, R.string.backup_password_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                startExport(password)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startExport(password: String) {
        val helper = ProgressDialogHelper(this)
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                helper.show(getString(R.string.backup_exporting), 100)
            }
            val success = backupManager.exportBackup(fileManager.vaultDir, password) { progress ->
                lifecycleScope.launch(Dispatchers.Main) {
                    helper.updateProgress(progress.overallPercent,
                        "${progress.phase} ${progress.currentFile}")
                }
            }
            withContext(Dispatchers.Main) {
                helper.dismiss()
                if (success) {
                    Toast.makeText(this@VaultActivity, R.string.backup_export_success, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@VaultActivity, R.string.backup_export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openBackupImport() {
        backupImportLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }

    private fun showImportPasswordDialog() {
        val uri = backupImportUri ?: return
        val input = EditText(this).apply {
            hint = getString(R.string.backup_password_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_import)
            .setMessage(R.string.backup_import_desc)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val password = input.text.toString().trim()
                if (password.isEmpty()) {
                    Toast.makeText(this, R.string.backup_password_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                startImport(uri, password)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startImport(uri: Uri, password: String) {
        val helper = ProgressDialogHelper(this)
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                helper.show(getString(R.string.backup_importing), 100)
            }
            val result = backupManager.importBackup(uri, fileManager.vaultDir, password) { progress ->
                lifecycleScope.launch(Dispatchers.Main) {
                    helper.updateProgress(progress.overallPercent,
                        "${progress.phase} ${progress.currentEntry}")
                }
            }
            withContext(Dispatchers.Main) {
                helper.dismiss()
                when {
                    result < 0 -> Toast.makeText(this@VaultActivity, R.string.backup_import_password_error, Toast.LENGTH_SHORT).show()
                    result > 0 -> {
                        Toast.makeText(this@VaultActivity, getString(R.string.backup_import_success, result), Toast.LENGTH_SHORT).show()
                        loadFiles()
                    }
                    else -> Toast.makeText(this@VaultActivity, R.string.backup_import_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        backupImportUri = null
    }

    private fun openTrash() {
        val intent = Intent(this, TrashActivity::class.java)
        trashActivityLauncher.launch(intent)
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
        if (uris.isEmpty()) return
        val helper = ProgressDialogHelper(this)
        val total = uris.size
        val mainHandler = Handler(Looper.getMainLooper())
        val targetDir = currentDir ?: fileManager.vaultDir

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                helper.show(getString(R.string.importing_files), 100)
            }
            var successCount = 0
            for (uri in uris) {
                withContext(Dispatchers.Main) {
                    helper.updateProgress(0, "$successCount / $total")
                }
                val ok = fileManager.copyToVault(uri, targetDir = targetDir) { progress ->
                    mainHandler.post {
                        helper.updateProgress(progress, "$successCount / $total")
                    }
                }
                if (ok) successCount++
                withContext(Dispatchers.Main) {
                    helper.updateProgress(100, "$successCount / $total")
                }
            }
            withContext(Dispatchers.Main) {
                helper.dismiss()
                if (successCount > 0) {
                    Toast.makeText(this@VaultActivity, "成功导入 $successCount 个文件", Toast.LENGTH_SHORT).show()
                }
                loadFiles()
            }
        }
    }

    private fun loadFiles() {
        val dir = currentDir ?: fileManager.vaultDir
        lifecycleScope.launch(Dispatchers.IO) {
            val files = fileManager.listFiles(directory = dir, sortMode = currentSortMode)
            withContext(Dispatchers.Main) {
                adapter.submitList(files)
                updateEmptyView(files.isEmpty())
            }
        }
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

    private fun shareFile(item: FileItem) {
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
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, getString(R.string.share_file))
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "无法分享文件: ${e.message}", Toast.LENGTH_SHORT).show()
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
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val biometricSwitch = SwitchCompat(this).apply {
            text = getString(R.string.biometric_toggle)
            isChecked = settingsManager.isBiometricEnabled
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.isBiometricEnabled = isChecked
            }
            setPadding(0, 10, 0, 10)
        }
        container.addView(biometricSwitch)

        val ffmpegSwitch = SwitchCompat(this).apply {
            text = getString(R.string.ffmpeg_toggle)
            isChecked = settingsManager.useFFmpeg
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.useFFmpeg = isChecked
            }
            setPadding(0, 10, 0, 10)
        }
        container.addView(ffmpegSwitch)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(container)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun clearGlideCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            Glide.get(this@VaultActivity).clearDiskCache()
            File(cacheDir, "video_frame_cache").deleteRecursively()
            withContext(Dispatchers.Main) {
                Glide.get(this@VaultActivity).clearMemory()
                Toast.makeText(this@VaultActivity, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteSelectedFiles() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) return

        confirmAndMoveToTrash(selectedItems.map { it.path })
        adapter.exitSelectionMode()
    }

    private fun confirmAndMoveToTrash(paths: List<String>) {
        if (paths.isEmpty()) return

        val names = paths.map { File(it).name }
        val displayNames = if (names.size <= 3) {
            names.joinToString("\n")
        } else {
            names.take(3).joinToString("\n") + "\n...等共 ${names.size} 个文件"
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_trash_message, displayNames))
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val count = fileManager.moveToTrash(paths)
                    withContext(Dispatchers.Main) {
                        if (count > 0) {
                            Toast.makeText(this@VaultActivity, R.string.trash_move_success, Toast.LENGTH_SHORT).show()
                        }
                        loadFiles()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onBackPressed() {
        if (adapter.isSelectionMode) {
            adapter.exitSelectionMode()
            return
        }

        if (!isAtRoot) {
            navigateUp()
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

    override fun onResume() {
        super.onResume()
        loadFiles()
        ensureTaskFloatingWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingWindowManager.closeAll()
        taskFloatingWindow?.destroy()
        taskFloatingWindow = null
    }

    override fun onAppLockCleanup() {
        floatingWindowManager.closeAll()
        taskFloatingWindow?.destroy()
        taskFloatingWindow = null
    }

    private fun ensureTaskFloatingWindow() {
        if (taskFloatingWindow == null) {
            taskFloatingWindow = TaskFloatingWindow(this)
            taskFloatingWindow?.attachTo(binding.floatTask)
        }
        taskFloatingWindow?.show()
    }
}
