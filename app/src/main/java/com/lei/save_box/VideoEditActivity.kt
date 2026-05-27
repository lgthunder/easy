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
        val mediaItem = MediaItem.fromUri(file.toURI().toString())
        p.setMediaItem(mediaItem)
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
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_INTERNAL) {
                    val end = binding.trimRangeView.endMs
                    if (p.currentPosition >= end && p.isPlaying) {
                        p.seekTo(binding.trimRangeView.startMs)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    val cur = p.currentPosition
                    val end = binding.trimRangeView.endMs
                    if (cur < binding.trimRangeView.startMs || cur >= end) {
                        p.seekTo(binding.trimRangeView.startMs)
                    }
                }
            }
        })
    }

    private fun setupButtons() {
        binding.trimRangeView.onRangeChanged = { _, _ ->
            updateTimeText()
        }

        binding.btnPlayPause.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) {
                p.pause()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                val startPos = binding.trimRangeView.startMs
                val cur = p.currentPosition
                if (cur < startPos || cur >= binding.trimRangeView.endMs) {
                    p.seekTo(startPos)
                }
                p.play()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        binding.btnUndo.setOnClickListener {
            val p = player ?: return@setOnClickListener
            p.pause()
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
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

        val helper = ProgressDialogHelper(this)
        val sourceFile = File(sourcePath)
        val outputDir = FileManager(this).vaultDir
        val outputFile = File(outputDir, "edited_${System.currentTimeMillis()}.mp4")

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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                transformer.start(mediaItem, outputFile.absolutePath)
                withContext(Dispatchers.Main) {
                    helper.dismiss()
                    Toast.makeText(this@VideoEditActivity, R.string.trim_success, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    helper.dismiss()
                    Toast.makeText(this@VideoEditActivity, "裁剪失败", Toast.LENGTH_SHORT).show()
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
