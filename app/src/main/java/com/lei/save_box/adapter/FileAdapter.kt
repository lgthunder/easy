package com.lei.save_box.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.signature.ObjectKey
import com.lei.save_box.databinding.ItemFileBinding
import com.lei.save_box.model.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class FileAdapter(
    private val onItemClick: (FileItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<FileItem, FileAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val thumbnailExecutor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
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
                    binding.ivFileIcon.setImageResource(android.R.drawable.ic_media_play)
                    loadVideoThumbnail(item.path)
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

        private fun loadVideoThumbnail(filePath: String) {
            val targetPath = filePath
            thumbnailExecutor.execute {
                val cachedBitmap = getCachedThumbnail(targetPath)
                if (cachedBitmap != null) {
                    Log.d("leiting","缓存命中 $filePath")
                    updateThumbnail(cachedBitmap, targetPath)
                    return@execute
                }

                val retriever = MediaMetadataRetriever()
                try {
                    Log.d("leiting","缓存未命中  retriever  $filePath")
                    retriever.setDataSource(targetPath)
                    val embedded = retriever.embeddedPicture
                    val rawBitmap = if (embedded != null) {
                        BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                    } else {
                        extractNonBlackFrame(retriever)
                    }
                    val result: Any = if (rawBitmap != null) {
                        val thumb = Bitmap.createScaledBitmap(rawBitmap, 128, 128, true)
                        if (thumb !== rawBitmap) rawBitmap.recycle()
                        cacheThumbnail(targetPath, thumb)
                        thumb
                    } else {
                        -1
                    }
                    updateThumbnail(result, targetPath)
                } catch (_: Exception) {
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        }

        private fun getCachedThumbnail(filePath: String): Bitmap? {
            return try {
                Glide.with(binding.ivFileIcon.context)
                    .asBitmap()
                    .load(File(filePath))
                    .signature(ObjectKey(filePath))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .submit()
                    .get()
            } catch (_: Exception) {
                null
            }
        }

        private fun cacheThumbnail(filePath: String, bitmap: Bitmap) {
            try {
                Glide.with(binding.ivFileIcon.context)
                    .asBitmap()
                    .load(bitmap)
                    .signature(ObjectKey(filePath))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload(128, 128)
            } catch (_: Exception) {
            }
        }

        private fun updateThumbnail(result: Any, targetPath: String) {
            mainHandler.post {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val currentItem = getItem(bindingAdapterPosition)
                    if (currentItem.path == targetPath) {
                        if (result is Bitmap) {
                            binding.ivFileIcon.setImageBitmap(result)
                        }
                    }
                }
            }
        }

        private fun extractNonBlackFrame(retriever: MediaMetadataRetriever): Bitmap? {
            val offsets = longArrayOf(1_000_000, 500_000, 2_000_000, 10_000_000, 0)
            for (offset in offsets) {
                val frame = retriever.getFrameAtTime(offset, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null && !isMostlyBlack(frame)) return frame
            }
            return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }

        private fun isMostlyBlack(bitmap: Bitmap): Boolean {
            val sampleSize = 10
            var darkPixels = 0
            var totalSamples = 0
            for (y in 0 until bitmap.height step sampleSize) {
                for (x in 0 until bitmap.width step sampleSize) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (r < 30 && g < 30 && b < 30) darkPixels++
                    totalSamples++
                }
            }
            return totalSamples > 0 && darkPixels.toFloat() / totalSamples > 0.8f
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