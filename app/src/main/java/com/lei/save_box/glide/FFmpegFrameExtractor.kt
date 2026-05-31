package com.lei.save_box.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
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

        FFmpegLogger.d("=== extractFrames called ===")
        FFmpegLogger.d("Input: $filePath")
        FFmpegLogger.d("TimeMsList size: ${timeMsList.size}")
        FFmpegLogger.d("Target size: ${targetWidth}x${targetHeight}")
        FFmpegLogger.d("Output dir: ${outputDir.absolutePath}")

        return runCatching {
            runBlockingInternal(filePath, timeMsList, targetWidth, targetHeight, outputDir)
        }.getOrElse { e ->
            FFmpegLogger.e("Exception in extractFrames", e)
            timeMsList.map { null }
        }
    }

    private fun runBlockingInternal(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int,
        outputDir: File
    ): List<Bitmap?> {
        val results = mutableListOf<Bitmap?>()
        val sortedTimeMs = timeMsList.sorted()
        
        FFmpegLogger.d("Processing ${sortedTimeMs.size} frames")

        sortedTimeMs.forEachIndexed { index, timeMs ->
            val seconds = timeMs / 1000.0
            val outPath = "${outputDir.absolutePath}/frame_${timeMs}.jpg"
            
            FFmpegLogger.d("[${index + 1}/${sortedTimeMs.size}] Processing frame at ${timeMs}ms -> $outPath")
            
            val command = "-y -ss $seconds -i \"$filePath\" -vframes 1 -vf scale=${targetWidth}:${targetHeight} \"$outPath\""
            FFmpegLogger.d("Command[$index]: $command")
            
            FFmpegLogger.d("Executing FFmpegKit.execute()...")
            val session = FFmpegKit.execute(command)
            FFmpegLogger.d("FFmpegKit returned: ${session.returnCode}")
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                val outFile = File(outPath)
                if (outFile.exists() && outFile.length() > 0) {
                    FFmpegLogger.d("File created: ${outFile.length()} bytes")
                    val bitmap = BitmapFactory.decodeFile(outPath)
                    results.add(bitmap)
                    FFmpegLogger.d("Bitmap decoded: ${bitmap != null}")
                } else {
                    FFmpegLogger.e("File not created or empty")
                    FFmpegLogger.e("Session output: ${session.output}")
                    results.add(null)
                }
            } else {
                FFmpegLogger.e("FFmpeg failed. Code: ${session.returnCode}")
                FFmpegLogger.e("Output: ${session.output}")
                FFmpegLogger.e("Error stack: ${session.failStackTrace}")
                results.add(null)
            }
        }
        
        val successCount = results.count { it != null }
        FFmpegLogger.d("=== Completed: $successCount/${sortedTimeMs.size} ===")
        
        // Cleanup
        try {
            outputDir.deleteRecursively()
            FFmpegLogger.d("Cleanup done")
        } catch (_: Exception) {}
        
        val bitmapMap = sortedTimeMs.zip(results).toMap()
        return timeMsList.map { bitmapMap[it] }
    }
}
