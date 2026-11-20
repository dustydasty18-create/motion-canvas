package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.model.Layer
import com.mohammed.motioncanvas.model.LayerEffects

fun Layer.withEffects(effects: LayerEffects): Layer = when (this) {
    is Layer.TextLayer -> copy(effects = effects)
    is Layer.ShapeLayer -> copy(effects = effects)
    is Layer.ImageLayer -> copy(effects = effects)
}
