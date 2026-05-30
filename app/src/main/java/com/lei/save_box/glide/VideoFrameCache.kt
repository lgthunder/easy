package com.lei.save_box.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object VideoFrameCache {

    private const val TAG = "VideoFrameCache"
    private const val CACHE_DIR = "video_frame_cache"

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cacheFileName(key: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".png"
    }

    fun getCacheKey(filePath: String, timeMs: Long): String {
        var time = (timeMs * 1f / 1000).toInt()
        return "${filePath}_frame_${time}"
    }

    fun isCached(context: Context, filePath: String, timeMs: Long): Boolean {
        return File(getCacheDir(context), cacheFileName(getCacheKey(filePath, timeMs))).exists()
    }

    suspend fun loadFromCache(context: Context, filePath: String, timeMs: Long): Bitmap? {
        return withContext(Dispatchers.IO) {
            val cacheFile = File(getCacheDir(context), cacheFileName(getCacheKey(filePath, timeMs)))
            if (cacheFile.exists()) BitmapFactory.decodeFile(cacheFile.absolutePath) else null
        }
    }

    suspend fun saveToCache(context: Context, bitmap: Bitmap, filePath: String, timeMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(getCacheDir(context), cacheFileName(getCacheKey(filePath, timeMs)))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(cacheFile))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun extractAndCache(
        context: Context,
        filePath: String,
        timeMs: Long,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val cacheKey = getCacheKey(filePath, timeMs)

        val cached = loadFromCache(context, filePath, timeMs)
        if (cached != null) {
            Log.d(TAG, "extractAndCache: cache hit $cacheKey")
            return cached
        }

        Log.d(TAG, "extractAndCache: cache miss, extracting $cacheKey")
        val file = File(filePath)
        if (!file.exists()) return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val timeUs = timeMs * 1000L
            val scaled = retriever.getScaledFrameAtTime(
                timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetWidth, targetHeight
            )

            scaled?.let { bmp ->
                saveToCache(context, bmp, filePath, timeMs)
                bmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractAndCache failed: $cacheKey", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun extractAndCacheBatch(
        context: Context,
        filePath: String,
        targetWidth: Int,
        targetHeight: Int,
        timeMsList: List<Long>
    ): List<Bitmap?> {
        val file = File(filePath)
        if (!file.exists() || timeMsList.isEmpty()) {
            return timeMsList.map { null }
        }

        val results = Array<Bitmap?>(timeMsList.size) { null }
        val missedIndices = mutableListOf<Int>()

        for ((index, timeMs) in timeMsList.withIndex()) {
            val cached = loadFromCache(context, filePath, timeMs)
            if (cached != null) {
                results[index] = cached
            } else {
                missedIndices.add(index)
            }
        }

        if (missedIndices.isEmpty()) {
            return results.toList()
        }

        Log.d(TAG, "extractAndCacheBatch: ${missedIndices.size}/${timeMsList.size} cache misses, batch extracting")

        val missedTimeMs = missedIndices.map { timeMsList[it] }
        val extractor = FrameExtractorProvider.select(context)
        val frames = extractor.extractFrames(
            filePath, missedTimeMs, targetWidth, targetHeight
        )

        FrameExtractorProvider.benchmarkAndDecide(
            context, filePath, missedTimeMs, targetWidth, targetHeight
        )

        for ((i, index) in missedIndices.withIndex()) {
            val bitmap = frames[i]
            if (bitmap != null) {
                saveToCache(context, bitmap, filePath, timeMsList[index])
                results[index] = bitmap
            }
        }

        return results.toList()
    }

    suspend fun preloadFrames(
        context: Context,
        filePath: String,
        startMs: Long,
        endMs: Long,
        frameCount: Int,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap?> {
        val interval = (endMs - startMs) / frameCount
        val timeMsList = (0 until frameCount).map { startMs + interval * it + interval / 2 }
        return extractAndCacheBatch(context, filePath, targetWidth, targetHeight, timeMsList)
    }
}
