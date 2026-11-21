package com.mohammed.motioncanvas.engine
import com.mohammed.motioncanvas.model.TextStyleKind

import com.mohammed.motioncanvas.model.BlendModeKind
import com.mohammed.motioncanvas.model.LayerEffects

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
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
    private val retrieverCache = mutableMapOf<String, MediaMetadataRetriever?>()
    private var lastVideoKey = ""
    private var lastVideoBmp: Bitmap? = null

    fun renderFrame(canvas: Canvas, project: Project, timeMs: Long) {
        canvas.drawColor(project.backgroundColorHex.toInt())

        fun applyParentChain(l: Layer, depth: Int = 0) {
            if (depth > 8) return
            val pid = l.parentId ?: return
            val parent = project.layers.find { it.id == pid } ?: return
            applyParentChain(parent, depth + 1)
            val pt = KeyframeInterpolator.evaluateTrack(parent.transform, (timeMs - parent.startTimeMs).coerceIn(0L, parent.durationMs))
            canvas.translate(pt.x, pt.y)
            canvas.rotate(pt.rotation)
            canvas.scale(pt.scaleX, pt.scaleY)
            canvas.skew(parent.skewX, parent.skewY)
            canvas.translate(-parent.anchorX, -parent.anchorY)
        }
        for (layer in project.layers) {
            if (!layer.visible) continue
            val localTime = timeMs - layer.startTimeMs
            if (localTime < 0L || localTime > layer.durationMs) continue

            val tr = KeyframeInterpolator.evaluateTrack(layer.transform, localTime)
            if (tr.opacity <= 0.001f) continue

            val useLayer = layer.effects.blendMode != BlendModeKind.NORMAL || layer.effects.blurRadius > 0.5f; if (useLayer) canvas.saveLayer(null, null) else canvas.save()
            applyParentChain(layer)
            canvas.translate(tr.x, tr.y)
            canvas.rotate(tr.rotation)
            canvas.scale(tr.scaleX, tr.scaleY)
            canvas.skew(layer.skewX, layer.skewY)
            canvas.translate(-layer.anchorX, -layer.anchorY)

            when (layer) {
                is Layer.ShapeLayer -> drawShape(canvas, layer, tr.opacity)
                is Layer.TextLayer -> drawText(canvas, layer, tr.opacity)
                is Layer.ImageLayer -> drawImage(canvas, layer, tr.opacity)
                is Layer.VideoLayer -> drawVideo(canvas, layer, localTime, tr.opacity)
            }
            canvas.restore()
        }
    }

    
    private fun drawVideo(canvas: Canvas, layer: Layer.VideoLayer, localTimeMs: Long, opacity: Float) {
        val retriever = retrieverCache.getOrPut(layer.uriString) {
            runCatching { MediaMetadataRetriever().apply { setDataSource(context, Uri.parse(layer.uriString)) } }.getOrNull()
        } ?: return
        val sourceMs = layer.trimStartMs + (localTimeMs * layer.speed).toLong()
        val bucketMs = (sourceMs / 33L) * 33L
        val key = "${layer.uriString}_$bucketMs"
        val bmp = if (key == lastVideoKey && lastVideoBmp != null) lastVideoBmp else {
            val f = runCatching { retriever.getFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
            lastVideoKey = key; lastVideoBmp = f; f
        }
        if (bmp != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            applyEffects(paint, layer.effects, opacity)
            val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
            val targetH = layer.heightPx
            val targetW = targetH * aspect
            val dst = RectF(-targetW / 2f, -targetH / 2f, targetW / 2f, targetH / 2f)
            canvas.drawBitmap(bmp, null, dst, paint)
        }
    }

    private fun applyEffects(paint: Paint, fx: LayerEffects, opacity: Float) {
        paint.xfermode = when (fx.blendMode) {
            BlendModeKind.NORMAL -> null
            BlendModeKind.ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
            BlendModeKind.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            BlendModeKind.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            BlendModeKind.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        }
        paint.maskFilter = if (fx.blurRadius > 0.5f) BlurMaskFilter(fx.blurRadius, BlurMaskFilter.Blur.NORMAL) else null
        if (fx.shadowRadius > 0.5f) paint.setShadowLayer(fx.shadowRadius, fx.shadowDx, fx.shadowDy, applyAlpha(fx.shadowColorHex.toInt(), opacity))
        else paint.clearShadowLayer()
    }

    private fun drawShape(canvas: Canvas, layer: Layer.ShapeLayer, opacity: Float) {
        shapePaint.color = applyAlpha(layer.colorHex.toInt(), opacity); applyEffects(shapePaint, layer.effects, opacity)
        val hw = layer.widthPx / 2f
        val hh = layer.heightPx / 2f
        val rect = RectF(-hw, -hh, hw, hh)
        when (layer.kind) {
            ShapeKind.RECTANGLE -> canvas.drawRect(rect, shapePaint)
            ShapeKind.ROUNDED_RECT -> canvas.drawRoundRect(rect, layer.cornerRadiusPx, layer.cornerRadiusPx, shapePaint)
            ShapeKind.CIRCLE -> canvas.drawCircle(0f, 0f, min(hw, hh), shapePaint)
        }
        if (layer.strokeWidthPx > 0.5f) { shapePaint.style = Paint.Style.STROKE; shapePaint.strokeWidth = layer.strokeWidthPx; shapePaint.color = applyAlpha(layer.strokeColorHex.toInt(), opacity); shapePaint.clearShadowLayer(); when (layer.kind) { ShapeKind.RECTANGLE -> canvas.drawRect(rect, shapePaint); ShapeKind.ROUNDED_RECT -> canvas.drawRoundRect(rect, layer.cornerRadiusPx, layer.cornerRadiusPx, shapePaint); ShapeKind.CIRCLE -> canvas.drawCircle(0f, 0f, min(hw, hh), shapePaint) }; shapePaint.style = Paint.Style.FILL }
    }

    private fun drawText(canvas: Canvas, layer: Layer.TextLayer, opacity: Float) {
        textPaint.color = applyAlpha(layer.colorHex.toInt(), opacity); applyEffects(textPaint, layer.effects, opacity)
        textPaint.textSize = layer.fontSizeSp * 2.5f
        textPaint.typeface = when (layer.textStyle) { TextStyleKind.BOLD -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD); TextStyleKind.NORMAL -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); TextStyleKind.ITALIC -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC); TextStyleKind.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
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
        shapePaint.alpha = (opacity * 255).toInt().coerceIn(0, 255); applyEffects(shapePaint, layer.effects, opacity)
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
