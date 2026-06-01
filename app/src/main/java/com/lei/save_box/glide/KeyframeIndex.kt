package com.lei.save_box.glide

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object KeyframeIndex {

    private val indexCache = ConcurrentHashMap<String, List<Long>>()

    fun buildIndex(filePath: String): List<Long> {
        FFmpegLogger.d("Building keyframe index for: $filePath")
        
        // 检查缓存
        val cached = indexCache[filePath]
        if (cached != null) {
            FFmpegLogger.d("Using cached keyframe index")
            return cached
        }

        val keyframes = mutableListOf<Long>()
        
        // 使用 FFmpeg 获取关键帧信息
        try {
            val command = "-i \"$filePath\" -vf \"select='eq(pict_type,I)'\" -print_format csv -show_entries frame=pts_time -loglevel quiet"
            
            FFmpegLogger.d("Executing FFmpeg keyframe detection")
            val session = FFmpegKit.execute(command)

            if (ReturnCode.isSuccess(session.returnCode)) {
                val output = session.output ?: ""
                val lines = output.split("\n")
                
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val timeSec = trimmed.toDoubleOrNull()
                        if (timeSec != null) {
                            val timeMs = (timeSec * 1000).toLong()
                            keyframes.add(timeMs)
                        }
                    }
                }
                
                keyframes.sort()
                FFmpegLogger.d("Found ${keyframes.size} keyframes using FFmpeg")
                indexCache[filePath] = keyframes
            } else {
                FFmpegLogger.w("FFmpeg keyframe detection failed: ${session.returnCode}")
            }
        } catch (e: Exception) {
            FFmpegLogger.e("Error building keyframe index", e)
        }
        
        return keyframes
    }



    fun buildIndexFast(filePath: String): List<Long> {
        FFmpegLogger.d("Building keyframe index (fast) for: $filePath")

        val file = File(filePath)
        if (!file.exists()) {
            FFmpegLogger.e("File not found: $filePath")
            return emptyList()
        }

        val cached = indexCache[filePath]
        if (cached != null) {
            return cached
        }

        val keyframes = mutableListOf<Long>()

        try {
            // ✅ 使用 ffprobe 直接读取容器信息，不解码（快10-100倍）
            val command = arrayOf(
                "-v", "quiet",
                "-select_streams", "v:0",
                "-show_entries", "packet=pts_time,flags",
                "-of", "csv=p=0",
                filePath
            )

            FFmpegLogger.d("Executing ffprobe: ${command.joinToString(" ")}")

            val session = FFprobeKit.executeWithArguments(command)

            if (ReturnCode.isSuccess(session.returnCode)) {
                val output = session.output ?: ""
                val lines = output.split("\n")

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        val parts = trimmed.split(",")
                        if (parts.size >= 2) {
                            val ptsTime = parts[0].toDoubleOrNull()
                            val flags = parts[1]
                            // K 表示关键帧（Key frame）
                            if (ptsTime != null && flags.contains("K")) {
                                val timeMs = (ptsTime * 1000).toLong()
                                keyframes.add(timeMs)
                            }
                        }
                    }
                }

                keyframes.sort()
                FFmpegLogger.d("Found ${keyframes.size} keyframes via ffprobe")
                indexCache[filePath] = keyframes
            } else {
                FFmpegLogger.e("ffprobe failed with code: ${session.returnCode}")
            }
        } catch (e: Exception) {
            FFmpegLogger.e("Error building keyframe index", e)
        }

        return keyframes
    }

    fun findNearestKeyframe(keyframes: List<Long>, targetTimeMs: Long): Long {
        if (keyframes.isEmpty()) {
            return targetTimeMs
        }

        var left = 0
        var right = keyframes.size - 1
        
        if (targetTimeMs <= keyframes[0]) {
            return keyframes[0]
        }
        if (targetTimeMs >= keyframes[right]) {
            return keyframes[right]
        }

        while (left <= right) {
            val mid = left + (right - left) / 2
            when {
                keyframes[mid] == targetTimeMs -> return keyframes[mid]
                keyframes[mid] < targetTimeMs -> left = mid + 1
                else -> right = mid - 1
            }
        }

        val leftFrame = keyframes[right]
        val rightFrame = keyframes[left]
        
        return if (targetTimeMs - leftFrame < rightFrame - targetTimeMs) {
            leftFrame
        } else {
            rightFrame
        }
    }

    fun findNearestKeyframes(keyframes: List<Long>, targetTimeMsList: List<Long>): List<Long> {
        return targetTimeMsList.map { findNearestKeyframe(keyframes, it) }
    }

    fun clearCache() {
        indexCache.clear()
        FFmpegLogger.d("Keyframe index cache cleared")
    }

    fun removeFromCache(filePath: String) {
        indexCache.remove(filePath)
    }
}
