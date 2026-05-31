package com.lei.save_box.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.lei.save_box.SaveBoxApp
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
        if (timeMsList.isEmpty()) {
            Log.d(TAG, "Empty timeMsList, returning empty")
            return emptyList()
        }
        
        val inputFile = File(filePath)
        if (!inputFile.exists()) {
            Log.e(TAG, "Input file not found: $filePath")
            return timeMsList.map { null }
        }

        val outputDir = File(inputFile.parentFile, "ffmpeg_frames_${System.currentTimeMillis()}")
        if (!outputDir.exists()) {
            val created = outputDir.mkdirs()
            if (!created) {
                Log.e(TAG, "Failed to create output directory: ${outputDir.absolutePath}")
                return timeMsList.map { null }
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Output directory: ${outputDir.absolutePath}")
                
                val sortedTimeMs = timeMsList.sorted()
                val results = mutableListOf<Bitmap?>()
                
                sortedTimeMs.forEach { timeMs ->
                    val seconds = timeMs / 1000.0
                    val outPath = "${outputDir.absolutePath}/frame_${timeMs}.jpg"
                    
                    Log.d(TAG, "Frame ${timeMs}ms: $outPath")
                    
                    val command = "-y -ss $seconds -i \"$filePath\" -vframes 1 -vf scale=${targetWidth}:${targetHeight} \"$outPath\""
                    Log.d(TAG, "Command: $command")
                    withContext(Dispatchers.Main){
                        Toast.makeText(SaveBoxApp.APP,"FFmpegKit.execute(command)", Toast.LENGTH_SHORT).show()
                    }
                    val session = FFmpegKit.execute(command)
                    withContext(Dispatchers.Main){
                        Toast.makeText(SaveBoxApp.APP,"after FFmpegKit.execute(command) ${session.returnCode}", Toast.LENGTH_SHORT).show()
                    }

                    
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        val outFile = File(outPath)
                        if (outFile.exists() && outFile.length() > 0) {
//                            withContext(Dispatchers.Main){
//                                Toast.makeText(SaveBoxApp.APP,"FFmpeg successful  file created", Toast.LENGTH_SHORT).show()
//                            }

                            results.add(BitmapFactory.decodeFile(outPath))
                        } else {
//                            withContext(Dispatchers.Main){
//                                Toast.makeText(SaveBoxApp.APP,"FFmpeg successful but no file created", Toast.LENGTH_SHORT).show()
//                            }

                            Log.e(TAG, "FFmpeg successful but no file created")
                            Log.e(TAG, "Session output: ${session.output}")
                            results.add(null)
                        }

                    } else {
                        Log.e(TAG, "FFmpeg failed. Code: ${session.returnCode}")
                        Log.e(TAG, "Output: ${session.output}")
                        Log.e(TAG, "Error stack: ${session.failStackTrace}")
                        results.add(null)
//                        withContext(Dispatchers.Main){
//                            Toast.makeText(SaveBoxApp.APP,"FFmpeg failed. Code: ${session.returnCode}", Toast.LENGTH_SHORT).show()
//                        }

                    }
                }
                
                val successCount = results.count { it != null }
                Log.d(TAG, "Completed: $successCount/${sortedTimeMs.size}")
                
                val bitmapMap = sortedTimeMs.zip(results).toMap()
                timeMsList.map { bitmapMap[it] }
            } catch (e: Exception) {
                Log.e(TAG, "Exception", e)
//                withContext(Dispatchers.Main){
//                    Toast.makeText(SaveBoxApp.APP,"e ${e.message}", Toast.LENGTH_SHORT).show()
//                }
                timeMsList.map { null }
            } finally {
                try {
                    outputDir.deleteRecursively()
                } catch (_: Exception) {}
            }
        }
    }
}
