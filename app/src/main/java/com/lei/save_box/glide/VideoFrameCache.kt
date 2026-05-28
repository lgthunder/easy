package com.lei.save_box.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        return "${filePath}_frame_${timeMs}"
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
                val scaled = Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true)
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
        val results = mutableListOf<Bitmap?>()

        for (i in 0 until frameCount) {
            val timeMs = startMs + interval * i + interval / 2
            val bitmap = extractAndCache(context, filePath, timeMs, targetWidth, targetHeight)
            results.add(bitmap)
        }

        return results
    }
}