package com.westly.opay.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val DOT_COUNT = 9
private const val MIN_DOTS = 4
private const val HINT_DRAW = "Draw Pattern"
private const val HINT_AGAIN = "Draw the pattern again"
private const val HINT_SHORT = "Connect at least 4 dots"
private const val HINT_MISMATCH = "Patterns did not match. Draw the pattern again"

@Composable
internal fun PatternOverlay(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val saved by rememberUpdatedState(onSaved)
    val selected = remember { mutableStateListOf<Int>() }
    var firstPattern by remember { mutableStateOf<List<Int>?>(null) }
    var hint by remember { mutableStateOf(HINT_DRAW) }
    var warning by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val hitRadius = with(LocalDensity.current) { 34.dp.toPx() }

    BackHandler(onBack = onClose)

    fun begin(point: Offset) {
        selected.clear()
        finger = point
        val hit = hitDot(point, gridSize, hitRadius)
        if (hit != null) selected.add(hit)
    }

    fun move(point: Offset) {
        finger = point
        val hit = hitDot(point, gridSize, hitRadius)
        if (hit != null && hit !in selected) selected.add(hit)
    }

    fun finish() {
        finger = null
        val drawn = selected.toList()
        selected.clear()
        if (drawn.isEmpty()) return
        val first = firstPattern
        when {
            drawn.size < MIN_DOTS -> {
                hint = HINT_SHORT
                warning = true
            }
            first == null -> {
                firstPattern = drawn
                hint = HINT_AGAIN
                warning = false
            }
            first == drawn -> saved()
            else -> {
                firstPattern = null
                hint = HINT_MISMATCH
                warning = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Close"; role = Role.Button }
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
                }
                Text(
                    text = "Set Pattern",
                    style = OPayType.title,
                    color = c.text,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                DottedIcon(tint = c.green)
                Text(
                    text = hint,
                    style = OPayType.body,
                    color = if (warning) c.danger else c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp)
                )
                Spacer(Modifier.height(36.dp))
                Canvas(
                    modifier = Modifier
                        .size(280.dp)
                        .onSizeChanged { gridSize = it }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                begin(down.position)
                                drag(down.id) { change ->
                                    change.consume()
                                    move(change.position)
                                }
                                finish()
                            }
                        }
                ) {
                    val lineWidth = 4.dp.toPx()
                    val points = selected.map { dotCenter(it, size.width, size.height) }
                    points.zipWithNext().forEach { (from, to) ->
                        drawLine(
                            color = c.green,
                            start = from,
                            end = to,
                            strokeWidth = lineWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    val tail = finger
                    if (tail != null && points.isNotEmpty()) {
                        drawLine(
                            color = c.green,
                            start = points.last(),
                            end = tail,
                            strokeWidth = lineWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    for (index in 0 until DOT_COUNT) {
                        val centre = dotCenter(index, size.width, size.height)
                        if (index in selected) {
                            drawCircle(color = c.green, radius = 9.dp.toPx(), center = centre)
                            drawCircle(
                                color = c.green.copy(alpha = 0.35f),
                                radius = 22.dp.toPx(),
                                center = centre,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        } else {
                            drawCircle(color = c.muted, radius = 6.dp.toPx(), center = centre)
                        }
                    }
                }
            }
        }
    }
}

private fun dotCenter(index: Int, width: Float, height: Float): Offset {
    val column = index % 3
    val row = index / 3
    return Offset(width / 3f * (column + 0.5f), height / 3f * (row + 0.5f))
}

private fun hitDot(point: Offset, size: IntSize, radius: Float): Int? {
    for (index in 0 until DOT_COUNT) {
        val centre = dotCenter(index, size.width.toFloat(), size.height.toFloat())
        if ((centre - point).getDistance() <= radius) return index
    }
    return null
}

@Composable
private fun DottedIcon(tint: Color) {
    Canvas(modifier = Modifier.size(28.dp)) {
        val step = size.width / 3f
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                drawCircle(
                    color = tint,
                    radius = 2.dp.toPx(),
                    center = Offset(step * (column + 0.5f), step * (row + 0.5f))
                )
            }
        }
    }
}
