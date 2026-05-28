package com.lei.save_box.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toRect

class TrimRangeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var thumbnailCount: Int = 60
    var thumbnailHeight: Float = 60f

    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x806200EE.toInt()
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6200EE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
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
    private val positionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFEB3B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val thumbSrcRect = RectF()
    private val thumbDstRect = RectF()

    private var thumbnails: List<Bitmap> = emptyList()

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

    var currentPositionMs: Long = 0L
        set(value) {
            field = value
            invalidate()
        }

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

    fun setThumbnails(list: List<Bitmap>) {
        thumbnails = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val trackH = thumbnailHeight
        val trackTop = (h - trackH) / 2f
        val trackBottom = trackTop + trackH

        drawThumbnails(canvas, trackTop, trackBottom)

        if (durationMs > 0) {
            val startX = (startMs.toFloat() / durationMs) * w
            val endX = (endMs.toFloat() / durationMs) * w

            canvas.drawRect(startX, trackTop, endX, trackBottom, rangePaint)

            canvas.drawLine(startX, trackTop, startX, trackBottom, borderPaint)
            canvas.drawLine(endX, trackTop, endX, trackBottom, borderPaint)
            canvas.drawLine(startX, trackTop, endX, trackTop, borderPaint)
            canvas.drawLine(startX, trackBottom, endX, trackBottom, borderPaint)

            drawHandle(canvas, startX, h / 2)
            drawHandle(canvas, endX, h / 2)

            if (currentPositionMs > 0 && currentPositionMs <= durationMs) {
                val posX = (currentPositionMs.toFloat() / durationMs) * w
                canvas.drawLine(posX, trackTop, posX, trackBottom, positionLinePaint)
                canvas.drawCircle(posX, h / 2, 5f, positionLinePaint)
            }
        }
    }

    private fun drawThumbnails(canvas: Canvas, top: Float, bottom: Float) {
        val w = width.toFloat()
        if (thumbnails.isEmpty() || w <= 0) return
        val count = thumbnailCount.coerceIn(1, thumbnails.size)
        val cellW = w / count
        val cellH = bottom - top
        for (i in 0 until count) {
            val bmp = thumbnails[i % thumbnails.size]
            val cellLeft = i * cellW
            val cellRight = (i + 1) * cellW

            canvas.save()
            canvas.clipRect(cellLeft, top, cellRight, bottom)

            val dstW = (bmp.width.toFloat() / bmp.height.toFloat()) * cellH
            val dstH = cellH
            val dstCenter = cellLeft + cellW / 2f
            thumbDstRect.set(dstCenter - dstW / 2f, top, dstCenter + dstW / 2f, top + dstH)

            thumbSrcRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
            canvas.drawBitmap(bmp, thumbSrcRect.toRect(), thumbDstRect, null)

            canvas.restore()
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
        val h = height.toFloat()
        val trackTop = (h - thumbnailHeight) / 2f
        val trackBottom = trackTop + thumbnailHeight

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val distStart = Math.hypot((x - startX).toDouble(), (event.y - h / 2).toDouble())
                val distEnd = Math.hypot((x - endX).toDouble(), (event.y - h / 2).toDouble())
                dragging = when {
                    distStart < handleRadius * 2 -> DRAG_START
                    distEnd < handleRadius * 2 -> DRAG_END
                    event.y in trackTop..trackBottom -> {
                        val ratio = (x / w).coerceIn(0f, 1f)
                        val t = (ratio * durationMs).toLong()
                        val distStart2 = Math.abs(t - startMs)
                        val distEnd2 = Math.abs(t - endMs)
                        if (distStart2 <= distEnd2) DRAG_START else DRAG_END
                    }
                    else -> DRAG_NONE
                }
                if (dragging != DRAG_NONE) {
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
                    invalidate()
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
