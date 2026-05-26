package com.lei.save_box.manager

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.lei.save_box.model.FileItem
import java.io.File
import java.io.FileOutputStream

enum class SortMode {
    NAME_ASC,
    NAME_DESC,
    DATE_ASC,
    DATE_DESC,
    SIZE_ASC,
    SIZE_DESC,
    TYPE_ASC,
    TYPE_DESC
}

class FileManager(private val context: Context) {

    companion object {
        const val VAULT_DIR = "vault"
        const val NOMEDIA_FILE = ".nomedia"
    }

    val vaultDir: File
        get() {
            val dir = File(context.filesDir, VAULT_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun ensureNomedia() {
        val nomedia = File(vaultDir, NOMEDIA_FILE)
        if (!nomedia.exists()) {
            nomedia.createNewFile()
        }
        val nomediaRoot = File(context.filesDir, NOMEDIA_FILE)
        if (!nomediaRoot.exists()) {
            nomediaRoot.createNewFile()
        }
    }

    fun copyToVault(uri: Uri, originalFileName: String? = null, onProgress: ((Int) -> Unit)? = null): Boolean {
        ensureNomedia()
        return try {
            var fileName = originalFileName ?: getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
            var destFile = File(vaultDir, fileName)
            var counter = 1
            val baseName = fileName.substringBeforeLast('.', "")
            val ext = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ""

            while (destFile.exists()) {
                fileName = "${baseName}_${counter}${ext}"
                destFile = File(vaultDir, fileName)
                counter++
            }

            val totalSize = getFileSizeFromUri(uri)
            var bytesCopied = 0L

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        if (totalSize > 0) {
                            onProgress?.invoke(((bytesCopied * 100) / totalSize).toInt())
                        }
                    }
                }
            }

            onProgress?.invoke(100)
            destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteFiles(paths: List<String>): Int {
        var deletedCount = 0
        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.delete()) {
                deletedCount++
            } else if (file.exists() && file.isDirectory) {
                if (file.deleteRecursively()) {
                    deletedCount++
                }
            }
        }
        return deletedCount
    }

    fun listFiles(sortMode: SortMode = SortMode.DATE_DESC): List<FileItem> {
        ensureNomedia()
        val files = vaultDir.listFiles()?.filter { it.name != NOMEDIA_FILE } ?: emptyList()

        return files
            .sortedWith(getComparator(sortMode))
            .map { FileItem.fromFile(it) }
    }

    private fun getComparator(sortMode: SortMode): Comparator<File> {
        return when (sortMode) {
            SortMode.NAME_ASC -> compareBy { it.name.lowercase() }
            SortMode.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> compareBy { it.lastModified() }
            SortMode.DATE_DESC -> compareByDescending { it.lastModified() }
            SortMode.SIZE_ASC -> compareBy { it.length() }
            SortMode.SIZE_DESC -> compareByDescending { it.length() }
            SortMode.TYPE_ASC -> compareBy<File> { it.name.substringAfterLast('.', "").lowercase() }
                .thenBy { it.name.lowercase() }
            SortMode.TYPE_DESC -> compareByDescending<File> { it.name.substringAfterLast('.', "").lowercase() }
                .thenByDescending { it.name.lowercase() }
        }
    }

    fun getMimeType(file: File): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.name)
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        } else {
            "*/*"
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }
}
