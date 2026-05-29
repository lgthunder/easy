package com.lei.save_box.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lei.save_box.R
import com.lei.save_box.databinding.ViewFloatingWindowBinding

class FloatingWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewFloatingWindowBinding
    private var onCloseListener: (() -> Unit)? = null
    private var onFocusListener: (() -> Unit)? = null

    private var isFullscreen = false
    private var savedX = 0f
    private var savedY = 0f
    private var savedWidth = 0
    private var savedHeight = 0

    private val minWidth = dpToPx(190)
    private val minHeight = dpToPx(100)
    private val edgeThreshold = dpToPx(28)

    private var dragDx = 0f
    private var dragDy = 0f

    private var resizeMode = RESIZE_NONE
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeOrigW = 0
    private var resizeOrigH = 0

    var fullScreenW = 0
    var fullScreenH =0

    private val handler = Handler(Looper.getMainLooper())
    private val volumeHideRunnable = Runnable {
        binding.volumeBar.visibility = View.GONE
    }

    companion object {
        private const val RESIZE_NONE = 0
        private const val RESIZE_WIDTH = 1
        private const val RESIZE_HEIGHT = 2
    }

    init {
        binding = ViewFloatingWindowBinding.inflate(LayoutInflater.from(context), this, true)

        binding.titleBar.setOnTouchListener { _, event -> handleTitleDrag(event) }
        binding.btnClose.setOnClickListener { onCloseListener?.invoke() }
        binding.btnMinimize.setOnClickListener { minimize() }
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }

    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        fullScreenW = (parent as View).width
        fullScreenH = (parent as View).height
        layoutParams = FrameLayout.LayoutParams(fullScreenW/4,fullScreenH/4)
    }



    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isFullscreen) return false

        val ex = event.x
        val ey = event.y
        val titleBarH = binding.titleBar.height.toFloat()

        if (binding.volumeBar.visibility == View.VISIBLE) {
            val volumeBarTop = titleBarH
            val volumeBarBottom = titleBarH + binding.volumeBar.height
            if (ey >= volumeBarTop && ey <= volumeBarBottom) return false
        }

        val inRightEdge = ex >= (width - edgeThreshold) && ey > titleBarH
        val inBottomEdge = ey >= (height - edgeThreshold) && ex < (width - edgeThreshold) && ey > titleBarH

        if (inRightEdge) {
            resizeMode = RESIZE_WIDTH
            resizeStartX = event.rawX
            resizeOrigW = width
            return true
        }
        if (inBottomEdge) {
            resizeMode = RESIZE_HEIGHT
            resizeStartY = event.rawY
            resizeOrigH = height
            return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isFullscreen) return false

        when (resizeMode) {
            RESIZE_WIDTH -> return handleWidthResize(event)
            RESIZE_HEIGHT -> return handleHeightResize(event)
        }
        return super.onTouchEvent(event)
    }

    private fun handleTitleDrag(event: MotionEvent): Boolean {
        if (isFullscreen) return false
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragDx = event.rawX - x
                dragDy = event.rawY - y
                bringToFront()
                onFocusListener?.invoke()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                x = event.rawX - dragDx
                y = event.rawY - dragDy
                true
            }
            else -> false
        }
    }

    private fun handleWidthResize(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val newW = (resizeOrigW + (event.rawX - resizeStartX).toInt()).coerceAtLeast(minWidth)
                val currentLp = layoutParams as? FrameLayout.LayoutParams
                layoutParams = FrameLayout.LayoutParams(newW, height).apply {
                    marginStart = currentLp?.marginStart ?: 0
                    marginEnd = currentLp?.marginEnd ?: 0
                    topMargin = currentLp?.topMargin ?: 0
                    bottomMargin = currentLp?.bottomMargin ?: 0
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resizeMode = RESIZE_NONE
                true
            }
            else -> false
        }
    }

    private fun handleHeightResize(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val newH = (resizeOrigH + (event.rawY - resizeStartY).toInt()).coerceAtLeast(minHeight)
                val currentLp = layoutParams as? FrameLayout.LayoutParams
                layoutParams = FrameLayout.LayoutParams(width, newH).apply {
                    marginStart = currentLp?.marginStart ?: 0
                    marginEnd = currentLp?.marginEnd ?: 0
                    topMargin = currentLp?.topMargin ?: 0
                    bottomMargin = currentLp?.bottomMargin ?: 0
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resizeMode = RESIZE_NONE
                true
            }
            else -> false
        }
    }

    private fun toggleFullscreen() {
        if (isFullscreen) exitFullscreen() else enterFullscreen()
    }

    private fun minimize() {
        savedX = x
        savedY = y
        savedWidth = width
        savedHeight = height

        layoutParams = FrameLayout.LayoutParams(minWidth, minHeight)
        x = (fullScreenW - minWidth).toFloat() / 2
        y = (fullScreenH - minHeight).toFloat() / 2
        requestLayout()
    }

    private fun enterFullscreen() {
        savedX = x
        savedY = y
        savedWidth = width
        savedHeight = height

        layoutParams.height = fullScreenH
        layoutParams.width = fullScreenW
        x = 0f
        y = 0f
        requestLayout()

        binding.btnFullscreen.setImageResource(android.R.drawable.ic_menu_revert)
        binding.btnFullscreen.contentDescription = context.getString(R.string.exit_fullscreen)
        isFullscreen = true
    }

    private fun exitFullscreen() {
        layoutParams = FrameLayout.LayoutParams(savedWidth, savedHeight)
        x = savedX
        y = savedY

        binding.btnFullscreen.setImageResource(android.R.drawable.ic_menu_crop)
        binding.btnFullscreen.contentDescription = context.getString(R.string.fullscreen)
        isFullscreen = false
    }

    fun setTitle(title: String) {
        binding.tvTitle.text = title
    }

    fun setContent(view: View) {
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(view, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        binding.contentContainer.setOnClickListener {
            toggleOverlay()
        }
    }

    private fun toggleOverlay() {
        if (binding.titleBar.visibility == View.VISIBLE) {
            binding.titleBar.visibility = View.GONE
            binding.volumeBar.visibility = View.GONE
            handler.removeCallbacks(volumeHideRunnable)
        } else {
            binding.titleBar.visibility = View.VISIBLE
        }
    }

    fun showMuteButton(show: Boolean) {
        binding.btnMute.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setMuted(muted: Boolean) {
        binding.btnMute.setImageResource(
            if (muted) android.R.drawable.ic_lock_silent_mode_off
            else android.R.drawable.ic_lock_silent_mode
        )
        binding.btnMute.contentDescription = context.getString(
            if (muted) R.string.unmute else R.string.mute
        )
    }

    fun setOnMuteClickListener(listener: () -> Unit) {
        binding.btnMute.setOnClickListener { listener() }
    }

    fun setOnMuteLongClickListener(listener: () -> Unit) {
        binding.btnMute.setOnLongClickListener {
            listener()
            true
        }
    }

    fun toggleVolumeBar() {
        if (binding.volumeBar.visibility == View.VISIBLE) {
            binding.volumeBar.visibility = View.GONE
            handler.removeCallbacks(volumeHideRunnable)
        } else {
            binding.volumeBar.visibility = View.VISIBLE
            startVolumeAutoHide()
        }
    }

    fun hideVolumeBar() {
        binding.volumeBar.visibility = View.GONE
        handler.removeCallbacks(volumeHideRunnable)
    }

    private fun startVolumeAutoHide() {
        handler.removeCallbacks(volumeHideRunnable)
        handler.postDelayed(volumeHideRunnable, 3000)
    }

    private fun resetVolumeAutoHide() {
        if (binding.volumeBar.visibility == View.VISIBLE) {
            startVolumeAutoHide()
        }
    }

    fun setVolume(volume: Int) {
        binding.sbVolume.progress = volume
    }

    fun getVolumeProgress(): Int {
        return binding.sbVolume.progress
    }

    fun setOnVolumeChangeListener(listener: (Int) -> Unit) {
        binding.sbVolume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) listener(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {
                handler.removeCallbacks(volumeHideRunnable)
            }
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {
                startVolumeAutoHide()
            }
        })
    }

    fun setOnEditClickListener(listener: () -> Unit) {
        binding.btnEdit.setOnClickListener { listener() }
    }

    fun showEditButton(show: Boolean) {
        binding.btnEdit.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }

    fun setOnFocusListener(listener: () -> Unit) {
        onFocusListener = listener
    }

    fun removeFromParent() {
        handler.removeCallbacks(volumeHideRunnable)
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun setTitleBarVisible(visibility: Int) {
        binding.titleBar.visibility = visibility
    }
}
