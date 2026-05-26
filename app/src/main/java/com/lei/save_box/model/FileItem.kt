package com.lei.save_box.model

import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String
) {
    companion object {
        fun fromFile(file: File): FileItem {
            return FileItem(
                name = file.name,
                path = file.absolutePath,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                extension = if (file.isFile) {
                    file.name.substringAfterLast('.', "").lowercase()
                } else {
                    ""
                }
            )
        }
    }

    val formattedSize: String
        get() {
            if (isDirectory) return ""
            return when {
                size < 1024 -> "${size} B"
                size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
                size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024))} MB"
                else -> "${"%.1f".format(size / (1024.0 * 1024 * 1024))} GB"
            }
        }

    val isImage: Boolean
        get() = extension in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")

    val isVideo: Boolean
        get() = extension in setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm")

    val isAudio: Boolean
        get() = extension in setOf("mp3", "wav", "aac", "flac", "ogg", "wma", "m4a")

    val isDocument: Boolean
        get() = extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")
}
