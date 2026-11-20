package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.engine.KeyframeInterpolator
import com.mohammed.motioncanvas.model.EasingType
import com.mohammed.motioncanvas.model.Layer

fun Layer.withGestureDelta(
    timeMs: Long,
    dxCanvas: Float,
    dyCanvas: Float,
    zoomFactor: Float,
    rotationDeltaDeg: Float,
    easing: EasingType
): Layer {
    val localTime = (timeMs - startTimeMs).coerceIn(0L, durationMs)
    val cur = KeyframeInterpolator.evaluateTrack(transform, localTime)
    val newX = (cur.x + dxCanvas).coerceIn(0f, 1080f)
    val newY = (cur.y + dyCanvas).coerceIn(0f, 1920f)
    val newScale = (cur.scaleX * zoomFactor).coerceIn(0.1f, 3.0f)
    val newRot = ((cur.rotation + rotationDeltaDeg + 360f) % 720f) - 360f

    val t = transform
    val newTrack = t.copy(
        x = t.x.upsertKeyframe(localTime, newX, easing),
        y = t.y.upsertKeyframe(localTime, newY, easing),
        scaleX = t.scaleX.upsertKeyframe(localTime, newScale, easing),
        scaleY = t.scaleY.upsertKeyframe(localTime, newScale, easing),
        rotation = t.rotation.upsertKeyframe(localTime, newRot, easing)
    )
    return when (this) {
        is Layer.TextLayer -> copy(transform = newTrack)
        is Layer.ShapeLayer -> copy(transform = newTrack)
        is Layer.ImageLayer -> copy(transform = newTrack)
    }
}
