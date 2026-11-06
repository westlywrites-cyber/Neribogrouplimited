package com.westly.neribogroup.feature.common

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira
import com.westly.neribogroup.ui.theme.extras

// ---------------------------------------------------------------------------
// Shared spacing and shape constants (reuse these on every screen)
// ---------------------------------------------------------------------------

val ScreenPadding = 16.dp
val SectionGap = 24.dp
val TitleGap = 10.dp
val CardPadding = 16.dp
val HeroPadding = 20.dp
val RowGap = 14.dp
val FieldGap = 16.dp

val CardRadius = 20.dp
val FieldRadius = 14.dp
val ButtonRadius = 16.dp
val DialogRadius = 28.dp

@Composable
fun profitColor(value: Long): Color =
    if (value < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

// ---------------------------------------------------------------------------
// Titles, cards and stats
// ---------------------------------------------------------------------------

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** A flat card: 20dp radius, card colour, no elevation, no border. */
@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    padding: Dp = CardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

/** One stat: muted label above, value below. No card of its own. */
@Composable
fun StatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor
        )
    }
}

/** A single stat shown in its own flat card (kept so existing screens still compile). */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    FlatCard(modifier = modifier) {
        StatColumn(label = label, value = value, valueColor = valueColor)
    }
}

data class StatCardItem(
    val label: String,
    val value: String,
    val valueColor: Color? = null
)

/**
 * One flat card holding several stats in equal columns.
 * With more items than [columns] the stats wrap onto further rows.
 */
