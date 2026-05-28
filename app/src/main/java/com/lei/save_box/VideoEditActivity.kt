package com.lei.save_box

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import com.lei.save_box.manager.BackgroundTaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

        val sourceFile = File(sourcePath)
        val sourceName = sourceFile.nameWithoutExtension

        BackgroundTaskManager.getInstance().addTask(sourcePath, startMs, endMs, sourceName)

        Toast.makeText(this, "已添加到后台任务", Toast.LENGTH_SHORT).show()
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
