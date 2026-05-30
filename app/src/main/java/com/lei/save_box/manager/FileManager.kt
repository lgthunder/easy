package com.lei.save_box.manager

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.lei.save_box.model.FileItem
import com.lei.save_box.model.TrashItem
import org.json.JSONArray
import org.json.JSONObject
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
        const val TRASH_DIR = ".trash"
        const val TRASH_META_FILE = ".trash_meta.json"
    }

    val vaultDir: File
        get() {
            val dir = File(context.filesDir, VAULT_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getTrashDir(): File {
        val dir = File(vaultDir, TRASH_DIR)
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

    fun copyToVault(
        uri: Uri,
        targetDir: File = vaultDir,
        originalFileName: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean {
        ensureNomedia()
        return try {
            var fileName = originalFileName ?: getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
            var destFile = File(targetDir, fileName)
            var counter = 1
            val baseName = fileName.substringBeforeLast('.', "")
            val ext = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ""

            while (destFile.exists()) {
                fileName = "${baseName}_${counter}${ext}"
                destFile = File(targetDir, fileName)
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

    fun listFiles(
        directory: File = vaultDir,
        sortMode: SortMode = SortMode.DATE_DESC
    ): List<FileItem> {
        ensureNomedia()
        val files = directory.listFiles()?.filter {
            it.name != NOMEDIA_FILE && it.name != TRASH_DIR
        } ?: emptyList()

        return files
            .sortedWith(getComparator(sortMode))
            .map { FileItem.fromFile(it) }
    }

    fun createFolder(parentPath: String, folderName: String): Boolean {
        val dir = File(parentPath, folderName)
        if (dir.exists()) return false
        return dir.mkdirs()
    }

    fun renameFile(oldPath: String, newName: String): Boolean {
        val oldFile = File(oldPath)
        if (!oldFile.exists()) return false
        val parentDir = oldFile.parentFile ?: return false
        val newFile = File(parentDir, newName)
        if (newFile.exists()) return false
        return oldFile.renameTo(newFile)
    }

    fun moveFiles(paths: List<String>, targetDirPath: String): Int {
        var count = 0
        val targetDir = File(targetDirPath)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        for (path in paths) {
            val src = File(path)
            if (!src.exists()) continue
            val dest = resolveNameConflict(targetDir, src.name)
            if (src.renameTo(dest)) {
                count++
            }
        }
        return count
    }

    private fun resolveNameConflict(targetDir: File, name: String): File {
        var dest = File(targetDir, name)
        if (!dest.exists()) return dest
        val baseName = name.substringBeforeLast('.', "")
        val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while (dest.exists()) {
            dest = File(targetDir, "${baseName}_${counter}${ext}")
            counter++
        }
        return dest
    }

    fun moveToTrash(paths: List<String>): Int {
        val trashDir = getTrashDir()
        val meta = loadTrashMeta()
        var count = 0

        for (path in paths) {
            val src = File(path)
            if (!src.exists()) continue

            val trashFileName = "${System.currentTimeMillis()}_${src.name}"
            val dest = File(trashDir, trashFileName)
            if (!src.renameTo(dest)) continue

            meta.add(
                TrashItem(
                    originalPath = path,
                    originalName = src.name,
                    trashFileName = trashFileName,
                    deletedAt = System.currentTimeMillis(),
                    size = dest.length()
                )
            )
            count++
        }

        saveTrashMeta(meta)
        return count
    }

    fun restoreFromTrash(trashFileNames: List<String>): Int {
        val trashDir = getTrashDir()
        val meta = loadTrashMeta()
        var count = 0
        val restoredItems = mutableListOf<TrashItem>()

        for (fileName in trashFileNames) {
            val item = meta.find { it.trashFileName == fileName } ?: continue
            val src = File(trashDir, fileName)
            if (!src.exists()) {
                restoredItems.add(item)
                continue
            }

            val originalParent = File(item.originalPath).parentFile
            if (originalParent != null && !originalParent.exists()) {
                originalParent.mkdirs()
            }

            val dest = resolveNameConflict(
                originalParent ?: vaultDir,
                item.originalName
            )

            if (src.renameTo(dest)) {
                restoredItems.add(item)
                count++
            }
        }

        meta.removeAll(restoredItems)
        saveTrashMeta(meta)
        return count
    }

    fun permanentlyDeleteFromTrash(trashFileNames: List<String>): Int {
        val trashDir = getTrashDir()
        val meta = loadTrashMeta()
        var count = 0
        val deletedItems = mutableListOf<TrashItem>()

        for (fileName in trashFileNames) {
            val item = meta.find { it.trashFileName == fileName } ?: continue
            val file = File(trashDir, fileName)
            if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            deletedItems.add(item)
            count++
        }

        meta.removeAll(deletedItems)
        saveTrashMeta(meta)
        return count
    }

    fun emptyTrash(): Int {
        val trashDir = getTrashDir()
        val files = trashDir.listFiles()?.filter { it.name != TRASH_META_FILE } ?: emptyList()
        var count = 0
        for (file in files) {
            if (file.isDirectory) {
                if (file.deleteRecursively()) count++
            } else {
                if (file.delete()) count++
            }
        }
        saveTrashMeta(emptyList())
        return count
    }

    fun listTrash(): List<TrashItem> {
        return loadTrashMeta().sortedByDescending { it.deletedAt }
    }

    private fun loadTrashMeta(): MutableList<TrashItem> {
        val metaFile = File(getTrashDir(), TRASH_META_FILE)
        if (!metaFile.exists()) return mutableListOf()

        return try {
            val json = metaFile.readText()
            val array = JSONArray(json)
            val items = mutableListOf<TrashItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    TrashItem(
                        originalPath = obj.getString("originalPath"),
                        originalName = obj.getString("originalName"),
                        trashFileName = obj.getString("trashFileName"),
                        deletedAt = obj.getLong("deletedAt"),
                        size = obj.optLong("size", 0)
                    )
                )
            }
            items
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveTrashMeta(items: List<TrashItem>) {
        val metaFile = File(getTrashDir(), TRASH_META_FILE)
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("originalPath", item.originalPath)
            obj.put("originalName", item.originalName)
            obj.put("trashFileName", item.trashFileName)
            obj.put("deletedAt", item.deletedAt)
            obj.put("size", item.size)
            array.put(obj)
        }
        metaFile.writeText(array.toString())
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

    fun listAllDirs(root: File = vaultDir): List<String> {
        val dirs = mutableListOf<String>()
        root.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name != TRASH_DIR) {
                dirs.add(file.absolutePath)
                dirs.addAll(listAllDirs(file))
            }
        }
        return dirs
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