@Composable
fun StatCard(
    items: List<StatCardItem>,
    modifier: Modifier = Modifier,
    columns: Int = items.size.coerceAtLeast(1)
) {
    FlatCard(modifier = modifier) {
        items.chunked(columns).forEachIndexed { index, rowItems ->
            if (index > 0) Spacer(modifier = Modifier.height(RowGap))
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    StatColumn(
                        label = item.label,
                        value = item.value,
                        modifier = Modifier.weight(1f),
                        valueColor = item.valueColor ?: MaterialTheme.colorScheme.onSurface
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 8dp dot followed by a coloured, semibold label. Used for Paid, Unpaid, Overdue, plans... */
@Composable
fun StatusDot(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// ---------------------------------------------------------------------------
// Fields
// ---------------------------------------------------------------------------

@Composable
fun neriboFieldColors(): TextFieldColors {
    val cs = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = cs.surfaceContainerHigh,
        unfocusedContainerColor = cs.surfaceContainerHigh,
        errorContainerColor = cs.surfaceContainerHigh,
        disabledContainerColor = cs.surfaceContainerHigh,
        focusedTextColor = cs.onSurface,
        unfocusedTextColor = cs.onSurface,
        errorTextColor = cs.onSurface,
        cursorColor = cs.primary,
        errorCursorColor = cs.error,
        focusedIndicatorColor = cs.primary,
        unfocusedIndicatorColor = Color.Transparent,
        errorIndicatorColor = cs.error,
        disabledIndicatorColor = Color.Transparent
    )
}

/** Text field with a static muted label above it and an optional inline error line below. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    errorText: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            keyboardOptions = keyboardOptions,
            isError = errorText != null,
            shape = RoundedCornerShape(FieldRadius),
            colors = neriboFieldColors()
        )
        if (errorText != null) {
            Text(
                text = errorText,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** A number box that only accepts digits. Empty means 0. */
@Composable
fun NumberField(
    label: String,
    value: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxDigits: Int = 15
) {
    var text by remember { mutableStateOf(if (value == 0L) "" else value.toString()) }
    LabeledField(
        label = label,
        value = text,
        onValueChange = { raw ->
            val clean = raw.filter { it.isDigit() }.take(maxDigits)
            text = clean
            onChange(clean.toLongOrNull() ?: 0L)
        },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text("Remove", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun EntryRow(entry: Entry, editing: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RowGap / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = (if (entry.auto) "Auto · " else "") + formatDate(entry.dateMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = (if (entry.isIncome) "+" else "-") + formatNaira(entry.amount),
            style = MaterialTheme.typography.titleSmall,
            color = if (entry.isIncome) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        if (editing) {
            Spacer(modifier = Modifier.width(4.dp))
            RemoveButton(onClick = onDelete)
        }
    }
}

/** A row with a title, optional small line under it, an amount and (while editing) a Remove button. */
@Composable
fun MoneyRow(
    title: String,
    subtitle: String?,
    amountText: String,
    editing: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RowGap / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleSmall,
            color = if (amountText.startsWith("-")) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
        if (editing) {
            Spacer(modifier = Modifier.width(4.dp))
            RemoveButton(onClick = onDelete)
        }
    }
}

// ---------------------------------------------------------------------------
// Buttons and bars
// ---------------------------------------------------------------------------

/**
 * The primary action: a tinted block with accent text.
 * [destructive] uses the red tint. A disabled button is shown at 40% strength.
 */
@Composable
fun TintedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val container = if (destructive) MaterialTheme.extras.dangerContainer else cs.primaryContainer
    val content = if (destructive) cs.error else cs.onPrimaryContainer
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.4f)
        )
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

/** Bottom bar shown only while editing. */
@Composable
fun SaveBar(onCancel: () -> Unit, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(ButtonRadius)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onSave,
                modifier = Modifier
                    .weight(1.6f)
                    .height(52.dp),
                shape = RoundedCornerShape(ButtonRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

/** The one dialog look used everywhere: dimmed background, 28dp card, title, optional subtitle. */
@Composable
fun NeriboDialog(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        SideEffect {
            val provider = (view.parent as? DialogWindowProvider) ?: (view as? DialogWindowProvider)
            val window = provider?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0.6f)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .imePadding(),
            shape = RoundedCornerShape(DialogRadius),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                content()
            }
        }
    }
}

/** The action row at the bottom of a dialog, right-aligned. */
@Composable
fun DialogActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun NameDialog(
    heading: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    NeriboDialog(onDismiss = onDismiss, title = heading) {
        LabeledField(
            label = label,
            value = name,
            onValueChange = { name = it.take(40) }
        )
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Add",
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            )
        }
    }
}

@Composable
fun AddEntryDialog(
    heading: String,
    onDismiss: () -> Unit,
    onAdd: (String, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val value = amount.toLongOrNull()

    NeriboDialog(onDismiss = onDismiss, title = heading) {
        LabeledField(
            label = "Description",
            value = title,
            onValueChange = { title = it.take(60) }
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Amount (₦)",
            value = amount,
            onValueChange = { raw -> amount = raw.filter { it.isDigit() }.take(15) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Add",
                onClick = { if (value != null) onAdd(title.trim(), value) },
                enabled = value != null && value > 0 && title.isNotBlank()
            )
        }
    }
}

/** Adds something that expires. Reminder days are typed like "30, 7, 2". */
@Composable
fun AddRenewalDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, amount: Long, days: Int, reminders: List<Int>, recordCost: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var reminders by remember { mutableStateOf("30, 7, 2") }
    var recordCost by remember { mutableStateOf(true) }

    val dayCount = days.toIntOrNull() ?: 0
    val amountValue = amount.toLongOrNull() ?: 0L

    NeriboDialog(onDismiss = onDismiss, title = "Add renewal") {
        LabeledField(
            label = "What did you pay for?",
            value = name,
            onValueChange = { name = it.take(60) }
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Amount paid (₦)",
            value = amount,
            onValueChange = { raw -> amount = raw.filter { it.isDigit() }.take(15) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Lasts how many days?",
            value = days,
            onValueChange = { raw -> days = raw.filter { it.isDigit() }.take(4) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Warn me this many days before",
            value = reminders,
            onValueChange = {
                reminders = it.filter { c -> c.isDigit() || c == ',' || c == ' ' }.take(30)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Also add to company costs",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(checked = recordCost, onCheckedChange = { recordCost = it })
        }
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Add",
                onClick = {
                    val parsed = reminders.split(',', ' ')
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { it in 1..3650 }
                        .distinct()
                        .sortedDescending()
                        .take(6)
                    onAdd(name.trim(), amountValue, dayCount, parsed, recordCost && amountValue > 0L)
                },
                enabled = name.isNotBlank() && dayCount > 0
            )
        }
    }
}

/**
 * [destructive] shows the confirm button in red. When not given, it is on
 * automatically for confirm buttons whose text starts with "Delete".
 */
@Composable
fun ConfirmDialog(
    heading: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = confirmLabel.startsWith("Delete")
) {
    NeriboDialog(onDismiss = onDismiss, title = heading) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = confirmLabel,
                onClick = onConfirm,
                destructive = destructive
            )
        }
    }
}
