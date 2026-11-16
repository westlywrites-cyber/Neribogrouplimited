package com.westly.opay.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object OPayShapes {
    val card = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(6.dp)
    val pill = RoundedCornerShape(26.dp)
    val sheet = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val tile = RoundedCornerShape(10.dp)
}

val OPayMaterialShapes = Shapes(
    extraSmall = OPayShapes.chip,
    small = OPayShapes.tile,
    medium = OPayShapes.card,
    large = OPayShapes.sheet,
    extraLarge = OPayShapes.pill
)
