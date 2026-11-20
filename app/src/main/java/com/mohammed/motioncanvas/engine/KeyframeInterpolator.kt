package com.mohammed.motioncanvas.engine

import com.mohammed.motioncanvas.model.EasingType
import com.mohammed.motioncanvas.model.Keyframe
import com.mohammed.motioncanvas.model.TransformTrack
import kotlin.math.pow

data class EvaluatedTransform(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val opacity: Float
)

object KeyframeInterpolator {
    fun evaluateTrack(track: TransformTrack, localTimeMs: Long): EvaluatedTransform {
        return EvaluatedTransform(
            x = evaluate(track.x, localTimeMs, 540f),
            y = evaluate(track.y, localTimeMs, 960f),
            scaleX = evaluate(track.scaleX, localTimeMs, 1f),
            scaleY = evaluate(track.scaleY, localTimeMs, 1f),
            rotation = evaluate(track.rotation, localTimeMs, 0f),
            opacity = evaluate(track.opacity, localTimeMs, 1f).coerceIn(0f, 1f)
        )
    }

    fun evaluate(keyframes: List<Keyframe>, timeMs: Long, defaultValue: Float): Float {
        if (keyframes.isEmpty()) return defaultValue
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) return sorted.first().value
        if (timeMs >= sorted.last().timeMs) return sorted.last().value

        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeMs in k1.timeMs..k2.timeMs) {
                val span = (k2.timeMs - k1.timeMs).toFloat()
                if (span <= 0f) return k2.value
                val rawT = ((timeMs - k1.timeMs) / span).coerceIn(0f, 1f)
                val easedT = applyEasing(rawT, k2.easing)
                return k1.value + (k2.value - k1.value) * easedT
            }
        }
        return sorted.last().value
    }

    private fun applyEasing(t: Float, easing: EasingType): Float = when (easing) {
        EasingType.LINEAR -> t
        EasingType.EASE_IN -> t * t * t
        EasingType.EASE_OUT -> 1f - (1f - t).pow(3)
        EasingType.EASE_IN_OUT -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    }
}
