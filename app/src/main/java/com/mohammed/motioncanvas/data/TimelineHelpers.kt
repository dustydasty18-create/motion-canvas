package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.model.Layer

fun Layer.withTiming(startTimeMs: Long, durationMs: Long): Layer = when (this) {
    is Layer.TextLayer -> copy(startTimeMs = startTimeMs, durationMs = durationMs)
    is Layer.ShapeLayer -> copy(startTimeMs = startTimeMs, durationMs = durationMs)
    is Layer.ImageLayer -> copy(startTimeMs = startTimeMs, durationMs = durationMs)
}

fun Layer.keyframeGlobalTimesFor(prop: TrackProperty): List<Long> {
    val track = when (prop) {
        TrackProperty.X -> transform.x
        TrackProperty.Y -> transform.y
        TrackProperty.SCALE -> transform.scaleX
        TrackProperty.ROTATION -> transform.rotation
        TrackProperty.OPACITY -> transform.opacity
    }
    return track.map { (startTimeMs + it.timeMs).coerceAtLeast(0L) }.distinct().sorted()
}
