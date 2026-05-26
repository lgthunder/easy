package com.lei.save_box.view

import android.content.Context
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
    private var dX = 0f
    private var dY = 0f
    private var resizeDx = 0f
    private var resizeDy = 0f
    private var onCloseListener: (() -> Unit)? = null
    private var onFocusListener: (() -> Unit)? = null

    private val minWidth = dpToPx(200)
    private val minHeight = dpToPx(200)

    init {
        binding = ViewFloatingWindowBinding.inflate(LayoutInflater.from(context), this, true)

        binding.titleBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = event.rawX - x
                    dY = event.rawY - y
                    bringToFront()
                    onFocusListener?.invoke()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    x = event.rawX - dX
                    y = event.rawY - dY
                    true
                }
                else -> false
            }
        }

        binding.resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeDx = event.rawX
                    resizeDy = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newW = (width + event.rawX - resizeDx).toInt().coerceAtLeast(minWidth)
                    val newH = (height + event.rawY - resizeDy).toInt().coerceAtLeast(minHeight)
                    layoutParams = (layoutParams ?: LayoutParams(minWidth, minHeight)).apply {
                        width = newW
                        height = newH
                    }
                    resizeDx = event.rawX
                    resizeDy = event.rawY
                    true
                }
                else -> false
            }
        }

        binding.btnClose.setOnClickListener {
            onCloseListener?.invoke()
        }

        setOnClickListener {
            bringToFront()
            onFocusListener?.invoke()
        }
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
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseListener = listener
    }

    fun setOnFocusListener(listener: () -> Unit) {
        onFocusListener = listener
    }

    fun removeFromParent() {
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
