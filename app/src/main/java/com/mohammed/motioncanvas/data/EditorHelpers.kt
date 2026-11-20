package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.model.*

enum class TrackProperty(val label: String, val min: Float, val max: Float) {
    X("Pos X", 0f, 1080f),
    Y("Pos Y", 0f, 1920f),
    SCALE("Scale", 0.1f, 3.0f),
    ROTATION("Rotate", -360f, 360f),
    OPACITY("Opacity", 0f, 1f)
}

fun List<Keyframe>.upsertKeyframe(timeMs: Long, value: Float, easing: EasingType): List<Keyframe> {
    val filtered = filterNot { kotlin.math.abs(it.timeMs - timeMs) < 30L }
    return (filtered + Keyframe(timeMs, value, easing)).sortedBy { it.timeMs }
}

fun Layer.withUpdatedTrack(
    prop: TrackProperty,
    timeMs: Long,
    value: Float,
    easing: EasingType
): Layer {
    val localTime = (timeMs - startTimeMs).coerceIn(0L, durationMs)
    val t = transform
    val newTrack = when (prop) {
        TrackProperty.X -> t.copy(x = t.x.upsertKeyframe(localTime, value, easing))
        TrackProperty.Y -> t.copy(y = t.y.upsertKeyframe(localTime, value, easing))
        TrackProperty.SCALE -> t.copy(
            scaleX = t.scaleX.upsertKeyframe(localTime, value, easing),
            scaleY = t.scaleY.upsertKeyframe(localTime, value, easing)
        )
        TrackProperty.ROTATION -> t.copy(rotation = t.rotation.upsertKeyframe(localTime, value, easing))
        TrackProperty.OPACITY -> t.copy(opacity = t.opacity.upsertKeyframe(localTime, value, easing))
    }
    return when (this) {
        is Layer.TextLayer -> copy(transform = newTrack)
        is Layer.ShapeLayer -> copy(transform = newTrack)
        is Layer.ImageLayer -> copy(transform = newTrack)
    }
}

fun Layer.clearKeyframesFor(prop: TrackProperty, defaultValue: Float): Layer {
    val single = listOf(Keyframe(0L, defaultValue))
    val t = transform
    val newTrack = when (prop) {
        TrackProperty.X -> t.copy(x = single)
        TrackProperty.Y -> t.copy(y = single)
        TrackProperty.SCALE -> t.copy(scaleX = single, scaleY = single)
        TrackProperty.ROTATION -> t.copy(rotation = single)
        TrackProperty.OPACITY -> t.copy(opacity = single)
    }
    return when (this) {
        is Layer.TextLayer -> copy(transform = newTrack)
        is Layer.ShapeLayer -> copy(transform = newTrack)
        is Layer.ImageLayer -> copy(transform = newTrack)
    }
}
