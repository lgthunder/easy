package com.lei.save_box.glide

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.lei.save_box.manager.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

interface BatchFrameExtractor {
    suspend fun extractFrames(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap?>
}

class FfmpegBatchExtractor : BatchFrameExtractor {
    override suspend fun extractFrames(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap?> {
        FFmpegLogger.d("[FfmpegBatchExtractor] extractFrames called: file=$filePath, frames=${timeMsList.size}")
        val result = FFmpegFrameExtractor.extractFrames(filePath, timeMsList, targetWidth, targetHeight)
        FFmpegLogger.d("[FfmpegBatchExtractor] extractFrames completed: ${result.count { it != null }}/${timeMsList.size} success")
        return result
    }
}

class RetrieverBatchExtractor : BatchFrameExtractor {

    override suspend fun extractFrames(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap?> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext timeMsList.map { null }

        val results = Array<Bitmap?>(timeMsList.size) { null }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            for ((i, timeMs) in timeMsList.withIndex()) {
                results[i] = retriever.getScaledFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                )
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        results.toList()
    }
}

object FrameExtractorProvider {

    private const val PREF_NAME = "frame_extractor_prefs"
    private const val KEY_USE_RETRIEVER = "use_retriever"
    private const val SLOW_THRESHOLD_MS = 3_000L

    private var useRetriever: Boolean? = null

    fun select(context: Context): BatchFrameExtractor {
        val settings = SettingsManager(context)
        FFmpegLogger.d("[FrameExtractorProvider] select called: useFFmpeg=${settings.useFFmpeg}, cached=$useRetriever")
        
        if (!settings.useFFmpeg) {
            FFmpegLogger.d("[FrameExtractorProvider] Using RetrieverBatchExtractor (disabled in settings)")
            return RetrieverBatchExtractor()
        }
        if (useRetriever == null) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            useRetriever = prefs.getBoolean(KEY_USE_RETRIEVER, false)
        }
        
        val extractor = if (useRetriever == true) {
            RetrieverBatchExtractor()
        } else {
            FfmpegBatchExtractor()
        }
        
        FFmpegLogger.d("[FrameExtractorProvider] Selected: ${extractor::class.simpleName}")
        return extractor
    }

    private fun markSlow(context: Context) {
        useRetriever = true
        FFmpegLogger.w("[FrameExtractorProvider] Marking FFmpeg as slow, switching to Retriever")
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_RETRIEVER, true).apply()
    }

    fun benchmarkAndDecide(
        context: Context,
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ) {
        val settings = SettingsManager(context)
        FFmpegLogger.d("[FrameExtractorProvider] benchmarkAndDecide called")
        
        if (!settings.useFFmpeg) {
            FFmpegLogger.d("[FrameExtractorProvider] Skipping benchmark (FFmpeg disabled)")
            return
        }
        if (useRetriever != null) {
            FFmpegLogger.d("[FrameExtractorProvider] Skipping benchmark (already decided: $useRetriever)")
            return
        }

        val start = System.currentTimeMillis()
        FFmpegLogger.d("[FrameExtractorProvider] Starting benchmark with ${timeMsList.size} frames...")
        
        runBlocking {
            FFmpegFrameExtractor.extractFrames(filePath, timeMsList, targetWidth, targetHeight)
        }
        
        val elapsed = System.currentTimeMillis() - start
        FFmpegLogger.d("[FrameExtractorProvider] Benchmark completed in ${elapsed}ms (threshold: ${SLOW_THRESHOLD_MS}ms)")

        if (elapsed > SLOW_THRESHOLD_MS) {
            markSlow(context)
        } else {
            FFmpegLogger.d("[FrameExtractorProvider] FFmpeg is fast enough, keeping as is")
        }
    }
}
