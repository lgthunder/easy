package com.lei.save_box

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lei.save_box.databinding.ActivityVideoEditBinding
import com.lei.save_box.manager.FileManager
import com.lei.save_box.view.ProgressDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class VideoEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEditBinding
    private var player: ExoPlayer? = null
    private var originalStart: Long = 0
    private var originalEnd: Long = 0
    private var sourcePath: String = ""

    private val thumbnailThCount = 15
    private val thumbnailHeightDp = 60

    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionUpdater = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.isPlaying) {
                    binding.trimRangeView.currentPositionMs = p.currentPosition
                }
            }
            positionHandler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityVideoEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourcePath = intent.getStringExtra("video_path") ?: run {
            finish()
            return
        }

        initPlayer()
        setupButtons()
    }

    private fun initPlayer() {
        val p = ExoPlayer.Builder(this).build()
        player = p
        binding.playerView.player = p
        binding.playerView.useController = false

        binding.trimRangeView.thumbnailCount = thumbnailThCount
        binding.trimRangeView.thumbnailHeight = thumbnailHeightDp * resources.displayMetrics.density

        val file = File(sourcePath)
        p.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        p.prepare()
        p.playWhenReady = false
        p.repeatMode = Player.REPEAT_MODE_OFF

        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val duration = p.duration
                    if (duration > 0 && binding.trimRangeView.durationMs == 0L) {
                        binding.trimRangeView.durationMs = duration
                        originalStart = 0
                        originalEnd = duration
                        updateTimeText()
                        generateThumbnails(file, duration)
                    }
                    positionHandler.removeCallbacks(positionUpdater)
                    positionHandler.post(positionUpdater)
                }
                if (state == Player.STATE_ENDED) {
                    binding.trimRangeView.currentPositionMs = 0L
                    p.seekTo(binding.trimRangeView.startMs)
                    p.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }
                binding.trimRangeView.currentPositionMs = p.currentPosition
            }
        })
    }

    private fun generateThumbnails(file: File, durationMs: Long) {
        val count = thumbnailThCount
        val interval = durationMs / count
        if (interval <= 0) return

        lifecycleScope.launch(Dispatchers.IO) {
            val thumbs = mutableListOf<Bitmap>()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                for (i in 0 until count) {
                    val timeUs = (interval * i + interval / 2) * 1000L
                    val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bmp != null) {
                        val thumbH = (thumbnailHeightDp * resources.displayMetrics.density * 2).toInt()
                        val thumbW = (bmp.width.toFloat() / bmp.height * thumbH).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bmp, thumbW, thumbH, true)
                        if (scaled !== bmp) bmp.recycle()
                        thumbs.add(scaled)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                binding.trimRangeView.setThumbnails(thumbs)
            }
        }
    }

    private fun setupButtons() {
        binding.trimRangeView.onSeeking = { timeMs ->
            binding.trimRangeView.currentPositionMs = timeMs
            player?.let { p ->
                p.pause()
                p.seekTo(timeMs)
            }
        }

        binding.trimRangeView.onRangeChanged = { _, _ ->
            updateTimeText()
        }

        binding.btnPlayPause.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) {
                p.pause()
            } else {
                val start = binding.trimRangeView.startMs
                val cur = p.currentPosition
                if (cur < start || cur >= binding.trimRangeView.endMs) {
                    p.seekTo(start)
                }
                p.play()
            }
        }

        binding.btnUndo.setOnClickListener {
            val p = player ?: return@setOnClickListener
            p.pause()
            binding.trimRangeView.setRange(originalStart, originalEnd)
            updateTimeText()
            p.seekTo(originalStart)
        }

        binding.btnSave.setOnClickListener {
            saveTrimmedVideo()
        }
    }

    private fun updateTimeText() {
        binding.tvStartTime.text = formatMs(binding.trimRangeView.startMs)
        binding.tvEndTime.text = formatMs(binding.trimRangeView.endMs)
    }

    private fun saveTrimmedVideo() {
        val startMs = binding.trimRangeView.startMs
        val endMs = binding.trimRangeView.endMs
        if (endMs - startMs < 100) {
            Toast.makeText(this, "裁剪范围太小", Toast.LENGTH_SHORT).show()
            return
        }

        val p = player
        p?.pause()

        val helper = ProgressDialogHelper(this)
        val sourceFile = File(sourcePath)
        val sourceName = sourceFile.nameWithoutExtension
        val outputDir = FileManager(this).vaultDir
        val timestamp = System.currentTimeMillis()
        val outputFile = File(outputDir, "edited_${sourceName}_${timestamp}.mp4")

        helper.show(getString(R.string.trimming_video), 100)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mainHandler = Handler(Looper.getMainLooper())
                trimWithMediaExtractor(sourceFile, outputFile, startMs * 1000L, endMs * 1000L) { progress ->
                    mainHandler.post {
                        helper.updateProgress(progress, "")
                    }
                }
                withContext(Dispatchers.Main) {
                    helper.dismiss()
                    Toast.makeText(this@VideoEditActivity, R.string.trim_success, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    helper.dismiss()
                    Toast.makeText(this@VideoEditActivity, "裁剪失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun trimWithMediaExtractor(
        sourceFile: File,
        outputFile: File,
        startUs: Long,
        endUs: Long,
        onProgress: (Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(sourceFile.absolutePath)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(0)

            val trackCount = extractor.trackCount
            val muxerTrackIndices = IntArray(trackCount) { -1 }
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
            var firstSample = true
            while (true) {
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
                        val pts = if (firstSample) 0L else (sampleTime - startUs).coerceAtLeast(0)
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
                    firstSample = false
                    val progress = ((sampleTime - startUs).toFloat() / totalDurationUs * 100).toInt()
                    if (progress != lastProgress) {
                        lastProgress = progress
                        onProgress(progress.coerceIn(0, 99))
                    }
                }

                if (!extractor.advance()) break
            }
            onProgress(100)
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        positionHandler.removeCallbacks(positionUpdater)
        player?.release()
        player = null
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}
