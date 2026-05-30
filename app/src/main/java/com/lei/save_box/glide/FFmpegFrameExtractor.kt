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

        return withContext(Dispatchers.IO) {
            timeMsList.map { timeMs ->
                val seconds = timeMs / 1000.0
                val outPath = "${outputDir.absolutePath}/frame_$timeMs.jpg"
                val command = "-y -ss $seconds -i \"$filePath\" -vframes 1 -s ${targetWidth}x${targetHeight} -q:v 5 \"$outPath\""

                val session = FFmpegKit.execute(command)

                val outFile = File(outPath)
                val bitmap = if (outFile.exists() && outFile.length() > 0) {
                    BitmapFactory.decodeFile(outFile.absolutePath)
                } else {
                    null
                }

                if (!ReturnCode.isSuccess(session.returnCode)) {
                    Log.e(TAG, "FFmpeg failed at ${timeMs}ms: ${session.failStackTrace}")
                }
                Log.e(TAG, "FFmpeg load success  at ${timeMs}ms: ")

                bitmap
            }.also {
                outputDir.deleteRecursively()
                val successCount = it.count { b -> b != null }
                Log.d(TAG, "FFmpeg success: $successCount/${timeMsList.size} frames")
            }
        }
    }
}
