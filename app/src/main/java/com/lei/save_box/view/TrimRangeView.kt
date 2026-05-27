package com.lei.save_box.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TrimRangeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40FFFFFF
        style = Paint.Style.FILL
    }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6200EE.toInt()
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6200EE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    var durationMs: Long = 0L
        set(value) {
            field = value
            startMs = 0
            endMs = value
            invalidate()
        }

    var startMs: Long = 0L
        private set
    var endMs: Long = 0L
        private set

    var onRangeChanged: ((Long, Long) -> Unit)? = null
    var onSeeking: ((Long) -> Unit)? = null

    private val handleRadius = 20f
    private var dragging: Int = DRAG_NONE

    companion object {
        private const val DRAG_NONE = 0
        private const val DRAG_START = 1
        private const val DRAG_END = 2
    }

    fun setRange(start: Long, end: Long) {
        startMs = start.coerceIn(0, durationMs)
        endMs = end.coerceIn(0, durationMs)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val trackTop = h / 2 - 4f
        val trackBottom = h / 2 + 4f

        canvas.drawRoundRect(0f, trackTop, w, trackBottom, 4f, 4f, trackPaint)

        if (durationMs > 0) {
            val startX = (startMs.toFloat() / durationMs) * w
            val endX = (endMs.toFloat() / durationMs) * w
            canvas.drawRect(startX, trackTop, endX, trackBottom, rangePaint)

            drawHandle(canvas, startX, h / 2)
            drawHandle(canvas, endX, h / 2)
        }
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadius, handlePaint)
        canvas.drawCircle(cx, cy, handleRadius, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0) return false
        val x = event.x
        val w = width.toFloat()
        val startX = (startMs.toFloat() / durationMs) * w
        val endX = (endMs.toFloat() / durationMs) * w

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val distStart = Math.hypot((x - startX).toDouble(), (event.y - height / 2f).toDouble())
                val distEnd = Math.hypot((x - endX).toDouble(), (event.y - height / 2f).toDouble())
                dragging = when {
                    distStart < handleRadius * 2 -> DRAG_START
                    distEnd < handleRadius * 2 -> DRAG_END
                    else -> DRAG_NONE
                }
                return dragging != DRAG_NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val ratio = (x / w).coerceIn(0f, 1f)
                val timeAtPos = (ratio * durationMs).toLong()
                when (dragging) {
                    DRAG_START -> {
                        startMs = timeAtPos.coerceIn(0, endMs - 100)
                        onSeeking?.invoke(startMs)
                    }
                    DRAG_END -> {
                        endMs = timeAtPos.coerceIn(startMs + 100, durationMs)
                        onSeeking?.invoke(endMs)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val ratio = (x / w).coerceIn(0f, 1f)
                val timeAtPos = (ratio * durationMs).toLong()
                when (dragging) {
                    DRAG_START -> {
                        startMs = timeAtPos.coerceIn(0, endMs - 100)
                        onSeeking?.invoke(startMs)
                    }
                    DRAG_END -> {
                        endMs = timeAtPos.coerceIn(startMs + 100, durationMs)
                        onSeeking?.invoke(endMs)
                    }
                }
                onRangeChanged?.invoke(startMs, endMs)
                dragging = DRAG_NONE
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
