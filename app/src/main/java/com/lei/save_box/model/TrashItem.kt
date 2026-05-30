package com.lei.save_box.model

data class TrashItem(
    val originalPath: String,
    val originalName: String,
    val trashFileName: String,
    val deletedAt: Long,
    val size: Long
) {
    val formattedSize: String
        get() {
            if (size <= 0) return ""
            return when {
                size < 1024 -> "${size} B"
                size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
                size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024))} MB"
                else -> "${"%.1f".format(size / (1024.0 * 1024 * 1024))} GB"
            }
        }
}
