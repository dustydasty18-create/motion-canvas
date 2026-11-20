package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.model.Layer
import com.mohammed.motioncanvas.model.TextStyleKind
import com.mohammed.motioncanvas.model.Project
import com.mohammed.motioncanvas.model.ShapeKind

val SWATCH_COLORS: List<Long> = listOf(
    0xFFFFFFFF, // White
    0xFF0F172A, // Deep Slate
    0xFF000000, // Pure Black
    0xFF3B82F6, // Electric Blue
    0xFF06B6D4, // Cyan
    0xFF10B981, // Emerald
    0xFFF59E0B, // Amber
    0xFFEF4444, // Coral Red
    0xFFEC4899, // Neon Pink
    0xFF8B5CF6  // Violet
)

fun Project.moveLayer(layerId: String, delta: Int): Project {
    val idx = layers.indexOfFirst { it.id == layerId }
    if (idx == -1) return this
    val targetIdx = (idx + delta).coerceIn(0, layers.lastIndex)
    if (idx == targetIdx) return this
    val mutable = layers.toMutableList()
    val item = mutable.removeAt(idx)
    mutable.add(targetIdx, item)
    return copy(layers = mutable)
}

fun Layer.withColor(colorHex: Long): Layer = when (this) {
    is Layer.TextLayer -> copy(colorHex = colorHex)
    is Layer.ShapeLayer -> copy(colorHex = colorHex)
    is Layer.ImageLayer -> this
}

fun Layer.withFontSize(sizeSp: Float): Layer = when (this) {
    is Layer.TextLayer -> copy(fontSizeSp = sizeSp)
    else -> this
}

fun Layer.withShapeKind(kind: ShapeKind): Layer = when (this) {
    is Layer.ShapeLayer -> copy(kind = kind)
    else -> this
}

fun Layer.withTextStyle(fontSizeSp: Float? = null, textStyle: TextStyleKind? = null): Layer =
    if (this is Layer.TextLayer) copy(
        fontSizeSp = fontSizeSp ?: this.fontSizeSp,
        textStyle = textStyle ?: this.textStyle
    ) else this

fun Layer.withShapeStroke(strokeWidthPx: Float? = null, strokeColorHex: Long? = null): Layer =
    if (this is Layer.ShapeLayer) copy(
        strokeWidthPx = strokeWidthPx ?: this.strokeWidthPx,
        strokeColorHex = strokeColorHex ?: this.strokeColorHex
    ) else this
