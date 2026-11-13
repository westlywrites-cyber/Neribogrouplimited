package com.westly.neribogroup.feature.storageroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val ConsoleBack = Color(0xFF070A0B)
private val ConsoleHead = Color(0xFF0D1314)
private val ConsoleEdge = Color(0xFF1D2A2B)
private val ConsoleHeadText = Color(0xFF8FB3B0)
private val ConsoleButtonEdge = Color(0xFF2B3D3E)
private val ConsoleButtonText = Color(0xFFBFE3DF)
private val ConsoleText = Color(0xFFB7C9C7)
private val ConsoleTime = Color(0xFF5F7775)

private fun levelColor(level: RoomLevel): Color = when (level) {
    RoomLevel.INFO -> Color(0xFF5FD38A)
    RoomLevel.SYNC -> Color(0xFF6BB7FF)
    RoomLevel.WARN -> Color(0xFFFFC27A)
    RoomLevel.ERROR -> Color(0xFFFF8A80)
    RoomLevel.CHANGE -> Color(0xFFC7A8FF)
}

/**
 * The live console strip: time, level (INFO, SYNC, WARN, ERROR, CHANGE) and message, scrolling to the newest line
 * by itself. The last 200 lines are kept. Pause freezes what is shown (new lines are still kept), Clear empties it
 * and Expand makes it taller. The lines come from [StorageRoomLog], which only receives real events.
 */
@Composable
fun RoomConsole(modifier: Modifier = Modifier) {
    var paused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<RoomLine>>(emptyList()) }
    val scroll = rememberScrollState()

    val shown: List<RoomLine> = if (paused) frozen else StorageRoomLog.lines.toList()

    LaunchedEffect(shown.size, expanded, paused) {
        if (!paused) {
            delay(40L)
            scroll.scrollTo(scroll.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ConsoleBack)
            .border(1.dp, ConsoleEdge, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleHead)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Server console", color = ConsoleHeadText, fontSize = 12.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ConsoleButton(if (paused) "Resume" else "Pause") {
                    if (!paused) frozen = StorageRoomLog.lines.toList()
                    paused = !paused
                }
                ConsoleButton("Clear") {
                    StorageRoomLog.clear()
                    frozen = emptyList()
                }
                ConsoleButton(if (expanded) "Shrink" else "Expand") { expanded = !expanded }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (expanded) 360.dp else 128.dp)
                .verticalScroll(scroll)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (shown.isEmpty()) {
                Text(
                    "Waiting for the first event.",
                    color = ConsoleTime,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp
                )
            }
            for (line in shown) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = ConsoleTime)) { append(StorageRoomLog.timeText(line.millis) + " ") }
                        withStyle(SpanStyle(color = levelColor(line.level), fontWeight = FontWeight.Bold)) {
                            append(line.level.name.padEnd(6))
                        }
                        withStyle(SpanStyle(color = ConsoleText)) {
                            append(" " + line.source + ": " + line.message)
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ConsoleButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ConsoleButtonEdge, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 2.dp)
    ) {
        Text(label, color = ConsoleButtonText, fontSize = 11.5.sp)
    }
}
