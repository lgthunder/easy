package com.lei.save_box.glide

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.lei.save_box.manager.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
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

//    override suspend fun extractFrames(
//        filePath: String,
//        timeMsList: List<Long>,
//        targetWidth: Int,
//        targetHeight: Int
//    ): List<Bitmap?> = withContext(Dispatchers.IO) {
//        val file = File(filePath)
//        if (!file.exists()) return@withContext timeMsList.map { null }
//
//        val results = Array<Bitmap?>(timeMsList.size) { null }
//        val retriever = MediaMetadataRetriever()
//        try {
//            retriever.setDataSource(filePath)
//            for ((i, timeMs) in timeMsList.withIndex()) {
//                results[i] = retriever.getScaledFrameAtTime(
//                    timeMs * 1000L,
//                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
//                    targetWidth,
//                    targetHeight
//                )
//            }
//        } finally {
//            try { retriever.release() } catch (_: Exception) {}
//        }
//        results.toList()
//    }

    override suspend fun extractFrames(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap?> =withContext(Dispatchers.IO){
             extractFramesParallel(filePath,timeMsList)
    }


    // 限制最大并行任务数为 4
    private val semaphore = Semaphore(4)

    /**
     * 并行提取指定时间点的视频帧
     * @param videoUri 视频URI
     * @param timePointsMs 要提取的时间点列表 (单位: 毫秒)
     * @return 成功提取的 Bitmap 列表，顺序与原列表相同，失败位置为 null
     */
    suspend fun extractFramesParallel(
        videoUri: String,
        timePointsMs: List<Long>
    ): List<Bitmap?> = withContext(Dispatchers.IO) {

        // 为每个时间点创建一个并发任务
        val deferredResults = timePointsMs.map { timeMs ->
            async {
                // 获取信号量，如果当前并发数已达上限，此处会挂起等待
                semaphore.acquire()
                try {
                    extractFrameAtTime(videoUri, timeMs)
                } finally {
                    // 任务结束，释放信号量
                    semaphore.release()
                }
            }
        }

        // 等待所有任务完成并返回结果
        deferredResults.awaitAll()
    }

    // 实际的抽帧逻辑，每个实例独立，运行在IO线程
    private fun extractFrameAtTime(videoUri: String, timeMs: Long): Bitmap? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever().apply {
                setDataSource( videoUri)
            }
            // 使用 OPTION_CLOSEST_SYNC 获取最近的关键帧，速度最快
            // 如需更精确，可改用 OPTION_CLOSEST (Android 10+)
            retriever?.getFrameAtTime(
                timeMs * 1000, // 注意：参数单位是微秒 (μs)
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
        } catch (e: Exception) {
            // 记录错误日志
            e.printStackTrace()
            null
        } finally {
            retriever?.release()
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
