package com.westly.opay.feature.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet

private const val KYC_SHEET_MAX_HEIGHT_FRACTION = 0.7f

/** The green note under the title of a questionnaire page. */
@Composable
internal fun KycPill(text: String) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.greenSoft)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(text = text, style = OPayType.bodySmall, color = c.green)
    }
}

/** A question with two radio answers side by side. [value] is null until the person has answered. */
@Composable
internal fun KycRadioQuestion(
    question: String,
    value: Boolean?,
    onChange: (Boolean) -> Unit,
    yesLabel: String,
    noLabel: String,
    yesFirst: Boolean
) {
    val c = OPay.colors
    Text(text = question, style = OPayType.titleSmall, color = c.text)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (yesFirst) {
            KycRadioOption(yesLabel, value == true, { onChange(true) }, Modifier.weight(1f))
            KycRadioOption(noLabel, value == false, { onChange(false) }, Modifier.weight(1f))
        } else {
            KycRadioOption(noLabel, value == false, { onChange(false) }, Modifier.weight(1f))
            KycRadioOption(yesLabel, value == true, { onChange(true) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KycRadioOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = OPay.colors
    Row(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KycRadioDot(selected)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = OPayType.bodySmall,
            color = if (selected) c.green else c.text
        )
    }
}

@Composable
internal fun KycRadioDot(selected: Boolean) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .size(18.dp)
            .border(1.5.dp, if (selected) c.green else c.muted, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(c.green)
            )
        }
    }
}

/** A tappable field that opens a sheet or a date picker. Shows [placeholder] in grey until there is a value. */
@Composable
internal fun KycChoiceField(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cardAlt)
            .border(1.dp, c.divider, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            style = OPayType.titleSmall,
            color = if (value.isEmpty()) c.muted else c.text,
            modifier = Modifier.weight(1f)
        )
        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
    }
}

@Composable
internal fun KycCheckRow(checked: Boolean, text: String, onToggle: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) c.green else Color.Transparent)
                .border(1.5.dp, if (checked) c.green else c.muted, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                OPayIconView(icon = OPayIcon.Check, size = 13.dp, tint = c.onGreen)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = OPayType.bodySmall,
            color = c.text,
            modifier = Modifier.weight(1f)
        )
    }
}

/** A bottom sheet with a title, a close mark and one radio row per choice (reference B11 and B13). */
@Composable
internal fun KycChoiceSheet(
    visible: Boolean,
    title: String,
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = OPay.colors
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * KYC_SHEET_MAX_HEIGHT_FRACTION)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = OPayType.title,
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Close, size = 16.dp, tint = c.muted)
                }
            }
            LazyColumn {
                items(options, key = { it }) { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onPick(option) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = OPayType.titleSmall,
                            color = if (isSelected) c.green else c.text,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        KycRadioDot(isSelected)
                    }
                }
            }
        }
    }
}
