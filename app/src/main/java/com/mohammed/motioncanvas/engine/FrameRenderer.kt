package com.mohammed.motioncanvas.engine

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.mohammed.motioncanvas.model.Layer
import com.mohammed.motioncanvas.model.Project
import com.mohammed.motioncanvas.model.ShapeKind
import kotlin.math.min

class FrameRenderer(private val context: Context) {
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bitmapCache = mutableMapOf<String, Bitmap?>()

    fun renderFrame(canvas: Canvas, project: Project, timeMs: Long) {
        canvas.drawColor(project.backgroundColorHex.toInt())

        for (layer in project.layers) {
            val localTime = timeMs - layer.startTimeMs
            if (localTime < 0L || localTime > layer.durationMs) continue

            val tr = KeyframeInterpolator.evaluateTrack(layer.transform, localTime)
            if (tr.opacity <= 0.001f) continue

            canvas.save()
            canvas.translate(tr.x, tr.y)
            canvas.rotate(tr.rotation)
            canvas.scale(tr.scaleX, tr.scaleY)

            when (layer) {
                is Layer.ShapeLayer -> drawShape(canvas, layer, tr.opacity)
                is Layer.TextLayer -> drawText(canvas, layer, tr.opacity)
                is Layer.ImageLayer -> drawImage(canvas, layer, tr.opacity)
            }
            canvas.restore()
        }
    }

    private fun drawShape(canvas: Canvas, layer: Layer.ShapeLayer, opacity: Float) {
        shapePaint.color = applyAlpha(layer.colorHex.toInt(), opacity)
        val hw = layer.widthPx / 2f
        val hh = layer.heightPx / 2f
        val rect = RectF(-hw, -hh, hw, hh)
        when (layer.kind) {
            ShapeKind.RECTANGLE -> canvas.drawRect(rect, shapePaint)
            ShapeKind.ROUNDED_RECT -> canvas.drawRoundRect(rect, layer.cornerRadiusPx, layer.cornerRadiusPx, shapePaint)
            ShapeKind.CIRCLE -> canvas.drawCircle(0f, 0f, min(hw, hh), shapePaint)
        }
    }

    private fun drawText(canvas: Canvas, layer: Layer.TextLayer, opacity: Float) {
        textPaint.color = applyAlpha(layer.colorHex.toInt(), opacity)
        textPaint.textSize = layer.fontSizeSp * 2.5f
        val fm = textPaint.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) / 2f
        canvas.drawText(layer.text, 0f, baselineOffset, textPaint)
    }

    private fun drawImage(canvas: Canvas, layer: Layer.ImageLayer, opacity: Float) {
        val bmp = bitmapCache.getOrPut(layer.uriString) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(layer.uriString))?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        } ?: return
        shapePaint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        val hw = layer.widthPx / 2f
        val hh = layer.heightPx / 2f
        canvas.drawBitmap(bmp, null, RectF(-hw, -hh, hw, hh), shapePaint)
    }

    private fun applyAlpha(argb: Int, opacity: Float): Int {
        val baseAlpha = (argb ushr 24) and 0xFF
        val finalAlpha = (baseAlpha * opacity).toInt().coerceIn(0, 255)
        return (finalAlpha shl 24) or (argb and 0x00FFFFFF)
    }
}
