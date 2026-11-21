package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.engine.KeyframeEvaluator
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
    val cur = KeyframeEvaluator.evaluateTransform(transform, localMs)
    fun upsert(list: List<Keyframe>, v: Float): List<Keyframe> {
        val filtered = list.filterNot { abs(it.timeMs - localMs) <= 16L }
        return (filtered + Keyframe(localMs, v, easing)).sortedBy { it.timeMs }
    }
    val tr = transform.copy(
        x = upsert(transform.x, cur.x + dx),
        y = upsert(transform.y, cur.y + dy),
        scaleX = upsert(transform.scaleX, (cur.scaleX * zoom).coerceIn(0.05f, 10f)),
        scaleY = upsert(transform.scaleY, (cur.scaleY * zoom).coerceIn(0.05f, 10f)),
        rotation = upsert(transform.rotation, cur.rotation + rotationDelta)
    )
    return when (this) {
        is Layer.TextLayer -> copy(transform = tr)
        is Layer.ShapeLayer -> copy(transform = tr)
        is Layer.ImageLayer -> copy(transform = tr)
        is Layer.VideoLayer -> copy(transform = tr)
    }
}
