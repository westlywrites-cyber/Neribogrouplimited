package com.westly.opay.core.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Solid (filled) glyphs used by the Home tiles, the tab bar and a few details.
 * They sit beside [OPayIcon]; the outline set is unchanged.
 */
enum class OPayGlyph {
    ToOpay, ToBank, Save, Loan,
    Airtime, Data, Betting, Tv, OWealth, LoanCentre, Invitation, More,
    HomeFilled, ShieldTick, Globe, GiftBox, CircleTick, TabWealth, TabMe
}

@Composable
fun OPayGlyphView(
    glyph: OPayGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current
) {
    Icon(
        imageVector = glyphVectors.getValue(glyph),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint
    )
}

private fun fillIcon(name: String, evenOdd: Boolean = true, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        pathBuilder = build
    ).build()

private fun lineIcon(name: String, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = build
    ).build()

private fun PathBuilder.gCircle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, false, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, false, -2 * r, 0f)
    close()
}

private fun PathBuilder.gRect(x: Float, y: Float, w: Float, h: Float) {
    moveTo(x, y)
    horizontalLineToRelative(w)
    verticalLineToRelative(h)
    horizontalLineToRelative(-w)
    close()
}

private fun PathBuilder.gRound(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    horizontalLineTo(x + w - r)
    arcToRelative(r, r, 0f, false, true, r, r)
    verticalLineTo(y + h - r)
    arcToRelative(r, r, 0f, false, true, -r, r)
    horizontalLineTo(x + r)
    arcToRelative(r, r, 0f, false, true, -r, -r)
    verticalLineTo(y + r)
    arcToRelative(r, r, 0f, false, true, r, -r)
    close()
}

private fun PathBuilder.phoneBody() {
    gRound(6.5f, 2.5f, 11f, 19f, 2.4f)
    gRect(8.5f, 5f, 7f, 11f)
    gCircle(12f, 18.7f, 1f)
}

