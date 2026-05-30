package com.lei.save_box.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FFmpegFrameExtractor {

    private const val TAG = "FFmpegFrameExtractor"

    suspend fun extractFrames(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap?> {
        if (timeMsList.isEmpty()) return emptyList()
        if (!File(filePath).exists()) return timeMsList.map { null }

        val outputDir = File("${File(filePath).parentFile?.absolutePath ?: "/tmp"}/ffmpeg_frames_${System.currentTimeMillis()}")
        outputDir.mkdirs()

        val command = buildCommand(filePath, timeMsList, targetWidth, targetHeight, outputDir.absolutePath)

        return withContext(Dispatchers.IO) {
            val session = FFmpegKit.execute(command)
            val results = timeMsList.map { timeMs ->
                val outFile = File(outputDir, "frame_$timeMs.jpg")
                if (outFile.exists() && outFile.length() > 0) {
                    BitmapFactory.decodeFile(outFile.absolutePath)
                } else {
                    null
                }
            }

            outputDir.deleteRecursively()

            if (ReturnCode.isSuccess(session.returnCode)) {
                Log.d(TAG, "FFmpeg success: ${results.count { it != null }}/${timeMsList.size} frames")
            } else {
                Log.e(TAG, "FFmpeg failed: ${session.failStackTrace}")
            }

            results
        }
    }

    private fun buildCommand(
        filePath: String,
        timeMsList: List<Long>,
        targetWidth: Int,
        targetHeight: Int,
        outputDir: String
    ): String {
        val sb = StringBuilder()
        sb.append("-y ")
        for (timeMs in timeMsList) {
            val seconds = timeMs / 1000.0
            val outPath = "$outputDir/frame_$timeMs.jpg"
            sb.append("-ss $seconds -i \"$filePath\" -vframes 1 -s ${targetWidth}x${targetHeight} -q:v 5 \"$outPath\" ")
        }
        return sb.toString().trim()
    }
}
