package com.lei.save_box.manager

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.ceil
import kotlin.math.sqrt
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lei.save_box.R
import com.lei.save_box.VideoEditActivity
import com.lei.save_box.view.FloatingWindowView
import java.io.File

class FloatingWindowManager(
    private val context: Context,
    private val container: FrameLayout
) {
    private val windows = mutableListOf<FloatingWindowView>()
    private val players = mutableListOf<ExoPlayer>()
    private val prefs: SharedPreferences = context.getSharedPreferences("floating_window", Context.MODE_PRIVATE)

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
        }
        Glide.with(context)
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(imageView)
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
        val playerView = LayoutInflater.from(context)
            .inflate(R.layout.view_video_player, null, false) as PlayerView
        playerView.apply {
            this.player = player
            useController = true
            controllerAutoShow = true
            controllerShowTimeoutMs = 3000
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
        window.showEditButton(true)
        window.setOnEditClickListener {
            val intent = Intent(context, VideoEditActivity::class.java)
            intent.putExtra("video_path", file.absolutePath)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
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

    fun tileHorizontal() {
        val list = windows.toList()
        if (list.isEmpty()) return
        val containerW = container.width
        val containerH = container.height
        if (containerW == 0 || containerH == 0) return

        val count = list.size
        val eachW = containerW / count
        val eachH = containerH

        for ((i, window) in list.withIndex()) {
            window.layoutParams = FrameLayout.LayoutParams(eachW, eachH)
            window.x = (i * eachW).toFloat()
            window.y = 0f
            saveWindowState(window)
        }
    }

    fun tileVertical() {
        val list = windows.toList()
        if (list.isEmpty()) return
        val containerW = container.width
        val containerH = container.height
        if (containerW == 0 || containerH == 0) return

        val count = list.size
        val eachW = containerW
        val eachH = containerH / count

        for ((i, window) in list.withIndex()) {
            window.layoutParams = FrameLayout.LayoutParams(eachW, eachH)
            window.x = 0f
            window.y = (i * eachH).toFloat()
            saveWindowState(window)
        }
    }

    fun tileGrid() {
        val list = windows.toList()
        if (list.isEmpty()) return
        val containerW = container.width
        val containerH = container.height
        if (containerW == 0 || containerH == 0) return

        val count = list.size
        val cols = ceil(sqrt(count.toDouble())).toInt()
        val rows = ceil(count.toDouble() / cols).toInt()
        val eachW = containerW / cols
        val eachH = containerH / rows

        for ((i, window) in list.withIndex()) {
            val col = i % cols
            val row = i / cols
            window.layoutParams = FrameLayout.LayoutParams(eachW, eachH)
            window.x = (col * eachW).toFloat()
            window.y = (row * eachH).toFloat()
            saveWindowState(window)
        }
    }

    private fun closeWindow(window: FloatingWindowView) {
        saveWindowState(window)
        windows.remove(window)
        window.removeFromParent()
    }

    private fun addWindow(window: FloatingWindowView) {
        val savedW = prefs.getInt(KEY_WINDOW_WIDTH, 0)
        val savedH = prefs.getInt(KEY_WINDOW_HEIGHT, 0)
        val savedX = prefs.getFloat(KEY_WINDOW_X, -1f)
        val savedY = prefs.getFloat(KEY_WINDOW_Y, -1f)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        container.addView(window, lp)
        windows.add(window)

        if (savedW > 0 && savedH > 0) {
            window.post {
                window.layoutParams = FrameLayout.LayoutParams(savedW, savedH)
                if (savedX >= 0f && savedY >= 0f) {
                    window.x = savedX
                    window.y = savedY
                }
            }
        }

        bringWindowToFront(window)
    }

    private fun saveWindowState(window: FloatingWindowView) {
        prefs.edit()
            .putFloat(KEY_WINDOW_X, window.x)
            .putFloat(KEY_WINDOW_Y, window.y)
            .putInt(KEY_WINDOW_WIDTH, window.width)
            .putInt(KEY_WINDOW_HEIGHT, window.height)
            .apply()
    }

    private fun bringWindowToFront(window: FloatingWindowView) {
        window.bringToFront()
    }

    companion object {
        private const val KEY_WINDOW_X = "window_x"
        private const val KEY_WINDOW_Y = "window_y"
        private const val KEY_WINDOW_WIDTH = "window_width"
        private const val KEY_WINDOW_HEIGHT = "window_height"
    }
}
