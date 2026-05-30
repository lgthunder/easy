package com.lei.save_box.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lei.save_box.databinding.ItemTrashBinding
import com.lei.save_box.model.TrashItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrashAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<TrashItem, TrashAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    var isSelectionMode = false
        private set

    private val selectedPositions = mutableSetOf<Int>()

    fun enterSelectionMode(position: Int) {
        isSelectionMode = true
        selectedPositions.add(position)
        notifyDataSetChanged()
        onSelectionChanged(selectedPositions.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
            if (selectedPositions.isEmpty()) {
                exitSelectionMode()
            }
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged(selectedPositions.size)
    }

    fun getSelectedItems(): List<TrashItem> {
        return selectedPositions.mapNotNull { position ->
            if (position in 0 until itemCount) getItem(position) else null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, isSelectionMode, selectedPositions.contains(position))
    }

    inner class ViewHolder(private val binding: ItemTrashBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TrashItem, position: Int, selectionMode: Boolean, isSelected: Boolean) {
            binding.tvFileName.text = item.originalName
            binding.tvFileSize.text = item.formattedSize
            binding.tvFileDate.text = dateFormat.format(Date(item.deletedAt))
            binding.tvOriginalPath.text = item.originalPath

            if (selectionMode) {
                binding.cbSelect.visibility = android.view.View.VISIBLE
                binding.cbSelect.isChecked = isSelected
            } else {
                binding.cbSelect.visibility = android.view.View.GONE
                binding.cbSelect.isChecked = false
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode(bindingAdapterPosition)
                }
                true
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(bindingAdapterPosition)
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<TrashItem>() {
        override fun areItemsTheSame(oldItem: TrashItem, newItem: TrashItem): Boolean {
            return oldItem.trashFileName == newItem.trashFileName
        }

        override fun areContentsTheSame(oldItem: TrashItem, newItem: TrashItem): Boolean {
            return oldItem == newItem
        }
    }
}
