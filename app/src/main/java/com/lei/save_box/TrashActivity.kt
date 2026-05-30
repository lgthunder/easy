package com.lei.save_box

import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lei.save_box.adapter.TrashAdapter
import com.lei.save_box.databinding.ActivityTrashBinding
import com.lei.save_box.manager.FileManager
import com.lei.save_box.model.TrashItem
import com.lei.save_box.view.ProgressDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private lateinit var fileManager: FileManager
    private lateinit var adapter: TrashAdapter
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enterFullScreen()

        fileManager = FileManager(this)

        setupRecyclerView()
        setupButtons()
        loadTrash()
    }

    private fun enterFullScreen() {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupRecyclerView() {
        adapter = TrashAdapter(
            onSelectionChanged = { count -> onSelectionCountChanged(count) }
        )
        binding.rvTrash.layoutManager = LinearLayoutManager(this)
        binding.rvTrash.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnRestore.setOnClickListener {
            restoreSelected()
        }

        binding.btnPermanentDelete.setOnClickListener {
            permanentlyDeleteSelected()
        }

        binding.btnEmptyTrash.setOnClickListener {
            confirmEmptyTrash()
        }
    }

    private fun onSelectionCountChanged(count: Int) {
        if (count > 0) {
            binding.chipSelectionCount.visibility = View.VISIBLE
            binding.chipSelectionCount.text = getString(R.string.selected_count, count)
            binding.btnRestore.visibility = View.VISIBLE
            binding.btnPermanentDelete.visibility = View.VISIBLE
        } else {
            binding.chipSelectionCount.visibility = View.GONE
            binding.btnRestore.visibility = View.GONE
            binding.btnPermanentDelete.visibility = View.GONE
        }
    }

    private fun loadTrash() {
        lifecycleScope.launch(Dispatchers.IO) {
            val items = fileManager.listTrash()
            withContext(Dispatchers.Main) {
                adapter.submitList(items)
                updateEmptyView(items.isEmpty())
            }
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvTrash.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.btnEmptyTrash.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun restoreSelected() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        val helper = ProgressDialogHelper(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val fileNames = selected.map { it.trashFileName }
            val count = fileManager.restoreFromTrash(fileNames)
            withContext(Dispatchers.Main) {
                helper.dismiss()
                if (count > 0) {
                    Toast.makeText(this@TrashActivity, getString(R.string.restore_success, count), Toast.LENGTH_SHORT).show()
                }
                adapter.exitSelectionMode()
                loadTrash()
            }
        }
    }

    private fun permanentlyDeleteSelected() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.permanent_delete)
            .setMessage(R.string.confirm_permanent_delete)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val helper = ProgressDialogHelper(this@TrashActivity)
                lifecycleScope.launch(Dispatchers.IO) {
                    val fileNames = selected.map { it.trashFileName }
                    val count = fileManager.permanentlyDeleteFromTrash(fileNames)
                    withContext(Dispatchers.Main) {
                        helper.dismiss()
                        if (count > 0) {
                            Toast.makeText(this@TrashActivity, "已彻底删除 $count 个文件", Toast.LENGTH_SHORT).show()
                        }
                        adapter.exitSelectionMode()
                        loadTrash()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmEmptyTrash() {
        AlertDialog.Builder(this)
            .setTitle(R.string.empty_trash)
            .setMessage(R.string.confirm_empty_trash)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val helper = ProgressDialogHelper(this@TrashActivity)
                lifecycleScope.launch(Dispatchers.IO) {
                    val count = fileManager.emptyTrash()
                    withContext(Dispatchers.Main) {
                        helper.dismiss()
                        if (count > 0) {
                            Toast.makeText(this@TrashActivity, "已清空回收站", Toast.LENGTH_SHORT).show()
                        }
                        loadTrash()
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
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterFullScreen()
        }
    }
}
