package com.lei.save_box

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.lei.save_box.databinding.ActivityVideoEditBinding
import com.lei.save_box.glide.VideoFrameCache
import com.lei.save_box.manager.BackgroundTaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log

class VideoEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEditBinding
    private var player: ExoPlayer? = null
    private var originalStart: Long = 0
    private var originalEnd: Long = 0
    private var sourcePath: String = ""

    private val thumbnailHeightDp = 60
    private val thumbnailCellWidthPx = 120f
    lateinit var oriFile :File
    val retriever = MediaMetadataRetriever()

    private val frameJobQueue = ArrayDeque<kotlinx.coroutines.Job>(8)
    private val throttleHandler = Handler(Looper.getMainLooper())
    private var throttleRunnable: Runnable? = null
    private var lastCallTimeMs: Long = 0

    private val speedOptions = arrayOf(0.5f, 1f, 1.25f, 1.5f, 2f)
    private var currentSpeedIndex = 1

    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionUpdater = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.isPlaying) {
                    val pos = p.currentPosition
                    val end = binding.trimRangeView.endMs
                    val start = binding.trimRangeView.startMs
                    if (pos >= end) {
                        p.seekTo(start)
                    }
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
        oriFile = File(sourcePath)
        initPlayer()
        setupButtons()
    }

    private fun initPlayer() {
        val p = ExoPlayer.Builder(this).build()
        player = p
        player?.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        binding.playerView.player = p
        binding.playerView.useController = false

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
                    p.seekTo(binding.trimRangeView.startMs)
                    binding.trimRangeView.currentPositionMs = binding.trimRangeView.startMs
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

        try { retriever.setDataSource(sourcePath) } catch (_: Exception) {}
    }

    private fun computeThumbnailCount(): Int {
        val viewWidth = binding.trimRangeView.width
        if (viewWidth <= 0) return 20
        return (viewWidth / thumbnailCellWidthPx).toInt().coerceIn(10, 60)
    }

    private fun generateThumbnails(file: File, durationMs: Long) {
        val count = computeThumbnailCount()
        binding.trimRangeView.thumbnailCount = count
        val interval = durationMs / count
        if (interval <= 0) return

        lifecycleScope.launch(Dispatchers.IO) {
            val thumbH = (thumbnailHeightDp * resources.displayMetrics.density * 2).toInt()
            val thumbW = (thumbnailHeightDp * resources.displayMetrics.density * 2).toInt()
            val timeMsList = (0 until count).map { interval * it + interval / 2 }

            val results = VideoFrameCache.extractAndCacheBatch(
                this@VideoEditActivity,
                file.absolutePath,
                thumbW,
                thumbH,
                timeMsList
            )

            val thumbs = results.filterNotNull()

            withContext(Dispatchers.Main) {
                binding.trimRangeView.setThumbnails(thumbs)
            }
        }
    }

    private fun generateThumbnailsForRange(file: File, startMs: Long, endMs: Long, thumbCount: Int) {
        Log.d("leiting","generateThumbnailsForRange  startMs $startMs  endMs $endMs thumbCount $thumbCount")
        val rangeDuration = endMs - startMs
        val interval = rangeDuration / thumbCount
        if (interval <= 0) return

        lifecycleScope.launch(Dispatchers.IO) {
            val thumbH = (thumbnailHeightDp * resources.displayMetrics.density * 2).toInt()
            val thumbW = (thumbnailHeightDp * resources.displayMetrics.density * 2).toInt()
            val timeMsList = (0 until thumbCount).map { startMs + interval * it + interval / 2 }

            val results = VideoFrameCache.extractAndCacheBatch(
                this@VideoEditActivity,
                file.absolutePath,
                thumbW,
                thumbH,
                timeMsList
            )

            val thumbs = results.filterNotNull()

            withContext(Dispatchers.Main) {
                Log.d("leiting","generateThumbnailsForRange finish  startMs $startMs  endMs $endMs thumbCount $thumbCount")
                binding.trimRangeView.setThumbnails(thumbs)
            }
        }
    }




    private fun setupButtons() {
        binding.btnSpeed.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
            val speed = speedOptions[currentSpeedIndex]
            player?.setPlaybackSpeed(speed)
            binding.tvSpeed.text = "${speed}x"
        }

        binding.btnVolume.setOnClickListener {
            val currentVolume = player?.volume ?: 0f
            if (currentVolume > 0) {
                player?.volume = 0f
                binding.sbVolume.progress = 0
            } else {
                player?.volume = 1f
                binding.sbVolume.progress = 100
            }
        }

        binding.sbVolume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.volume = progress / 100f
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {}
        })

        binding.trimRangeView.onSeeking = { timeMs ->
            val now = System.currentTimeMillis()
            val elapsed = now - lastCallTimeMs
            throttleRunnable?.let { throttleHandler.removeCallbacks(it) }
            if (elapsed >= 100) {
//                getCurrentFrame(timeMs)
                binding.trimRangeView.currentPositionMs = timeMs
                player?.let { p ->
                    p.pause()
                    p.seekTo(timeMs)
                }
                lastCallTimeMs = now
            } else {
                throttleRunnable = Runnable {
//                    getCurrentFrame(timeMs)
                    binding.trimRangeView.currentPositionMs = timeMs
                    player?.let { p ->
                        p.pause()
                        p.seekTo(timeMs)
                    }
                    lastCallTimeMs = System.currentTimeMillis()
                }
                throttleHandler.postDelayed(throttleRunnable!!, 100)
            }
        }

        binding.trimRangeView.onRangeChanged = { _, _ ->
            updateTimeText()
        }

        binding.trimRangeView.onZoomChanged = { startMs, endMs ->
            val videoFile = File(sourcePath)
            val totalDuration = player?.duration ?: 0L
            if (totalDuration > 0) {
                if (startMs == 0L && endMs == totalDuration) {
                    generateThumbnails(videoFile, totalDuration)
                } else {
                    val thumbCount = computeThumbnailCount()
                    generateThumbnailsForRange(videoFile, startMs, endMs, thumbCount)
                }
            }
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
                binding.videoCover.visibility = View.GONE
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


    private fun getCurrentFrame(durationMs: Long) {
        Log.d("leiting","getCurrentFrame $durationMs")
        val job = lifecycleScope.launch(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                bitmap = retriever.getFrameAtTime(durationMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                Log.d("leiting","getCurrentFrame excute $durationMs")
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) {
                Log.d("leiting","getCurrentFrame update image $durationMs")
                bitmap?.let {
                    binding.videoCover.visibility = View.VISIBLE
                    binding.videoCover.setImageBitmap(it)
                }
            }
        }
        frameJobQueue.addLast(job)
        while (frameJobQueue.size > 3) {
            frameJobQueue.removeFirst().cancel()
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
        while (frameJobQueue.isNotEmpty()) {
            frameJobQueue.removeFirst().cancel()
        }
        throttleRunnable?.let { throttleHandler.removeCallbacks(it) }
        player?.release()
        player = null
        retriever.release()
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}
