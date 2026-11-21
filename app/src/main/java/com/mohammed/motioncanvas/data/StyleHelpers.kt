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
    is Layer.ImageLayer, is Layer.VideoLayer -> this
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

fun Layer.withAeProps(
    visible: Boolean? = null,
    locked: Boolean? = null,
    parentId: String? = this.parentId,
    anchorX: Float? = null,
    anchorY: Float? = null,
    skewX: Float? = null,
    skewY: Float? = null
): Layer = when (this) {
    is Layer.TextLayer -> copy(visible = visible ?: this.visible, locked = locked ?: this.locked, parentId = parentId, anchorX = anchorX ?: this.anchorX, anchorY = anchorY ?: this.anchorY, skewX = skewX ?: this.skewX, skewY = skewY ?: this.skewY)
    is Layer.ShapeLayer -> copy(visible = visible ?: this.visible, locked = locked ?: this.locked, parentId = parentId, anchorX = anchorX ?: this.anchorX, anchorY = anchorY ?: this.anchorY, skewX = skewX ?: this.skewX, skewY = skewY ?: this.skewY)
    is Layer.ImageLayer -> copy(visible = visible ?: this.visible, locked = locked ?: this.locked, parentId = parentId, anchorX = anchorX ?: this.anchorX, anchorY = anchorY ?: this.anchorY, skewX = skewX ?: this.skewX, skewY = skewY ?: this.skewY)
    is Layer.VideoLayer -> copy(visible = visible ?: this.visible, locked = locked ?: this.locked, parentId = parentId, anchorX = anchorX ?: this.anchorX, anchorY = anchorY ?: this.anchorY, skewX = skewX ?: this.skewX, skewY = skewY ?: this.skewY)
}

fun Layer.withVideoTrim(trimStartMs: Long? = null, speed: Float? = null): Layer =
    if (this is Layer.VideoLayer) copy(trimStartMs = trimStartMs ?: this.trimStartMs, speed = speed ?: this.speed) else this

fun Layer.withEffects(effects: com.mohammed.motioncanvas.model.LayerEffects): Layer = when (this) {
    is Layer.TextLayer -> copy(effects = effects)
    is Layer.ShapeLayer -> copy(effects = effects)
    is Layer.ImageLayer -> copy(effects = effects)
    is Layer.VideoLayer -> copy(effects = effects)
}

fun Layer.withUpdatedTrack(
    prop: com.mohammed.motioncanvas.model.TrackProperty,
    globalTimeMs: Long,
    value: Float,
    easing: com.mohammed.motioncanvas.model.EasingType
): Layer {
    val localMs = (globalTimeMs - startTimeMs).coerceIn(0L, durationMs)
    fun upsert(list: List<com.mohammed.motioncanvas.model.Keyframe>): List<com.mohammed.motioncanvas.model.Keyframe> {
        val filtered = list.filterNot { kotlin.math.abs(it.timeMs - localMs) <= 16L }
        return (filtered + com.mohammed.motioncanvas.model.Keyframe(localMs, value, easing)).sortedBy { it.timeMs }
    }
    val tr = when (prop) {
        com.mohammed.motioncanvas.model.TrackProperty.X -> transform.copy(x = upsert(transform.x))
        com.mohammed.motioncanvas.model.TrackProperty.Y -> transform.copy(y = upsert(transform.y))
        com.mohammed.motioncanvas.model.TrackProperty.SCALE -> transform.copy(scaleX = upsert(transform.scaleX), scaleY = upsert(transform.scaleY))
        com.mohammed.motioncanvas.model.TrackProperty.ROTATION -> transform.copy(rotation = upsert(transform.rotation))
        com.mohammed.motioncanvas.model.TrackProperty.OPACITY -> transform.copy(opacity = upsert(transform.opacity))
    }
    return when (this) {
        is Layer.TextLayer -> copy(transform = tr)
        is Layer.ShapeLayer -> copy(transform = tr)
        is Layer.ImageLayer -> copy(transform = tr)
        is Layer.VideoLayer -> copy(transform = tr)
    }
}

fun Layer.keyframeGlobalTimesFor(prop: com.mohammed.motioncanvas.model.TrackProperty): List<Long> {
    val list = when (prop) {
        com.mohammed.motioncanvas.model.TrackProperty.X -> transform.x
        com.mohammed.motioncanvas.model.TrackProperty.Y -> transform.y
        com.mohammed.motioncanvas.model.TrackProperty.SCALE -> transform.scaleX
        com.mohammed.motioncanvas.model.TrackProperty.ROTATION -> transform.rotation
        com.mohammed.motioncanvas.model.TrackProperty.OPACITY -> transform.opacity
    }
    return list.map { startTimeMs + it.timeMs }.distinct().sorted()
}


fun Layer.withStartTimeMs(newStartMs: Long): Layer {
    val s = newStartMs.coerceAtLeast(0L)
    return when (this) {
        is Layer.TextLayer -> copy(startTimeMs = s)
        is Layer.ShapeLayer -> copy(startTimeMs = s)
        is Layer.ImageLayer -> copy(startTimeMs = s)
        is Layer.VideoLayer -> copy(startTimeMs = s)
    }
}
