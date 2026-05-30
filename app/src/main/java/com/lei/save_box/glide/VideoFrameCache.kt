package com.lei.save_box.glide

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoFrameCache {

    private const val TAG = "VideoFrameCache"

    fun getCacheKey(filePath: String, timeMs: Long): String {
        var time = (timeMs*1f/1000).toInt()
        return "${filePath}_frame_${time}"
    }

    fun isCached(context: Context, filePath: String, timeMs: Long): Boolean {
        val cacheKey = getCacheKey(filePath, timeMs)
        return try {
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(VideoThumbnail(filePath,timeMs))
                .signature(ObjectKey(cacheKey))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .submit()
                .get()
            bitmap != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadFromCache(context: Context, filePath: String, timeMs: Long): Bitmap? {
        val cacheKey = getCacheKey(filePath, timeMs)
        return withContext(Dispatchers.IO) {
            try {
                Glide.with(context)
                    .asBitmap()
                    .load(VideoThumbnail(filePath,timeMs))
                    .signature(ObjectKey(cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .submit()
                    .get()
            } catch (_: Exception) {
                Log.d(TAG, "loadFromCache miss: $cacheKey")
                null
            }
        }
    }

    suspend fun saveToCache(context: Context, bitmap: Bitmap, filePath: String, timeMs: Long): Boolean {
        val cacheKey = getCacheKey(filePath, timeMs)
        return withContext(Dispatchers.IO) {
            try {
                Glide.with(context)
                    .asBitmap()
                    .load(bitmap)
                    .signature(ObjectKey(cacheKey))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload()
                Log.d(TAG, "saveToCache success: $cacheKey")
                true
            } catch (_: Exception) {
                Log.d(TAG, "saveToCache failed: $cacheKey")
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
            val rawBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            rawBitmap?.let { bmp ->
                val srcW = bmp.width.toFloat()
                val srcH = bmp.height.toFloat()
                val scale = minOf(targetWidth.toFloat() / srcW, targetHeight.toFloat() / srcH)
                val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
                val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true)
                if (scaled !== bmp) bmp.recycle()

                val success = saveToCache(context, scaled, filePath, timeMs)
                if (success) {
                    Log.d(TAG, "extractAndCache: saved to cache $cacheKey")
                }

                scaled
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

        Log.d(TAG, "extractAndCacheBatch: ${missedIndices.size}/${timeMsList.size} cache misses, batch extracting with FFmpeg")

        val missedTimeMs = missedIndices.map { timeMsList[it] }
        val frames = FFmpegFrameExtractor.extractFrames(
            filePath, missedTimeMs, targetWidth, targetHeight
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