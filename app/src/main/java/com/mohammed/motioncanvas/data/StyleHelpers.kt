package com.mohammed.motioncanvas.data

import com.mohammed.motioncanvas.model.Layer
import com.mohammed.motioncanvas.model.TextStyleKind

fun Layer.withTextStyle(fontSizeSp: Float? = null, textStyle: TextStyleKind? = null): Layer =
    if (this is Layer.TextLayer) copy(
        fontSizeSp = fontSizeSp ?: this.fontSizeSp,
        textStyle = textStyle ?: this.textStyle
    ) else this

fun Layer.withShapeStroke(strokeWidthPx: Float? = null, strokeColorHex: Long? = null): Layer =
    if (this is Layer.ShapeLayer) copy(
        strokeWidthPx = strokeWidthPx ?: this.strokeWidthPx,
        strokeColorHex = strokeColorHex ?: this.strokeColorHex
    ) else this
