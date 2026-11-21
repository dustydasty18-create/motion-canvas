package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.model.EasingType
import com.mohammed.motioncanvas.model.Keyframe
import com.mohammed.motioncanvas.model.Layer
import kotlin.math.abs

fun Layer.withGestureDelta(
    globalTimeMs: Long,
    dx: Float,
    dy: Float,
    zoom: Float,
    rotationDelta: Float,
    easing: EasingType
): Layer {
    val localMs = (globalTimeMs - startTimeMs).coerceIn(0L, durationMs)
    fun currentVal(list: List<Keyframe>, default: Float): Float {
        val sorted = list.sortedBy { it.timeMs }
        return sorted.lastOrNull { it.timeMs <= localMs }?.value
            ?: sorted.firstOrNull()?.value
            ?: default
    }
    fun upsert(list: List<Keyframe>, v: Float): List<Keyframe> {
        val filtered = list.filterNot { abs(it.timeMs - localMs) <= 16L }
        return (filtered + Keyframe(localMs, v, easing)).sortedBy { it.timeMs }
    }
    val tr = transform.copy(
        x = upsert(transform.x, currentVal(transform.x, 0f) + dx),
        y = upsert(transform.y, currentVal(transform.y, 0f) + dy),
        scaleX = upsert(transform.scaleX, (currentVal(transform.scaleX, 1f) * zoom).coerceIn(0.05f, 10f)),
        scaleY = upsert(transform.scaleY, (currentVal(transform.scaleY, 1f) * zoom).coerceIn(0.05f, 10f)),
        rotation = upsert(transform.rotation, currentVal(transform.rotation, 0f) + rotationDelta)
    )
    return when (this) {
        is Layer.TextLayer -> copy(transform = tr)
        is Layer.ShapeLayer -> copy(transform = tr)
        is Layer.ImageLayer -> copy(transform = tr)
        is Layer.VideoLayer -> copy(transform = tr)
    }
}
