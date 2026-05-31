package com.lei.save_box.glide

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FFmpegLogger {
    private const val TAG = "FFmpegFrameExtractor"
    private const val LOG_DIR = "ffmpeg_logs"
    private const val LOG_FILE = "ffmpeg_trace.log"
    
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    private val lock = Any()

    private var writeLog =false
    
    fun init(context: Context) {
        synchronized(lock) {
            if (logFile != null) return
            
            val logDir = File(context.filesDir, LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            logFile = File(logDir, LOG_FILE)
            d("=== FFmpegLogger initialized ===")
            d("Log file: ${logFile?.absolutePath}")
        }
    }
    
    fun d(message: String) {
        Log.d(TAG, message)
        writeLog("D", message)
    }
    
    fun e(message: String) {
        Log.e(TAG, message)
        writeLog("E", message)
    }
    
    fun e(message: String, throwable: Throwable?) {
        Log.e(TAG, message, throwable)
        writeLog("E", "$message: ${throwable?.message}")
        throwable?.let {
            writeLog("E", "StackTrace: ${it.stackTraceToString()}")
        }
    }
    
    fun w(message: String) {
        Log.w(TAG, message)
        writeLog("W", message)
    }
    
    private fun writeLog(level: String, message: String) {
        if(!writeLog)  return
        synchronized(lock) {
            try {
                logFile?.let { file ->
                    PrintWriter(FileWriter(file, true)).use { writer ->
                        val timestamp = dateFormat.format(Date())
                        writer.println("$timestamp [$level] $message")
                        writer.flush()
                    }
                }
            } catch (_: Exception) {}
        }
    }
    
    fun getLogFile(): File? = logFile
    
    fun getLogDir(): File? = logFile?.parentFile
}
