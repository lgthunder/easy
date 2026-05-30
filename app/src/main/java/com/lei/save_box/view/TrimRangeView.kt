package com.lei.save_box.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.toRect
import com.lei.save_box.glide.RetrieverBatchExtractor
import com.lei.save_box.manager.SettingsManager

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
    private val zoomHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val zoomHandleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val positionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFEB3B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val positionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }
    private val thumbSrcRect = RectF()
    private val thumbDstRect = RectF()

    private var thumbnails: List<Bitmap> = emptyList()

    var durationMs: Long = 0L
        set(value) {
            field = value
            startMs = 0
            endMs = value
            zoomScale = zoomScale.coerceIn(minZoom, maxZoom)
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

    private var zoomScale: Float = 1f
    private var viewCenterMs: Long = 0L
    private var zoomAnchorMs: Long = 0L
    private val minZoom: Float = 1f
    private val maxZoom: Float
        get() {
            if (durationMs <= 0) return 5f
            return (durationMs.toFloat() / MIN_VISIBLE_DURATION_MS).coerceIn(5f, 200f)
        }
    private val isZoomed: Boolean get() = zoomScale > 1.01f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            zoomAnchorMs = pixelToTime(detector.focusX, width.toFloat())
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomScale = (zoomScale * detector.scaleFactor).coerceIn(minZoom, maxZoom)
            viewCenterMs = zoomAnchorMs
            invalidate()
            if (useFFmpeg) {
                scheduleZoomChangedCallback()
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (zoomScale < 1.02f) zoomScale = 1f
            viewCenterMs = zoomAnchorMs
            invalidate()
            triggerZoomChanged()
        }
    })

    var onZoomChanged: ((visibleStartMs: Long, visibleEndMs: Long) -> Unit)? = null

    private var zoomChangedPending = false

    private var useFFmpeg = false

    private fun scheduleZoomChangedCallback() {
        if (zoomChangedPending) return
        zoomChangedPending = true
        postDelayed({
            zoomChangedPending = false
            triggerZoomChanged()
        }, 100L)
    }

    private fun triggerZoomChanged() {
        if (isZoomed) {
            onZoomChanged?.invoke(visibleStartMs, visibleEndMs)
        } else {
            onZoomChanged?.invoke(0, durationMs)
        }
    }

    private val visibleDurationMs: Long
        get() = (durationMs / zoomScale).toLong().coerceAtLeast(1)
    private val visibleStartMs: Long
        get() = (viewCenterMs - visibleDurationMs / 2).coerceIn(0, durationMs - visibleDurationMs)
    private val visibleEndMs: Long
        get() = visibleStartMs + visibleDurationMs

    private fun pixelToTime(px: Float, w: Float): Long {
        if (!isZoomed) return (px / w * durationMs).toLong()
        return (visibleStartMs + px / w * visibleDurationMs).toLong()
    }

    private fun timeToPixel(ms: Long, w: Float): Float {
        if (!isZoomed) return (ms.toFloat() / durationMs) * w
        return ((ms - visibleStartMs).toFloat() / visibleDurationMs) * w
    }

    companion object {
        private const val DRAG_NONE = 0
        private const val DRAG_START = 1
        private const val DRAG_END = 2
        private const val DRAG_POSITION = 3
        private const val MIN_VISIBLE_DURATION_MS = 60_000L
    }

    init {
        val settings = SettingsManager(context)
        useFFmpeg = settings.useFFmpeg
    }

    fun setRange(start: Long, end: Long) {
        startMs = start.coerceIn(0, durationMs)
        endMs = end.coerceIn(0, durationMs)
        zoomScale = 1f
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
        val textHeight = positionTextPaint.descent() - positionTextPaint.ascent()
        val textMargin = 8f
        val trackTop = textHeight + textMargin * 2
        val trackBottom = trackTop + trackH

        drawThumbnails(canvas, trackTop, trackBottom, w)

        if (durationMs > 0) {
            val startX = timeToPixel(startMs, w)
            val endX = timeToPixel(endMs, w)

            canvas.drawRect(startX, trackTop, endX, trackBottom, rangePaint)

            canvas.drawLine(startX, trackTop, startX, trackBottom, borderPaint)
            canvas.drawLine(endX, trackTop, endX, trackBottom, borderPaint)
            canvas.drawLine(startX, trackTop, endX, trackTop, borderPaint)
            canvas.drawLine(startX, trackBottom, endX, trackBottom, borderPaint)

            val trackCenterY = trackTop + trackH / 2
            if (isZoomed) {
                drawZoomHandle(canvas, startX, trackCenterY)
                drawZoomHandle(canvas, endX, trackCenterY)

                val zoomText = "%.1fx".format(zoomScale)
                val zoomTextWidth = positionTextPaint.measureText(zoomText)
                positionTextPaint.color = 0xCC000000.toInt()
                canvas.drawRect(4f, trackTop - textHeight - textMargin, 4f + zoomTextWidth + 8f, trackTop, positionTextPaint)
                positionTextPaint.color = 0xFFFF9800.toInt()
                canvas.drawText(zoomText, 8f + zoomTextWidth / 2, trackTop - textMargin / 2, positionTextPaint)
            } else {
                drawHandle(canvas, startX, trackCenterY)
                drawHandle(canvas, endX, trackCenterY)
            }

            if (currentPositionMs > 0 && currentPositionMs <= durationMs) {
                val posX = timeToPixel(currentPositionMs, w)
                canvas.drawLine(posX, trackTop, posX, trackBottom, positionLinePaint)
                canvas.drawCircle(posX, trackCenterY, 5f, positionLinePaint)

                val timeText = formatTime(currentPositionMs)
                val textWidth = positionTextPaint.measureText(timeText)
                val textY = textHeight + textMargin

                val bgLeft = posX - textWidth / 2 - 4f
                val bgRight = posX + textWidth / 2 + 4f
                val bgTop = textMargin
                val bgBottom = textHeight + textMargin * 2

                positionTextPaint.color = 0xCC000000.toInt()
                canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, positionTextPaint)

                positionTextPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawText(timeText, posX, textY, positionTextPaint)
            }
        }
    }

    private fun drawThumbnails(canvas: Canvas, top: Float, bottom: Float, w: Float) {
        if (thumbnails.isEmpty() || w <= 0 || durationMs <= 0) return
        val totalCount = thumbnailCount.coerceIn(1, thumbnails.size)
        val cellH = bottom - top

        if (isZoomed) {
            val totalCount = thumbnails.size
            if (totalCount <= 0) return
            val cellW = w / totalCount
            for (i in 0 until totalCount) {
                val bmp = thumbnails[i]
                val cellLeft = i * cellW
                val cellRight = cellLeft + cellW

                canvas.save()
                canvas.clipRect(cellLeft, top, cellRight, bottom)

                val dstW = (bmp.width.toFloat() / bmp.height.toFloat()) * cellH
                val dstCenter = cellLeft + cellW / 2f
                thumbDstRect.set(dstCenter - dstW / 2f, top, dstCenter + dstW / 2f, top + cellH)

                thumbSrcRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                canvas.drawBitmap(bmp, thumbSrcRect.toRect(), thumbDstRect, null)

                canvas.restore()
            }
        } else {
            val cellW = w / totalCount
            for (i in 0 until totalCount) {
                val bmp = thumbnails[i % thumbnails.size]
                val cellLeft = i * cellW
                val cellRight = (i + 1) * cellW

                canvas.save()
                canvas.clipRect(cellLeft, top, cellRight, bottom)

                val dstW = (bmp.width.toFloat() / bmp.height.toFloat()) * cellH
                val dstCenter = cellLeft + cellW / 2f
                thumbDstRect.set(dstCenter - dstW / 2f, top, dstCenter + dstW / 2f, top + cellH)

                thumbSrcRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                canvas.drawBitmap(bmp, thumbSrcRect.toRect(), thumbDstRect, null)

                canvas.restore()
            }
        }
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadius, handlePaint)
        canvas.drawCircle(cx, cy, handleRadius, handleStrokePaint)
    }

    private fun drawZoomHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadius, zoomHandlePaint)
        canvas.drawCircle(cx, cy, handleRadius, zoomHandleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true

        if (durationMs <= 0) return false
        val x = event.x
        val w = width.toFloat()
        val startX = timeToPixel(startMs, w)
        val endX = timeToPixel(endMs, w)
        val trackH = thumbnailHeight
        val textHeight = positionTextPaint.descent() - positionTextPaint.ascent()
        val textMargin = 8f
        val trackTop = textHeight + textMargin * 2
        val trackBottom = trackTop + trackH
        val trackCenterY = trackTop + trackH / 2

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val posX = timeToPixel(currentPositionMs, w)
                val distStart = Math.hypot((x - startX).toDouble(), (event.y - trackCenterY).toDouble())
                val distEnd = Math.hypot((x - endX).toDouble(), (event.y - trackCenterY).toDouble())
                val distPos = Math.hypot((x - posX).toDouble(), (event.y - trackCenterY).toDouble())
                dragging = when {
                    distPos < handleRadius * 3 && currentPositionMs > 0 -> DRAG_POSITION
                    distStart < handleRadius * 2 -> DRAG_START
                    distEnd < handleRadius * 2 -> DRAG_END
                    else -> DRAG_NONE
                }
                return dragging != DRAG_NONE || event.y in trackTop..trackBottom
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging != DRAG_NONE) {
                    applyDrag(pixelToTime(x, w), w)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging != DRAG_NONE) {
                    applyDrag(pixelToTime(x, w), w)
                }
                onRangeChanged?.invoke(startMs, endMs)
                dragging = DRAG_NONE
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyDrag(timeAtPos: Long, w: Float) {
        when (dragging) {
            DRAG_POSITION -> {
                currentPositionMs = timeAtPos.coerceIn(0, durationMs)
                onSeeking?.invoke(currentPositionMs)
            }
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

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}
