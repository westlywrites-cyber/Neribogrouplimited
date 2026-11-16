package com.westly.opay.feature.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val ART_UNITS = 130f
private val ArtShadow = Color(0x33000000)
private val ArtOutline = Color(0x66FFFFFF)
private val ArtGold = Color(0xFFF3CF6A)

@Composable
fun SkinArt(color: Color, modifier: Modifier = Modifier) {
    val body = remember { PathParser().parsePathString("M28 110c0-38 12-56 37-56s37 18 37 56z").toPath() }
    val moon = remember {
        PathParser().parsePathString("M52 40a20 20 0 1 0 28 18 16 16 0 0 1 -28 -18z").toPath()
    }
    Canvas(modifier = modifier.size(130.dp)) {
        val scale = size.minDimension / ART_UNITS
        withTransform({ scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) }) {
            drawOval(
                color = ArtShadow,
                topLeft = Offset(23f, 109f),
                size = Size(84f, 14f)
            )
            drawPath(path = body, color = color)
            drawPath(path = body, color = ArtOutline, style = Stroke(width = 2f))
            drawPath(path = moon, color = ArtGold)
            drawLine(
                color = ArtGold,
                start = Offset(48f, 80f),
                end = Offset(82f, 80f),
                strokeWidth = 3f,
                cap = StrokeCap.Butt
            )
            drawLine(
                color = ArtGold,
                start = Offset(52f, 92f),
                end = Offset(78f, 92f),
                strokeWidth = 3f,
                cap = StrokeCap.Butt
            )
        }
    }
}