private val glyphVectors: Map<OPayGlyph, ImageVector> by lazy {
    mapOf(
        OPayGlyph.ToOpay to fillIcon("ToOpay") {
            moveTo(7f, 3f)
            horizontalLineTo(17f)
            arcTo(4f, 4f, 0f, false, true, 21f, 7f)
            verticalLineTo(13f)
            arcTo(4f, 4f, 0f, false, true, 17f, 17f)
            horizontalLineTo(15f)
            lineTo(12f, 20.5f)
            lineTo(9f, 17f)
            horizontalLineTo(7f)
            arcTo(4f, 4f, 0f, false, true, 3f, 13f)
            verticalLineTo(7f)
            arcTo(4f, 4f, 0f, false, true, 7f, 3f)
            close()
            gCircle(12f, 8.3f, 2.3f)
            moveTo(7.8f, 14f)
            curveTo(8f, 11.6f, 10f, 10.9f, 12f, 10.9f)
            curveTo(14f, 10.9f, 16f, 11.6f, 16.2f, 14f)
            close()
        },
        OPayGlyph.ToBank to fillIcon("ToBank") {
            moveTo(12f, 2.5f)
            lineTo(21f, 8f)
            horizontalLineTo(3f)
            close()
            gRect(5f, 10f, 2.8f, 7f)
            gRect(10.6f, 10f, 2.8f, 7f)
            gRect(16.2f, 10f, 2.8f, 7f)
            gRect(3f, 18.5f, 18f, 2.5f)
        },
        OPayGlyph.Save to fillIcon("Save") {
            gRound(3f, 4f, 18f, 15f, 3f)
            gRect(5.2f, 9f, 1.6f, 5f)
            gCircle(13.5f, 11.5f, 4f)
            gCircle(13.5f, 11.5f, 1.7f)
            gRect(6f, 19f, 2.5f, 2f)
            gRect(15.5f, 19f, 2.5f, 2f)
        },
        OPayGlyph.Loan to fillIcon("Loan") {
            gCircle(12f, 7f, 4.2f)
            gCircle(12f, 7f, 1.8f)
            moveTo(2.5f, 14f)
            curveTo(6f, 13f, 9f, 14.5f, 12f, 17.5f)
            curveTo(15f, 14.5f, 18f, 13f, 21.5f, 14f)
            lineTo(21.5f, 16.3f)
            curveTo(18f, 15.6f, 15.5f, 17.5f, 12f, 21.5f)
            curveTo(8.5f, 17.5f, 6f, 15.6f, 2.5f, 16.3f)
            close()
        },
        OPayGlyph.Airtime to fillIcon("Airtime") {
            phoneBody()
            gRect(9.6f, 12.5f, 1.3f, 2.5f)
            gRect(11.4f, 10.5f, 1.3f, 4.5f)
            gRect(13.2f, 8.5f, 1.3f, 6.5f)
        },
        OPayGlyph.Data to fillIcon("Data") {
            phoneBody()
            moveTo(10.3f, 6f)
            lineTo(8.9f, 8.2f)
            lineTo(11.7f, 8.2f)
            close()
            gRect(9.9f, 8.2f, 0.8f, 3.2f)
            moveTo(13.7f, 15.4f)
            lineTo(12.3f, 13.2f)
            lineTo(15.1f, 13.2f)
            close()
            gRect(13.3f, 10f, 0.8f, 3.2f)
        },
        OPayGlyph.Betting to fillIcon("Betting") {
            gCircle(12f, 12f, 9.5f)
            gCircle(12f, 12f, 6.3f)
            gCircle(12f, 12f, 2.6f)
        },
        OPayGlyph.Tv to fillIcon("Tv") {
            gRound(3f, 7.5f, 18f, 13f, 2.5f)
            gRound(5f, 9.5f, 14f, 9f, 1.2f)
            moveTo(10.2f, 11.5f)
            lineTo(10.2f, 16.5f)
            lineTo(14.6f, 14f)
            close()
            moveTo(12f, 6.6f)
            lineTo(8.3f, 2.9f)
            lineTo(9.3f, 2f)
            lineTo(12f, 4.7f)
            lineTo(14.7f, 2f)
            lineTo(15.7f, 2.9f)
            close()
        },
        OPayGlyph.OWealth to fillIcon("OWealth", evenOdd = false) {
            moveTo(4.5f, 13f)
            arcTo(8f, 6.2f, 0f, false, true, 20.5f, 13f)
            arcTo(8f, 6.2f, 0f, false, true, 4.5f, 13f)
            close()
            moveTo(8.6f, 7.6f)
            lineTo(8.9f, 3.8f)
            lineTo(12f, 6.4f)
            close()
            gRect(7f, 17.5f, 2.6f, 3.5f)
            gRect(15.4f, 17.5f, 2.6f, 3.5f)
            gRect(19.5f, 11f, 2.5f, 3.4f)
        },
        OPayGlyph.LoanCentre to fillIcon("LoanCentre") {
            moveTo(9f, 2.5f)
            lineTo(15f, 2.5f)
            lineTo(13.4f, 6f)
            lineTo(10.6f, 6f)
            close()
            moveTo(10.6f, 6.2f)
            horizontalLineTo(13.4f)
            curveTo(17.4f, 9f, 19.5f, 13f, 19.5f, 16.2f)
            curveTo(19.5f, 19.6f, 16.5f, 21.6f, 12f, 21.6f)
            curveTo(7.5f, 21.6f, 4.5f, 19.6f, 4.5f, 16.2f)
            curveTo(4.5f, 13f, 6.6f, 9f, 10.6f, 6.2f)
            close()
            gCircle(12f, 15.2f, 3.4f)
            gRect(11.3f, 13.3f, 1.4f, 3.8f)
        },
        OPayGlyph.Invitation to fillIcon("Invitation", evenOdd = false) {
            moveTo(3f, 9.5f)
            horizontalLineTo(7.5f)
            lineTo(16f, 4.6f)
            verticalLineTo(19.4f)
            lineTo(7.5f, 14.5f)
            horizontalLineTo(3f)
            close()
            gRect(6f, 15.2f, 3f, 5f)
            gRect(17.6f, 8.5f, 3.2f, 7f)
        },
        OPayGlyph.More to fillIcon("More", evenOdd = false) {
            moveTo(12f, 2.5f)
            lineTo(18.5f, 5.4f)
            lineTo(12f, 8.3f)
            lineTo(5.5f, 5.4f)
            close()
            moveTo(8f, 8f)
            verticalLineTo(10f)
            curveTo(9.6f, 11.3f, 14.4f, 11.3f, 16f, 10f)
            verticalLineTo(8f)
            lineTo(12f, 9.8f)
            close()
            gCircle(7f, 16f, 3.4f)
            gRect(5.6f, 19.6f, 2.8f, 1.6f)
            gRound(12.5f, 13f, 8.5f, 7.5f, 1.6f)
        },
        OPayGlyph.HomeFilled to fillIcon("HomeFilled") {
            gCircle(12f, 12f, 10f)
            gCircle(12f, 12f, 6.2f)
            gCircle(12f, 12f, 2.8f)
        },
        OPayGlyph.ShieldTick to fillIcon("ShieldTick") {
            moveTo(12f, 2.5f)
            lineTo(20f, 5.5f)
            verticalLineTo(11f)
            curveTo(20f, 16f, 16.5f, 19.8f, 12f, 21.5f)
            curveTo(7.5f, 19.8f, 4f, 16f, 4f, 11f)
            verticalLineTo(5.5f)
            close()
            moveTo(8f, 11.8f)
            lineTo(9.3f, 10.5f)
            lineTo(11.2f, 12.4f)
            lineTo(15f, 8.5f)
            lineTo(16.3f, 9.8f)
            lineTo(11.2f, 15f)
            close()
        },
        OPayGlyph.GiftBox to fillIcon("GiftBox") {
            gRect(4f, 10f, 16f, 10.5f)
            gRect(3f, 7f, 18f, 3f)
            gRect(11f, 7f, 2f, 13.5f)
            gCircle(9.5f, 4.6f, 2.2f)
            gCircle(14.5f, 4.6f, 2.2f)
        },
        OPayGlyph.CircleTick to lineIcon("CircleTick") {
            gCircle(12f, 12f, 9f)
            moveTo(7.8f, 12.3f)
            lineTo(10.7f, 15.2f)
            lineTo(16.4f, 9.2f)
        },
        OPayGlyph.Globe to lineIcon("Globe") {
            gCircle(12f, 12f, 9f)
            moveTo(3f, 12f)
            horizontalLineTo(21f)
            moveTo(12f, 3f)
            curveTo(8f, 7f, 8f, 17f, 12f, 21f)
            curveTo(16f, 17f, 16f, 7f, 12f, 3f)
        },
        OPayGlyph.TabWealth to lineIcon("TabWealth") {
            gRound(3f, 3f, 18f, 18f, 4f)
            moveTo(7f, 15.5f)
            lineTo(10.5f, 11.5f)
            lineTo(13f, 13.5f)
            lineTo(17f, 8.5f)
        },
        OPayGlyph.TabMe to lineIcon("TabMe") {
            gCircle(12f, 12f, 9f)
            moveTo(9f, 15f)
            horizontalLineTo(15f)
        }
    )
}
