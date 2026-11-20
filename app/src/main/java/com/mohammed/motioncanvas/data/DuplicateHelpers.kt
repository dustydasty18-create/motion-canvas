package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.model.Layer
import java.util.UUID

fun Layer.duplicateLayer(): Layer {
    val newId = UUID.randomUUID().toString()
    val newName = "$name Copy"
    val offsetX = transform.x.map { it.copy(value = (it.value + 40f).coerceAtMost(1040f)) }
    val offsetY = transform.y.map { it.copy(value = (it.value + 40f).coerceAtMost(1880f)) }
    val newTransform = transform.copy(x = offsetX, y = offsetY)
    return when (this) {
        is Layer.TextLayer -> copy(id = newId, name = newName, transform = newTransform)
        is Layer.ShapeLayer -> copy(id = newId, name = newName, transform = newTransform)
        is Layer.ImageLayer -> copy(id = newId, name = newName, transform = newTransform)
    }
}
