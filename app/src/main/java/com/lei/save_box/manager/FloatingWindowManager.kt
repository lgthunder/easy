package com.lei.save_box.manager

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.load
import com.lei.save_box.view.FloatingWindowView
import java.io.File

class FloatingWindowManager(
    private val context: Context,
    private val container: FrameLayout
) {
    private val windows = mutableListOf<FloatingWindowView>()
    private val players = mutableListOf<ExoPlayer>()

    fun openImage(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val window = FloatingWindowView(context)
        window.setTitle(file.name)

        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            load(file)
        }
        window.setContent(imageView)

        window.setOnCloseListener {
            closeWindow(window)
        }

        window.setOnFocusListener {
            bringWindowToFront(window)
        }

        addWindow(window)
    }

    fun openVideo(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val window = FloatingWindowView(context)
        window.setTitle(file.name)

        val player = ExoPlayer.Builder(context).build()
        players.add(player)
        val playerView = PlayerView(context).apply {
            this.player = player
            useController = true
            controllerAutoShow = true
            controllerShowTimeoutMs = 3000
        }
        player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        player.prepare()
        player.playWhenReady = true
        player.volume = 0f
        playerView.setControllerVisibilityListener(object :PlayerView.ControllerVisibilityListener{
            override fun onVisibilityChanged(visibility: Int) {
                window.setTitleBarVisible(visibility)
            }

        })

        window.setContent(playerView)

        window.showMuteButton(true)
        window.setMuted(true)
        window.setVolume(0)
        window.setOnMuteClickListener {
            val muted = player.volume > 0f
            player.volume = if (muted) 0f else (window.getVolumeProgress() / 100f)
            window.setMuted(!muted)
            if (!muted) {
                window.setVolume((player.volume * 100).toInt())
            }
        }
        window.setOnMuteLongClickListener {
            window.toggleVolumeBar()
        }
        window.setOnVolumeChangeListener { progress ->
            val vol = progress / 100f
            player.volume = vol
            window.setMuted(progress == 0)
        }

        window.setOnCloseListener {
            try { player.release() } catch (_: Exception) {}
            players.remove(player)
            closeWindow(window)
        }

        window.setOnFocusListener {
            bringWindowToFront(window)
        }

        addWindow(window)
    }

    fun closeAll() {
        for (player in players) {
            try { player.release() } catch (_: Exception) {}
        }
        players.clear()
        val copy = windows.toList()
        for (window in copy) {
            window.removeFromParent()
        }
        windows.clear()
    }

    private fun closeWindow(window: FloatingWindowView) {
        windows.remove(window)
        window.removeFromParent()
    }

    private fun addWindow(window: FloatingWindowView) {
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        container.addView(window, lp)
        windows.add(window)
        bringWindowToFront(window)
    }

    private fun bringWindowToFront(window: FloatingWindowView) {
        window.bringToFront()
    }
}
