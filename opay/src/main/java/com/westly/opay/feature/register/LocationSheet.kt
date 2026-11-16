package com.westly.opay.feature.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet

private const val SHEET_HEIGHT_FRACTION = 0.8f
private val SHEET_VERTICAL_PADDING = 38.dp

/** Search box on top, one row per choice, a green tick on the chosen row. Used for state, LGA and area. */
@Composable
fun LocationSheet(
    visible: Boolean,
    items: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = OPay.colors
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        var query by remember { mutableStateOf("") }
        val shown = NigeriaLocations.filter(items, query)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight * SHEET_HEIGHT_FRACTION - SHEET_VERTICAL_PADDING)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBox(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Cancel",
                    style = OPayType.titleSmall,
                    color = c.green,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(start = 14.dp, top = 8.dp, bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            if (shown.isEmpty()) {
                Text(
                    text = "No results",
                    style = OPayType.body,
                    color = c.muted,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(shown, key = { it }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                style = OPayType.titleSmall,
                                color = if (item == selected) c.green else c.text,
                                modifier = Modifier.weight(1f)
                            )
                            if (item == selected) {
                                OPayIconView(icon = OPayIcon.Check, size = 20.dp, tint = c.green)
                            } else {
                                Box(Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    val c = OPay.colors
    val style = TextStyle(fontSize = 15.sp, color = c.text)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.cardAlt)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = "Search", style = style, color = c.muted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(c.green)
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Close, size = 14.dp, tint = c.muted)
            }
        }
    }
}
