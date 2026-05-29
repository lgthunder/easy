package com.lei.save_box.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lei.save_box.databinding.ItemFileBinding
import com.lei.save_box.glide.VideoThumbnail
import com.lei.save_box.model.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val onItemClick: (FileItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<FileItem, FileAdapter.ViewHolder>(DiffCallback) {

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

    fun getSelectedItems(): List<FileItem> {
        return selectedPositions.mapNotNull { position ->
            if (position in 0 until itemCount) getItem(position) else null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, isSelectionMode, selectedPositions.contains(position))
    }

    inner class ViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileItem, position: Int, selectionMode: Boolean, isSelected: Boolean) {
            binding.tvFileName.text = item.name
            binding.tvFileSize.text = item.formattedSize
            binding.tvFileDate.text = dateFormat.format(Date(item.lastModified))

            val file = File(item.path)
            when {
                item.isImage -> {
                    Glide.with(binding.ivFileIcon.context)
                        .load(file)
                        .override(96, 96)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.ivFileIcon)
                }
                item.isVideo -> {
                    Glide.with(binding.ivFileIcon.context)
                        .asBitmap()
                        .load(VideoThumbnail(item.path,0))
                        .override(96, 96)
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_media_play)
                        .error(android.R.drawable.ic_media_play)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.ivFileIcon)
                }
                else -> {
                    val iconRes = when {
                        item.isAudio -> android.R.drawable.ic_media_play
                        item.isDocument -> android.R.drawable.ic_menu_edit
                        else -> android.R.drawable.ic_menu_save
                    }
                    binding.ivFileIcon.setImageResource(iconRes)
                }
            }

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
                } else {
                    onItemClick(item)
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem == newItem
        }
    }
}