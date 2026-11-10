package com.westly.neribogroup.feature.appdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Advert
import com.westly.neribogroup.data.AdvertEngine
import com.westly.neribogroup.data.AdvertPlatform
import com.westly.neribogroup.data.AdvertStatus
import com.westly.neribogroup.data.dayKey
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatCount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One short line for the Adverts card on the app's main page. */
fun advertsSummary(app: AppData, now: Long): String {
    if (app.adverts.isEmpty()) return "No adverts yet"
    val today = dayKey(now)
    val running = app.adverts.count { AdvertEngine.statusOn(it, today) == AdvertStatus.RUNNING }
    val upcoming = app.adverts.count { AdvertEngine.statusOn(it, today) == AdvertStatus.UPCOMING }
    val finished = app.adverts.count { AdvertEngine.statusOn(it, today) == AdvertStatus.FINISHED }
    val parts = ArrayList<String>()
    if (running > 0) parts.add("$running running")
    if (upcoming > 0) parts.add("$upcoming upcoming")
    if (finished > 0) parts.add("$finished finished")
    return parts.joinToString(" · ")
}

private fun statusLabel(advert: Advert, today: String): String {
    if (advert.isStopped) return "Stopped"
    return when (AdvertEngine.statusOn(advert, today)) {
        AdvertStatus.RUNNING -> "Running"
        AdvertStatus.UPCOMING -> "Upcoming"
        AdvertStatus.FINISHED -> "Finished"
    }
}

@Composable
private fun statusColor(advert: Advert, today: String): Color =
    if (AdvertEngine.statusOn(advert, today) == AdvertStatus.RUNNING) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

/**
 * The rows of the Adverts page. With [openAdvert] set they show that advert's details; otherwise the
 * list (New advert button, then Running, Upcoming and Finished). Back handling lives in the dashboard.
 */
fun LazyListScope.advertsPageItems(
    app: AppData,
    now: Long,
    openAdvert: Advert?,
    onOpenAdvert: (String) -> Unit,
    onStartAdvert: (String, List<AdvertPlatform>, Int, Int) -> Unit,
    onStopAdvert: (String) -> Unit,
    /** Part C: the exact names of the Socials accounts added by the owner (one extra followers field each). */
    extraPlatformNames: List<String> = emptyList()
) {
    val today = dayKey(now)

    if (openAdvert != null) {
        advertDetailItems(openAdvert, today, onStopAdvert)
        return
    }

    item(key = "adv-new") {
        var showNew by remember { mutableStateOf(false) }
        TintedButton(
            text = "New advert",
            onClick = { showNew = true },
            modifier = Modifier.fillMaxWidth()
        )
        if (showNew) {
            NewAdvertDialog(
                app = app,
                now = now,
                extraPlatformNames = extraPlatformNames,
                onDismiss = { showNew = false },
                onStart = { name, platforms, startIn, lasts ->
                    showNew = false
                    onStartAdvert(name, platforms, startIn, lasts)
                }
            )
        }
    }

    val running = app.adverts
        .filter { AdvertEngine.statusOn(it, today) == AdvertStatus.RUNNING }
        .sortedBy { it.endDayKey }
    val upcoming = app.adverts
        .filter { AdvertEngine.statusOn(it, today) == AdvertStatus.UPCOMING }
        .sortedBy { it.startDayKey }
    val finished = app.adverts
        .filter { AdvertEngine.statusOn(it, today) == AdvertStatus.FINISHED }
        .sortedWith(compareByDescending<Advert> { it.endDayKey }.thenByDescending { it.createdMillis })
        .take(AdvertEngine.MAX_FINISHED_KEPT)

    if (running.isEmpty() && upcoming.isEmpty() && finished.isEmpty()) {
        item(key = "adv-none") {
            Text(
                text = "No adverts yet. Tap New advert.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (running.isNotEmpty()) {
        item(key = "adv-h-running") { SectionTitle("Running") }
        advertRows(running, today, onOpenAdvert)
    }
    if (upcoming.isNotEmpty()) {
        item(key = "adv-h-upcoming") { SectionTitle("Upcoming") }
        advertRows(upcoming, today, onOpenAdvert)
    }
    if (finished.isNotEmpty()) {
        item(key = "adv-h-finished") { SectionTitle("Finished") }
        advertRows(finished, today, onOpenAdvert)
    }
}

private fun LazyListScope.advertRows(list: List<Advert>, today: String, onOpen: (String) -> Unit) {
    for (advert in list) {
        item(key = "adv-" + advert.id) {
            AdvertRow(advert = advert, today = today, onClick = { onOpen(advert.id) })
        }
    }
}

/** One compact row: name, followers, status, dates and how many users have arrived. */
@Composable
private fun AdvertRow(advert: Advert, today: String, onClick: () -> Unit) {
    FlatCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = advert.celebrityName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = AdvertEngine.compactFollowers(advert.totalFollowers) + " followers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = prettyDay(advert.startDayKey) + " to " + prettyDay(advert.endDayKey) + " · " +
                        formatCount(advert.deliveredUsers) + " / " + formatCount(advert.targetUsers) + " users",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusDot(
                text = statusLabel(advert, today),
                color = statusColor(advert, today)
            )
        }
    }
}

