package com.lei.save_box.glide

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.lei.save_box.manager.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    ): List<Bitmap?> = FFmpegFrameExtractor.extractFrames(filePath, timeMsList, targetWidth, targetHeight)
}

class RetrieverBatchExtractor : BatchFrameExtractor {

    private val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

    override suspend fun extractFrames(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap?> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext timeMsList.map { null }
        if (timeMsList.isEmpty()) return@withContext emptyList()

        val chunkSize = (timeMsList.size + threadCount - 1) / threadCount
        val chunks = timeMsList.withIndex().groupBy { it.index / chunkSize }.values

        coroutineScope {
            chunks.map { chunk ->
                async {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(filePath)
                        chunk.map { (originalIndex, timeMs) ->
                            val rawBitmap = retriever.getFrameAtTime(
                                timeMs * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            )
                            originalIndex to if (rawBitmap != null) {
                                val scale = minOf(
                                    targetWidth.toFloat() / rawBitmap.width,
                                    targetHeight.toFloat() / rawBitmap.height
                                )
                                val scaledW = (rawBitmap.width * scale).toInt().coerceAtLeast(1)
                                val scaledH = (rawBitmap.height * scale).toInt().coerceAtLeast(1)
                                val scaled = Bitmap.createScaledBitmap(rawBitmap, scaledW, scaledH, true)
                                if (scaled !== rawBitmap) rawBitmap.recycle()
                                scaled
                            } else null
                        }
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }
            }.flatMap { it.await() }
                .sortedBy { it.first }
                .map { it.second }
        }
    }
}

object FrameExtractorProvider {

    private const val PREF_NAME = "frame_extractor_prefs"
    private const val KEY_USE_RETRIEVER = "use_retriever"
    private const val SLOW_THRESHOLD_MS = 3_000L

    private var useRetriever: Boolean? = null

    fun select(context: Context): BatchFrameExtractor {
        val settings = SettingsManager(context)
        if (!settings.useFFmpeg) {
            return RetrieverBatchExtractor()
        }
        if (useRetriever == null) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            useRetriever = prefs.getBoolean(KEY_USE_RETRIEVER, false)
        }
        return if (useRetriever == true) RetrieverBatchExtractor() else FfmpegBatchExtractor()
    }

    private fun markSlow(context: Context) {
        useRetriever = true
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
        if (!settings.useFFmpeg) return
        if (useRetriever != null) return

        val start = System.currentTimeMillis()
        runBlocking {
            FFmpegFrameExtractor.extractFrames(filePath, timeMsList, targetWidth, targetHeight)
        }
        val elapsed = System.currentTimeMillis() - start

        if (elapsed > SLOW_THRESHOLD_MS) {
            markSlow(context)
        }
    }
}
