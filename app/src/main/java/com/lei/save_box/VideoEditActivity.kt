package com.lei.save_box

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Transformer
import com.lei.save_box.databinding.ActivityVideoEditBinding
import com.lei.save_box.manager.FileManager
import com.lei.save_box.view.ProgressDialogHelper
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
                    }
                }
                if (state == Player.STATE_ENDED) {
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
            }
        })
    }

    private fun setupButtons() {
        binding.trimRangeView.onSeeking = { timeMs ->
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

        val transformer = Transformer.Builder(this).build()
        val mediaItem = MediaItem.Builder()
            .setUri(sourceFile.toURI().toString())
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        transformer.start(mediaItem, outputFile.absolutePath)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    kotlinx.coroutines.delay(300)
                    if (!outputFile.exists()) continue
                    val size = outputFile.length()
                    kotlinx.coroutines.delay(300)
                    if (outputFile.length() == size && size > 0) break
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

    override fun onDestroy() {
        super.onDestroy()
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
