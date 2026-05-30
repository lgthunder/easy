package com.lei.save_box.manager

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(private val context: Context) {

    companion object {
        private const val SALT_LENGTH = 16
        private const val CTR_IV_LENGTH = 16
        private const val HMAC_LENGTH = 32
        private const val PBKDF2_ITERATIONS = 10000
        private const val BACKUP_EXTENSION = ".sav"
    }

    data class ExportProgress(
        val phase: String,
        val currentFile: String,
        val fileIndex: Int,
        val totalFiles: Int,
        val overallPercent: Int
    )

    data class ImportProgress(
        val phase: String,
        val currentEntry: String,
        val entryIndex: Int,
        val totalEntries: Int,
        val overallPercent: Int
    )

    fun collectVaultFiles(vaultDir: File): List<File> {
        val files = mutableListOf<File>()
        val trashDir = File(vaultDir, FileManager.TRASH_DIR)
        collectFilesRecursive(vaultDir, trashDir, files)
        return files
    }

    private fun collectFilesRecursive(dir: File, trashDir: File, result: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            if (file.absolutePath == trashDir.absolutePath) return@forEach
            if (file.name == FileManager.NOMEDIA_FILE) return@forEach
            if (file.isDirectory) {
                collectFilesRecursive(file, trashDir, result)
            } else {
                result.add(file)
            }
        }
    }

    fun exportBackup(
        vaultDir: File,
        password: String,
        onProgress: (ExportProgress) -> Unit
    ): Boolean {
        return try {
            val allFiles = collectVaultFiles(vaultDir)
            if (allFiles.isEmpty()) return false

            val tempZipFile = File(context.cacheDir, "backup_${System.currentTimeMillis()}.zip")
            val tempEncFile = File(context.cacheDir, "backup_${System.currentTimeMillis()}.enc")

            try {
                onProgress(ExportProgress("压缩中", "", 0, allFiles.size, 0))
                createZip(allFiles, vaultDir, tempZipFile) { index, name ->
                    val pct = (index * 50) / allFiles.size
                    onProgress(ExportProgress("压缩中", name, index, allFiles.size, pct))
                }

                onProgress(ExportProgress("加密中", "", allFiles.size, allFiles.size, 50))
                encryptFile(tempZipFile, tempEncFile, password) { pct ->
                    val overall = 50 + pct / 2
                    onProgress(ExportProgress("加密中", "", allFiles.size, allFiles.size, overall))
                }

                onProgress(ExportProgress("保存中", "", allFiles.size, allFiles.size, 100))
                saveToPublicDownloads(tempEncFile, "savebox_backup_${System.currentTimeMillis()}$BACKUP_EXTENSION")

                true
            } finally {
                tempZipFile.delete()
                tempEncFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun createZip(
        files: List<File>,
        vaultDir: File,
        destFile: File,
        onProgress: (Int, String) -> Unit
    ) {
        val vaultPath = vaultDir.absolutePath
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destFile))).use { zos ->
            files.forEachIndexed { index, file ->
                val entryName = file.absolutePath.removePrefix(vaultPath).removePrefix("/")
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
                onProgress(index + 1, entryName)
            }
        }
    }

    private fun encryptFile(
        srcFile: File,
        destFile: File,
        password: String,
        onProgress: (Int) -> Unit
    ) {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)

        val (aesKey, hmacKey) = deriveKeyMaterial(password, salt)

        val iv = ByteArray(CTR_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        mac.update(salt)
        mac.update(iv)

        val totalSize = srcFile.length()
        var bytesRead = 0L

        FileOutputStream(destFile).use { fos ->
            fos.write(salt)
            fos.write(iv)

            val inBuffer = ByteArray(65536)
            FileInputStream(srcFile).use { fis ->
                var len: Int
                while (fis.read(inBuffer).also { len = it } != -1) {
                    val encrypted = cipher.update(inBuffer, 0, len)
                    fos.write(encrypted)
                    mac.update(encrypted)
                    bytesRead += len
                    if (totalSize > 0) {
                        onProgress(((bytesRead * 100) / totalSize).toInt())
                    }
                }
            }

            val finalBytes = cipher.doFinal()
            if (finalBytes.isNotEmpty()) {
                fos.write(finalBytes)
                mac.update(finalBytes)
            }

            fos.write(mac.doFinal())
        }
    }

    private fun saveToPublicDownloads(encFile: File, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("无法创建下载文件")

        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(encFile).use { input ->
                input.copyTo(output)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    fun importBackup(
        uri: Uri,
        vaultDir: File,
        password: String,
        onProgress: (ImportProgress) -> Unit
    ): Int {
        return try {
            val tempEncFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.enc")
            val tempZipFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")

            try {
                onProgress(ImportProgress("读取中", "", 0, 0, 0))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempEncFile).use { output ->
                        input.copyTo(output)
                    }
                }

                try {
                    onProgress(ImportProgress("解密中", "", 0, 0, 30))
                    decryptFile(tempEncFile, tempZipFile, password) { pct ->
                        val overall = 30 + pct / 5
                        onProgress(ImportProgress("解密中", "", 0, 0, overall))
                    }
                } catch (e: Exception) {
                    return -1
                }

                onProgress(ImportProgress("解压中", "", 0, 0, 50))
                val count = restoreFromZip(tempZipFile, vaultDir) { index, name, total ->
                    val pct = 50 + (index * 50) / total
                    onProgress(ImportProgress("解压中", name, index, total, pct))
                }

                count
            } finally {
                tempEncFile.delete()
                tempZipFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun decryptFile(
        srcFile: File,
        destFile: File,
        password: String,
        onProgress: (Int) -> Unit
    ) {
        val fileSize = srcFile.length()
        val ciphertextSize = fileSize - SALT_LENGTH - CTR_IV_LENGTH - HMAC_LENGTH
        if (ciphertextSize < 0) throw IllegalArgumentException("Invalid backup file")

        FileInputStream(srcFile).use { fis ->
            val salt = ByteArray(SALT_LENGTH)
            fis.read(salt)

            val iv = ByteArray(CTR_IV_LENGTH)
            fis.read(iv)

            val (aesKey, hmacKey) = deriveKeyMaterial(password, salt)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
            mac.update(salt)
            mac.update(iv)

            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))

            var ciphertextRead = 0L
            val inBuffer = ByteArray(65536)

            FileOutputStream(destFile).use { fos ->
                while (ciphertextRead < ciphertextSize) {
                    val toRead = minOf(inBuffer.size.toLong(), ciphertextSize - ciphertextRead).toInt()
                    val len = fis.read(inBuffer, 0, toRead)
                    if (len < 0) break

                    mac.update(inBuffer, 0, len)

                    val decrypted = cipher.update(inBuffer, 0, len)
                    if (decrypted.isNotEmpty()) {
                        fos.write(decrypted)
                    }

                    ciphertextRead += len
                    if (ciphertextSize > 0) {
                        onProgress(((ciphertextRead * 100) / ciphertextSize).toInt())
                    }
                }

                val finalBytes = cipher.doFinal()
                if (finalBytes.isNotEmpty()) {
                    fos.write(finalBytes)
                }
            }

            val storedHmac = ByteArray(HMAC_LENGTH)
            fis.read(storedHmac)

            val computedHmac = mac.doFinal()
            if (!computedHmac.contentEquals(storedHmac)) {
                destFile.delete()
                throw javax.crypto.AEADBadTagException("HMAC verification failed")
            }
        }
    }

    private fun restoreFromZip(
        zipFile: File,
        vaultDir: File,
        onProgress: (Int, String, Int) -> Unit
    ): Int {
        var count = 0
        val entries = mutableListOf<String>()

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries.add(entry.name)
                }
                entry = zis.nextEntry
            }
        }

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            var index = 0
            while (entry != null) {
                if (!entry.isDirectory) {
                    val destFile = resolveRestorePath(vaultDir, entry.name)
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    count++
                    index++
                    onProgress(index, entry.name, entries.size)
                }
                entry = zis.nextEntry
            }
        }

        return count
    }

    private fun resolveRestorePath(vaultDir: File, entryName: String): File {
        var dest = File(vaultDir, entryName)
        if (!dest.exists()) return dest

        val baseName = entryName.substringBeforeLast('.', "")
        val ext = if (entryName.contains('.')) ".${entryName.substringAfterLast('.')}" else ""
        var counter = 1
        while (dest.exists()) {
            dest = File(vaultDir, "${baseName}_${counter}${ext}")
            counter++
        }
        return dest
    }

    private fun deriveKeyMaterial(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 512)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyMaterial = factory.generateSecret(spec).encoded
        return Pair(keyMaterial.copyOfRange(0, 32), keyMaterial.copyOfRange(32, 64))
    }
}
