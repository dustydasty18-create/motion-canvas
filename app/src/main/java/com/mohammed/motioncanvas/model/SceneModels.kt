package com.mohammed.motioncanvas.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class EasingType { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

@Serializable
enum class BlendModeKind { NORMAL, ADD, SCREEN, MULTIPLY, OVERLAY }

@Serializable
data class LayerEffects(
    val shadowRadius: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 12f,
    val shadowColorHex: Long = 0xAA000000,
    val blurRadius: Float = 0f,
    val blendMode: BlendModeKind = BlendModeKind.NORMAL
)

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
enum class TextStyleKind { BOLD, NORMAL, ITALIC, MONOSPACE }

@Serializable
enum class ShapeKind { RECTANGLE, CIRCLE, ROUNDED_RECT }

@Serializable
data class AudioTrack(
    val uriString: String,
    val name: String = "Audio Track",
    val startTimeMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val volume: Float = 1f
)

@Serializable
sealed interface Layer {
    val id: String
    val name: String
    val startTimeMs: Long
    val durationMs: Long
    val transform: TransformTrack
    val effects: LayerEffects
    val visible: Boolean get() = true
    val locked: Boolean get() = false
    val parentId: String? get() = null
    val anchorX: Float get() = 0f
    val anchorY: Float get() = 0f
    val skewX: Float get() = 0f
    val skewY: Float get() = 0f

    @Serializable @SerialName("text")
    data class TextLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Text",
        override val startTimeMs: Long = 0L,
        override val durationMs: Long = 3000L,
        override val transform: TransformTrack = TransformTrack(),
        override val effects: LayerEffects = LayerEffects(),
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val parentId: String? = null,
    override val anchorX: Float = 0f,
    override val anchorY: Float = 0f,
    override val skewX: Float = 0f,
    override val skewY: Float = 0f,
        val text: String = "Hello Motion",
        val fontSizeSp: Float = 64f,
        val textStyle: TextStyleKind = TextStyleKind.BOLD,
        val colorHex: Long = 0xFFFFFFFF
    ) : Layer

    @Serializable @SerialName("shape")
    data class ShapeLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Shape",
        override val startTimeMs: Long = 0L,
        override val durationMs: Long = 3000L,
        override val transform: TransformTrack = TransformTrack(),
        override val effects: LayerEffects = LayerEffects(),
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val parentId: String? = null,
    override val anchorX: Float = 0f,
    override val anchorY: Float = 0f,
    override val skewX: Float = 0f,
    override val skewY: Float = 0f,
        val kind: ShapeKind = ShapeKind.ROUNDED_RECT,
        val widthPx: Float = 300f,
        val heightPx: Float = 300f,
        val cornerRadiusPx: Float = 36f,
        val strokeWidthPx: Float = 0f,
        val strokeColorHex: Long = 0xFFFFFFFF,
        val colorHex: Long = 0xFF3B82F6
    ) : Layer

    @Serializable @SerialName("image")
    data class ImageLayer(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String = "Image",
        override val startTimeMs: Long = 0L,
        override val durationMs: Long = 3000L,
        override val transform: TransformTrack = TransformTrack(),
        override val effects: LayerEffects = LayerEffects(),
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val parentId: String? = null,
    override val anchorX: Float = 0f,
    override val anchorY: Float = 0f,
    override val skewX: Float = 0f,
    override val skewY: Float = 0f,
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
    val layers: List<Layer> = emptyList(),
    val audio: AudioTrack? = null
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