private fun daysBetweenKeys(from: String, to: String): Long? = runCatching {
    ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to))
}.getOrNull()

/** The details of one advert: followers, conversion, users, dates, the daily plan and Stop. */
private fun LazyListScope.advertDetailItems(advert: Advert, today: String, onStop: (String) -> Unit) {
    val status = AdvertEngine.statusOn(advert, today)

    item(key = "adv-d-head") {
        Column {
            Text(
                text = advert.celebrityName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusDot(text = statusLabel(advert, today), color = statusColor(advert, today))
        }
    }

    item(key = "adv-d-followers") {
        FlatCard {
            Text(
                text = "Followers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (platform in advert.platforms) {
                LineRow(platform.name, formatCount(platform.followers))
            }
            Spacer(modifier = Modifier.height(4.dp))
            LineRow("Total", formatCount(advert.totalFollowers), bold = true)
        }
    }

    item(key = "adv-d-conversion") {
        FlatCard {
            Text(
                text = "Conversion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This advert converts " + AdvertEngine.percentText(advert.conversionPpm) + " of followers",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    item(key = "adv-d-users") {
        FlatCard {
            Text(
                text = "Users",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LineRow("Expected in total", formatCount(advert.targetUsers))
            LineRow("Delivered so far", formatCount(advert.deliveredUsers))
            val left = maxOf(0L, advert.targetUsers - advert.deliveredUsers)
            LineRow(if (AdvertEngine.isSettled(advert)) "Not delivered" else "Remaining", formatCount(left))
        }
    }

    item(key = "adv-d-dates") {
        FlatCard {
            Text(
                text = "Dates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LineRow("Starts", prettyDay(advert.startDayKey))
            LineRow("Ends", prettyDay(advert.endDayKey))
            if (advert.isStopped) LineRow("Stopped on", prettyDay(advert.stoppedDayKey))
            val extra = when (status) {
                AdvertStatus.UPCOMING -> {
                    val n = daysBetweenKeys(today, advert.startDayKey) ?: 0L
                    "Starts in " + n + (if (n == 1L) " day" else " days")
                }
                AdvertStatus.RUNNING -> {
                    val n = (daysBetweenKeys(today, advert.endDayKey) ?: 0L) + 1L
                    n.toString() + (if (n == 1L) " day left" else " days left")
                }
                AdvertStatus.FINISHED -> "Ended"
            }
            LineRow("Days left", extra)
        }
    }

    item(key = "adv-d-plan-title") { SectionTitle("Daily plan") }
    item(key = "adv-d-plan") {
        FlatCard {
            val start = runCatching { LocalDate.parse(advert.startDayKey) }.getOrNull()
            val lastDeliverable = AdvertEngine.lastDeliverableIndex(advert)
            for ((index, planned) in advert.plan.withIndex()) {
                val dayKeyText = start?.plusDays(index.toLong())?.toString() ?: ""
                val state = when {
                    index < advert.deliveredDays -> "Delivered"
                    advert.isStopped && index > lastDeliverable -> "Not delivered (stopped)"
                    dayKeyText.isNotEmpty() && dayKeyText <= today -> "Due on next roll"
                    else -> "Upcoming"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (dayKeyText.isEmpty()) "Day " + (index + 1) else prettyDay(dayKeyText),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = state,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatCount(planned) + " users",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (index < advert.plan.size - 1) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (status != AdvertStatus.FINISHED) {
        item(key = "adv-d-stop") {
            var confirmStop by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { confirmStop = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop advert")
            }
            if (confirmStop) {
                ConfirmDialog(
                    heading = "Stop this advert?",
                    message = "Users not yet delivered will not arrive and it cannot be restarted.",
                    confirmLabel = "Stop",
                    onConfirm = {
                        confirmStop = false
                        onStop(advert.id)
                    },
                    onDismiss = { confirmStop = false }
                )
            }
        }
    }
}

@Composable
private fun LineRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** The New advert form. The conversion rate is not asked here: it is drawn when the advert starts. */
@Composable
private fun NewAdvertDialog(
    app: AppData,
    now: Long,
    extraPlatformNames: List<String>,
    onDismiss: () -> Unit,
    onStart: (String, List<AdvertPlatform>, Int, Int) -> Unit
) {
    // The six fixed platforms, then one more for every Socials account the owner added.
    val platformNames = AdvertEngine.allowedPlatformNames(extraPlatformNames)
    var name by remember { mutableStateOf("") }
    var followerTexts by remember { mutableStateOf(emptyMap<String, String>()) }
    var startText by remember { mutableStateOf("0") }
    var daysText by remember { mutableStateOf("7") }

    val platforms = platformNames.map { platformName ->
        AdvertPlatform(platformName, followerTexts[platformName]?.toLongOrNull() ?: 0L)
    }
    val total = platforms.sumOf { it.followers }
    val startIn = startText.toIntOrNull() ?: 0
    val lasts = daysText.toIntOrNull() ?: 0
    val error = AdvertEngine.advertError(app, name, platforms, startIn, lasts, extraPlatformNames)

    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val startDay = today.plusDays(startIn.toLong())
    val endDay = startDay.plusDays((lasts - 1).coerceAtLeast(0).toLong())

    NeriboDialog(
        onDismiss = onDismiss,
        title = "New advert",
        subtitle = "It costs nothing in the app. Followers decide how many users arrive."
    ) {
        LabeledField(
            label = "Celebrity name",
            value = name,
            onValueChange = { name = it.take(60) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        for (platformName in platformNames) {
            Spacer(modifier = Modifier.height(16.dp))
            LabeledField(
                label = "$platformName followers (optional)",
                value = followerTexts[platformName] ?: "",
                onValueChange = { raw ->
                    val clean = raw.filter { it.isDigit() }.take(10)
                    followerTexts = followerTexts + (platformName to clean)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Total followers: " + formatCount(total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField(
            label = "Starts in how many days (0 = today)",
            value = startText,
            onValueChange = { raw -> startText = raw.filter { it.isDigit() }.take(2) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField(
            label = "Lasts how many days (1 to " + AdvertEngine.MAX_LASTS_DAYS + ")",
            value = daysText,
            onValueChange = { raw -> daysText = raw.filter { it.isDigit() }.take(2) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = error ?: ("From " + prettyDay(startDay.toString()) + " to " + prettyDay(endDay.toString())),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Start",
                onClick = { onStart(name.trim(), platforms.filter { it.followers > 0L }, startIn, lasts) },
                enabled = error == null
            )
        }
    }
}
