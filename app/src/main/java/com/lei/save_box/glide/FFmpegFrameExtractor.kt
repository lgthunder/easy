package com.lei.save_box.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

object FFmpegFrameExtractor {

    fun extractFrames(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap?> {
        if (timeMsList.isEmpty()) {
            FFmpegLogger.d("Empty timeMsList, returning empty")
            return emptyList()
        }
        
        val inputFile = File(filePath)
        if (!inputFile.exists()) {
            FFmpegLogger.e("Input file not found: $filePath")
            return timeMsList.map { null }
        }

        val outputDir = File(inputFile.parentFile, "ffmpeg_frames_${System.currentTimeMillis()}")
        if (!outputDir.exists()) {
            val created = outputDir.mkdirs()
            if (!created) {
                FFmpegLogger.e("Failed to create output directory: ${outputDir.absolutePath}")
                return timeMsList.map { null }
            }
        }

        val startTime = System.currentTimeMillis()
        FFmpegLogger.d("=== extractFrames called ===")
        FFmpegLogger.d("Input: $filePath")
        FFmpegLogger.d("TimeMsList size: ${timeMsList.size}")
        FFmpegLogger.d("Target size: ${targetWidth}x${targetHeight}")

        return runCatching {
            runBlocking {
                extractFramesOptimized(filePath, timeMsList, targetWidth, targetHeight, outputDir)
            }
        }.getOrElse { e ->
            FFmpegLogger.e("Exception in extractFrames", e)
            timeMsList.map { null }
        }
    }

    private suspend fun extractFramesOptimized(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int,
        outputDir: File
    ): List<Bitmap?> {
        val sortedTimeMs = timeMsList.sorted()
        
        FFmpegLogger.d("Using fully optimized extraction (parallel FFmpeg + parallel decode + RGB_565 + q:v 5)")

        // 第一步：并行提取所有帧（FFmpeg）
        val ffmpegStartTime = System.currentTimeMillis()
        
        val filePaths = withContext(Dispatchers.IO) {
            sortedTimeMs.mapIndexed { index, timeMs ->
                async {
                    val seconds = timeMs / 1000.0
                    val outPath = "${outputDir.absolutePath}/frame_${timeMs}.jpg"
                    
                    FFmpegLogger.d("[${index + 1}/${sortedTimeMs.size}] Extracting frame at ${timeMs}ms (parallel)")
                    
                    // 方案2：调整质量参数 -q:v 5（更小的文件，更快的解码）
                    val command = "-y -ss $seconds -i \"$filePath\" -vframes 1 -q:v 5 -vf scale=${targetWidth}:${targetHeight} \"$outPath\""
                    
                    val session = FFmpegKit.execute(command)
                    
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        val outFile = File(outPath)
                        if (outFile.exists() && outFile.length() > 0) {
                            FFmpegLogger.d("  -> Frame[$index] OK: ${outFile.length()} bytes")
                            outPath
                        } else {
                            FFmpegLogger.e("  -> Frame[$index] failed: file not created or empty")
                            null
                        }
                    } else {
                        FFmpegLogger.e("  -> Frame[$index] failed: FFmpeg code ${session.returnCode}")
                        null
                    }
                }
            }.awaitAll()
        }
        
        val ffmpegTime = System.currentTimeMillis() - ffmpegStartTime
        FFmpegLogger.d("Parallel FFmpeg extraction completed in ${ffmpegTime}ms")

        // 第二步：并行解码（主要性能优化点！）
        val decodeStartTime = System.currentTimeMillis()
        
        val bitmaps = withContext(Dispatchers.IO) {
            filePaths.mapIndexed { index, path ->
                async {
                    if (path != null) {
                        // 方案1：使用 RGB_565 配置（内存减半，解码更快）
                        val options = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        val bitmap = BitmapFactory.decodeFile(path, options)
                        if (bitmap != null) {
                            FFmpegLogger.d("Decoded frame[$index]: ${bitmap.width}x${bitmap.height}")
                        }
                        bitmap
                    } else {
                        null
                    }
                }
            }.awaitAll()
        }
        
        val decodeTime = System.currentTimeMillis() - decodeStartTime
        FFmpegLogger.d("Parallel decoding completed in ${decodeTime}ms")

        // 清理
        try {
            outputDir.deleteRecursively()
            FFmpegLogger.d("Cleanup done")
        } catch (_: Exception) {}

        val successCount = bitmaps.count { it != null }
        
        FFmpegLogger.d("=== Completed: $successCount/${sortedTimeMs.size} in ${ffmpegTime + decodeTime}ms (FFmpeg: ${ffmpegTime}ms, Decode: ${decodeTime}ms) ===")

        // 恢复原始顺序
        val bitmapMap = sortedTimeMs.zip(bitmaps).toMap()
        return timeMsList.map { bitmapMap[it] }
    }
}
