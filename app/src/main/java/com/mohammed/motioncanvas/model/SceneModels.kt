package com.mohammed.motioncanvas.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class EasingType { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

@Serializable
data class Keyframe(val timeMs: Long, val value: Float, val easing: EasingType = EasingType.EASE_IN_OUT)

@Serializable
data class TransformTrack(
    val x: List<Keyframe> = listOf(Keyframe(0L, 540f)),
    val y: List<Keyframe> = listOf(Keyframe(0L, 960f)),
    val scaleX: List<Keyframe> = listOf(Keyframe(0L, 1f)),
    val scaleY: List<Keyframe> = listOf(Keyframe(0L, 1f)),
    val rotation: List<Keyframe> = listOf(Keyframe(0L, 0f)),
    val opacity: List<Keyframe> = listOf(Keyframe(0L, 1f))
)

@Serializable
enum class ShapeKind { RECTANGLE, CIRCLE, ROUNDED_RECT }

@Serializable
sealed interface Layer {
    val id: String
    val name: String
    val startTimeMs: Long
    val durationMs: Long
    val transform: TransformTrack

    @Serializable @SerialName("text")
    data class TextLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Text",
        override val startTimeMs: Long = 0L,
        override val durationMs: Long = 3000L,
        override val transform: TransformTrack = TransformTrack(),
        val text: String = "Hello Motion",
        val fontSizeSp: Float = 64f,
        val colorHex: Long = 0xFFFFFFFF
    ) : Layer

    @Serializable @SerialName("shape")
    data class ShapeLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Shape",
        override val startTimeMs: Long = 0L,
        override val durationMs: Long = 3000L,
        override val transform: TransformTrack = TransformTrack(),
        val kind: ShapeKind = ShapeKind.ROUNDED_RECT,
        val widthPx: Float = 300f,
        val heightPx: Float = 300f,
        val cornerRadiusPx: Float = 36f,
        val colorHex: Long = 0xFF3B82F6
    ) : Layer

    @Serializable @SerialName("image")
    data class ImageLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Image",
        override val startTimeMs: Long = 0L,
        override val durationMs: Long = 3000L,
        override val transform: TransformTrack = TransformTrack(),
        val uriString: String,
        val widthPx: Float = 400f,
        val heightPx: Float = 400f
    ) : Layer
}

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Project",
    val widthPx: Int = 1080,
    val heightPx: Int = 1920,
    val fps: Int = 30,
    val durationMs: Long = 5000L,
    val backgroundColorHex: Long = 0xFF0F172A,
    val layers: List<Layer> = emptyList()
) {
    fun toJson(): String = projectJson.encodeToString(this)
    companion object {
        val projectJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(raw: String): Project = projectJson.decodeFromString(raw)
        fun sampleDemo(): Project = Project(
            title = "Demo Scene",
            layers = listOf(
                Layer.ShapeLayer(
                    name = "Blue Card",
                    transform = TransformTrack(
                        x = listOf(Keyframe(0L, 200f), Keyframe(2500L, 540f, EasingType.EASE_OUT)),
                        rotation = listOf(Keyframe(0L, 0f), Keyframe(2500L, 360f))
                    )
                ),
                Layer.TextLayer(
                    name = "Title",
                    text = "Motion Canvas v0.1",
                    transform = TransformTrack(
                        y = listOf(Keyframe(0L, 1250f)),
                        opacity = listOf(Keyframe(0L, 0f), Keyframe(1000L, 1f))
                    )
                )
            )
        )
    }
}
