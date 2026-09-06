package com.dnk.wallpaperlyrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.dnk.wallpaperlyrics.LyricsSettings as LS

/**
 * The saturation and value square of the color picker.
 * Shaders and paints are built once, since onDraw runs on every drag frame.
 */
class SaturationValueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackRect = RectF()
    private val satPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val handleRadius = LS.dpToPx(context, 10f).toFloat()
    private val trackRadius = LS.dpToPx(context, 12f).toFloat()

    private val handleOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = LS.dpToPx(context, 3.5f).toFloat()
    }

    private val handleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = LS.dpToPx(context, 2f).toFloat()
    }

    private var currentHue: Float = 0f
    private var saturation: Float = 1f
    private var value: Float = 1f

    var onSaturationValueChanged: ((Float, Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Reserve padding equal to handle radius to prevent clipping at extremes
        trackRect.set(handleRadius, handleRadius, w.toFloat() - handleRadius, h.toFloat() - handleRadius)
        updateShaders()
    }

    private fun updateShaders() {
        if (trackRect.width() <= 0f || trackRect.height() <= 0f) return
        val pureHue = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
        satPaint.shader = LinearGradient(
            trackRect.left, trackRect.top, trackRect.right, trackRect.top,
            Color.WHITE, pureHue, Shader.TileMode.CLAMP
        )
        valPaint.shader = LinearGradient(
            trackRect.left, trackRect.top, trackRect.left, trackRect.bottom,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
    }

    fun setHue(hue: Float) {
        val clamped = hue.coerceIn(0f, 360f)
        if (currentHue != clamped) {
            currentHue = clamped
            updateShaders()
            invalidate()
        }
    }

    fun setSaturationAndValue(sat: Float, valLevel: Float) {
        saturation = sat.coerceIn(0f, 1f)
        value = valLevel.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, satPaint)
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, valPaint)

        val handleX = trackRect.left + saturation * trackRect.width()
        val handleY = trackRect.top + (1f - value) * trackRect.height()

        canvas.drawCircle(handleX, handleY, handleRadius, handleOuterPaint)
        canvas.drawCircle(handleX, handleY, handleRadius, handleInnerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Prevent enclosing ScrollView from stealing vertical drag gestures
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                updateFromTouch(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(x: Float, y: Float) {
        val newSat = IdleScreenSettings.componentFromTouch(x - trackRect.left, trackRect.width())
        val newV = 1f - IdleScreenSettings.componentFromTouch(y - trackRect.top, trackRect.height())
        if (saturation != newSat || value != newV) {
            saturation = newSat
            value = newV
            invalidate()
            onSaturationValueChanged?.invoke(newSat, newV)
        }
    }
}

/** The hue strip above the square. Same preallocation rule as the square. */
class HueSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val HUE_COLORS = intArrayOf(
            0xFFFF0000.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(),
            0xFF00FFFF.toInt(),
            0xFF0000FF.toInt(),
            0xFFFF00FF.toInt(),
            0xFFFF0000.toInt()
        )
    }

    private val trackRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val handleRadius = LS.dpToPx(context, 10f).toFloat()
    private val trackHeight = LS.dpToPx(context, 16f).toFloat()

    private val handleOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = LS.dpToPx(context, 3.5f).toFloat()
    }

    private val handleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = LS.dpToPx(context, 2f).toFloat()
    }

    private var hue: Float = 0f

    var onHueChanged: ((Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val top = (h.toFloat() - trackHeight) / 2f
        // Reserve padding equal to handle radius to prevent handle clipping at both ends
        trackRect.set(handleRadius, top, w.toFloat() - handleRadius, top + trackHeight)
        if (trackRect.width() > 0f) {
            trackPaint.shader = LinearGradient(
                trackRect.left, 0f, trackRect.right, 0f,
                HUE_COLORS, null, Shader.TileMode.CLAMP
            )
        }
    }

    fun setHue(newHue: Float) {
        val clamped = newHue.coerceIn(0f, 360f)
        if (hue != clamped) {
            hue = clamped
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cornerRadius = trackHeight / 2f
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        val handleX = trackRect.left + (hue / 360f) * trackRect.width()
        val handleY = height.toFloat() / 2f

        canvas.drawCircle(handleX, handleY, handleRadius, handleOuterPaint)
        canvas.drawCircle(handleX, handleY, handleRadius, handleInnerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Prevent enclosing ScrollView from stealing horizontal drag gestures
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                updateFromTouch(event.x)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(x: Float) {
        val fraction = IdleScreenSettings.componentFromTouch(x - trackRect.left, trackRect.width())
        val newHue = (fraction * 360f).coerceIn(0f, 360f)
        if (hue != newHue) {
            hue = newHue
            invalidate()
            onHueChanged?.invoke(newHue)
        }
    }
}
