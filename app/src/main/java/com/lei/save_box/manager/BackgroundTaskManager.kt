package com.lei.save_box.manager

import android.app.Application
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.Looper
import com.lei.save_box.model.BackgroundTask
import com.lei.save_box.model.TaskStatus
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BackgroundTaskManager private constructor(private val app: Application) {

    companion object {
        @Volatile
        private var instance: BackgroundTaskManager? = null

        fun init(app: Application): BackgroundTaskManager {
            return instance ?: synchronized(this) {
                instance ?: BackgroundTaskManager(app).also { instance = it }
            }
        }

        fun getInstance(): BackgroundTaskManager {
            return instance ?: throw IllegalStateException("BackgroundTaskManager not initialized")
        }
    }

    private val storage = TaskHistoryStorage(app)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cancelFlags = mutableMapOf<String, AtomicBoolean>()

    private val _tasks = CopyOnWriteArrayList<BackgroundTask>()
    val tasks: List<BackgroundTask> get() = _tasks.toList()

    private val listeners = CopyOnWriteArrayList<Runnable>()

    val pendingCount: Int get() = _tasks.count { it.status == TaskStatus.PENDING }
    val processingCount: Int get() = _tasks.count { it.status == TaskStatus.PROCESSING }
    val activeCount: Int get() = pendingCount + processingCount

    init {
        _tasks.addAll(storage.loadAll())
    }

    fun addListener(onChanged: Runnable) {
        listeners.add(onChanged)
    }

    fun removeListener(onChanged: Runnable) {
        listeners.remove(onChanged)
    }

    private fun notifyChanged() {
        mainHandler.post {
            listeners.forEach { it.run() }
        }
    }

    fun addTask(
        sourcePath: String,
        startMs: Long,
        endMs: Long,
        sourceName: String
    ): BackgroundTask {
        val outputDir = File(app.filesDir, "vault")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "edited_${sourceName}_${System.currentTimeMillis()}.mp4")

        val task = BackgroundTask(
            sourcePath = sourcePath,
            outputPath = outputFile.absolutePath,
            startMs = startMs,
            endMs = endMs,
            sourceName = sourceName,
            status = TaskStatus.PENDING
        )
        _tasks.add(0, task)
        storage.saveTask(task)
        notifyChanged()
        processQueue()
        return task
    }

    fun cancelTask(taskId: String) {
        cancelFlags[taskId]?.set(true)
    }

    fun retryTask(taskId: String) {
        val idx = _tasks.indexOfFirst { it.id == taskId }
        if (idx < 0) return
        val task = _tasks[idx]
        if (task.status != TaskStatus.FAILED && task.status != TaskStatus.CANCELLED) return

        val updated = task.copy(status = TaskStatus.PENDING, progress = 0, errorMessage = "")
        _tasks[idx] = updated
        storage.saveTask(updated)
        notifyChanged()
        processQueue()
    }

    fun deleteTask(taskId: String) {
        val task = _tasks.find { it.id == taskId }
        _tasks.removeAll { it.id == taskId }
        storage.removeTask(taskId)

        if(task?.status != TaskStatus.COMPLETED){
         task?.let {
            val file = File(it.outputPath)
            if (file.exists()) file.delete()
           }
        }
        cancelFlags.remove(taskId)
        notifyChanged()
    }

    private fun processQueue() {
        executor.execute {
            while (true) {
                val pendingIdx = _tasks.indexOfFirst { it.status == TaskStatus.PENDING }
                if (pendingIdx < 0) return@execute

                val task = _tasks[pendingIdx]
                val cancelFlag = AtomicBoolean(false)
                cancelFlags[task.id] = cancelFlag

                val processing = task.copy(status = TaskStatus.PROCESSING)
                _tasks[pendingIdx] = processing
                storage.saveTask(processing)
                notifyChanged()

                try {
                    executeTrim(processing, cancelFlag)
                    if (cancelFlag.get()) {
                        val cancelled = processing.copy(status = TaskStatus.CANCELLED, completedAt = System.currentTimeMillis())
                        _tasks[_tasks.indexOfFirst { it.id == task.id }] = cancelled
                        storage.saveTask(cancelled)
                        cleanupOutput(cancelled.outputPath)
                    } else {
                        val completed = processing.copy(status = TaskStatus.COMPLETED, progress = 100, completedAt = System.currentTimeMillis())
                        _tasks[_tasks.indexOfFirst { it.id == task.id }] = completed
                        storage.saveTask(completed)
                    }
                } catch (e: Exception) {
                    val failed = processing.copy(status = TaskStatus.FAILED, errorMessage = e.message ?: "Unknown error", completedAt = System.currentTimeMillis())
                    _tasks[_tasks.indexOfFirst { it.id == task.id }] = failed
                    storage.saveTask(failed)
                }
                cancelFlags.remove(task.id)
                notifyChanged()
            }
        }
    }

    private fun executeTrim(task: BackgroundTask, cancelFlag: AtomicBoolean) {
        val sourceFile = File(task.sourcePath)
        val outputFile = File(task.outputPath)
        val startUs = task.startMs * 1000L
        val endUs = task.endMs * 1000L

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(sourceFile.absolutePath)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(0)

            val trackCount = extractor.trackCount
            val muxerTrackIndices = IntArray(trackCount) { -1 }
            val firstSamplePerTrack = BooleanArray(trackCount) { true }
            var sampleBuffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            val totalDurationUs = endUs - startUs

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    muxerTrackIndices[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                }
            }

            if (muxerTrackIndices.all { it < 0 }) {
                throw RuntimeException("No video or audio tracks found")
            }

            muxer.start()

            val videoTrackIndex = (0 until trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            if (videoTrackIndex != null) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            var lastProgress = -1
            val taskId = task.id
            while (!cancelFlag.get()) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0 || sampleTime >= endUs) break

                val trackIndex = extractor.sampleTrackIndex
                if (muxerTrackIndices[trackIndex] >= 0) {
                    sampleBuffer.clear()
                    var sampleSize = extractor.readSampleData(sampleBuffer, 0)
                    if (sampleSize < 0) {
                        sampleBuffer = ByteBuffer.allocate(-sampleSize)
                        sampleSize = extractor.readSampleData(sampleBuffer, 0)
                    }
                    if (sampleSize >= 0) {
                        val pts = if (firstSamplePerTrack[trackIndex]) 0L else (sampleTime - startUs).coerceAtLeast(0)
                        bufferInfo.apply {
                            offset = 0
                            size = sampleSize
                            presentationTimeUs = pts
                            flags = extractor.sampleFlags
                        }
                        muxer.writeSampleData(muxerTrackIndices[trackIndex], sampleBuffer, bufferInfo)
                    }
                }

                if (sampleTime >= startUs) {
                    firstSamplePerTrack[trackIndex] = false
                    val progress = ((sampleTime - startUs).toFloat() / totalDurationUs * 100).toInt().coerceIn(0, 99)
                    if (progress != lastProgress) {
                        lastProgress = progress
                        val idx = _tasks.indexOfFirst { it.id == taskId }
                        if (idx >= 0) {
                            _tasks[idx] = _tasks[idx].copy(progress = progress)
                            notifyChanged()
                        }
                    }
                }

                if (!extractor.advance()) break
            }
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun cleanupOutput(outputPath: String) {
        try { File(outputPath).delete() } catch (_: Exception) {}
    }

    fun cancelAllProcessing() {
        _tasks.filter { it.status == TaskStatus.PROCESSING || it.status == TaskStatus.PENDING }
            .forEach { cancelFlags[it.id]?.set(true) }
    }
}
