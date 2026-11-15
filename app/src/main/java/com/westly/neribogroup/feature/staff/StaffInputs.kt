package com.westly.neribogroup.feature.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.Allowance
import com.westly.neribogroup.data.DayChange
import com.westly.neribogroup.data.Staff
import com.westly.neribogroup.data.WORK_OFF
import com.westly.neribogroup.data.WORK_ONSITE
import com.westly.neribogroup.data.WORK_REMOTE
import com.westly.neribogroup.data.dayStartMillis
import com.westly.neribogroup.data.daysBetweenMillis
import com.westly.neribogroup.data.workModeText
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FieldGap
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatDate
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** The date picker works in UTC, so a day on the phone is turned into the same calendar day in UTC. */
private fun dayToPickerMillis(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The day picked in the date picker, turned back into the start of that day on the phone. */
private fun pickerMillisToDay(pickerMillis: Long): Long =
    Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** "Fri 21 Nov" */
fun shortDayText(millis: Long): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(java.util.Date(millis))

/** The line under a salary or allowance that has a date: "Next pay: 25 Dec 2026 · in 3 days". */
fun dueText(label: String, due: Long, now: Long): String {
    val left = daysBetweenMillis(now, due)
    val when_ = when {
        left < 0L -> " · overdue by " + (-left) + (if (left == -1L) " day" else " days")
        left == 0L -> " · today"
        left == 1L -> " · tomorrow"
        left <= 14L -> " · in $left days"
        else -> ""
    }
    return label + ": " + formatDate(due) + when_
}

/** A date box: shows the day (or "Not set"), opens a calendar when tapped and has a Clear button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    millis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (millis > 0L) formatDate(millis) else "Not set")
            }
            if (millis > 0L) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onChange(0L) }) { Text("Clear") }
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = if (millis > 0L) dayToPickerMillis(millis) else null
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = state.selectedDateMillis
                        if (picked != null) onChange(pickerMillisToDay(picked))
                        open = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** One choice button: filled when chosen, outlined when not. */
@Composable
private fun ChoiceButton(
    text: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (chosen) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) { Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) { Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
    }
}

/** The weekly plan while editing: for each day, Off, On-site or Remote. */
@Composable
fun WeekPlanEditor(plan: List<Int>, onChange: (List<Int>) -> Unit) {
    val current = if (plan.size == 7) plan else List(7) { WORK_OFF }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (plan.size != 7) {
            Text(
                text = "Not set yet. Tap a choice for a day to start the plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        for (i in 0 until 7) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DAY_NAMES[i],
                    modifier = Modifier.width(40.dp),
                    style = MaterialTheme.typography.titleSmall
                )
                for (mode in listOf(WORK_OFF, WORK_ONSITE, WORK_REMOTE)) {
                    ChoiceButton(
                        text = workModeText(mode),
                        chosen = current[i] == mode,
                        onClick = {
                            val updated = current.toMutableList()
                            updated[i] = mode
                            onChange(updated)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (plan.size == 7) {
            TextButton(onClick = { onChange(emptyList()) }) { Text("Clear weekly plan") }
        }
    }
}

/** The work schedule in normal view: today, the weekly plan and the coming one-off changes. */
@Composable
fun WorkScheduleCard(person: Staff, now: Long) {
    val hasPlan = person.weekPlan.size == 7
    val todayStart = dayStartMillis(now)
    val coming = person.dayChanges
        .filter { dayStartMillis(it.dayMillis) >= todayStart }
        .sortedBy { it.dayMillis }
    if (!hasPlan && coming.isEmpty()) {
        Text(
            text = "No work schedule set yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val todayIndex = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).dayOfWeek.value - 1
    val todayMode = person.workModeOn(now)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (todayMode != null) {
                Text(
                    text = "Today: " + workModeText(todayMode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (hasPlan) {
                for (i in 0 until 7) {
                    Row {
                        Text(
                            text = DAY_NAMES[i],
                            modifier = Modifier.width(48.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (i == todayIndex) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            text = workModeText(person.weekPlan[i]) + if (i == todayIndex) " (today)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (i == todayIndex) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            if (coming.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "One-off changes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (change in coming) {
                    Text(
                        text = shortDayText(change.dayMillis) + ": " + workModeText(change.mode),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/** Adds a one-off change for a single day. */
@Composable
fun DayChangeDialog(
    onDismiss: () -> Unit,
    onAdd: (DayChange) -> Unit
) {
    var day by remember { mutableStateOf(0L) }
    var mode by remember { mutableStateOf(WORK_REMOTE) }
    NeriboDialog(
        onDismiss = onDismiss,
        title = "Change one day",
        subtitle = "This only changes the day you pick. The weekly plan stays the same."
    ) {
        DateField(label = "Day", millis = day, onChange = { day = it })
        Spacer(modifier = Modifier.height(FieldGap))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in listOf(WORK_OFF, WORK_ONSITE, WORK_REMOTE)) {
                ChoiceButton(
                    text = workModeText(m),
                    chosen = mode == m,
                    onClick = { mode = m },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Add",
                onClick = { onAdd(DayChange(dayMillis = day, mode = mode)) },
                enabled = day > 0L
            )
        }
    }
}

/** Adds or edits an allowance: name, amount, whether it renews every month or is paid one time, and its date. */
@Composable
fun AllowanceDialog(
    initial: Allowance?,
    onDismiss: () -> Unit,
    onSave: (Allowance) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(if (initial != null && initial.amount > 0L) initial.amount.toString() else "") }
    var renewing by remember { mutableStateOf(initial?.renewing ?: true) }
    var date by remember { mutableStateOf(initial?.nextDateMillis ?: 0L) }
    val value = amount.toLongOrNull() ?: 0L

    NeriboDialog(
        onDismiss = onDismiss,
        title = if (initial == null) "Add allowance" else "Edit allowance"
    ) {
        LabeledField(
            label = "Description",
            value = name,
            onValueChange = { name = it.take(60) }
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Amount (₦)",
            value = amount,
            onValueChange = { raw -> amount = raw.filter { it.isDigit() }.take(15) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        Text(
            text = "How is it paid?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceButton(
                text = "Every month",
                chosen = renewing,
                onClick = { renewing = true },
                modifier = Modifier.weight(1f)
            )
            ChoiceButton(
                text = "One time",
                chosen = !renewing,
                onClick = { renewing = false },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(FieldGap))
        DateField(
            label = if (renewing) "Next payment date" else "Payment date",
            millis = date,
            onChange = { date = it }
        )
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = if (initial == null) "Add" else "Save",
                onClick = {
                    val base = initial ?: Allowance(name = name.trim(), amount = value)
                    onSave(
                        base.copy(
                            name = name.trim(),
                            amount = value,
                            renewing = renewing,
                            nextDateMillis = date
                        )
                    )
                },
                enabled = value > 0L && name.isNotBlank()
            )
        }
    }
}
