package com.westly.neribogroup.feature.appdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.DAY_MILLIS
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.StatColumn
import com.westly.neribogroup.ui.formatCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Active users of one day, with a short day name such as "Mon". */
data class DayActive(
    val label: String,
    val count: Long
)

/** The last 7 days, oldest first, ending with today. */
fun activeUsersWeek(app: AppData, now: Long): List<DayActive> {
    val format = SimpleDateFormat("EEE", Locale.getDefault())
    return (6 downTo 0).map { back ->
        val millis = now - back * DAY_MILLIS
        DayActive(
            label = format.format(Date(millis)),
            count = app.activeUsersOn(millis, now)
        )
    }
}

/** New users of one day, for the last 7 days, oldest first, ending with today. */
fun newUsersWeek(app: AppData, now: Long): List<DayActive> {
    val format = SimpleDateFormat("EEE", Locale.getDefault())
    return (6 downTo 0).map { back ->
        val millis = now - back * DAY_MILLIS
        DayActive(
            label = format.format(Date(millis)),
            count = app.newUsersOn(millis, now)
        )
    }
}

private fun compactCount(n: Long): String = when {
    n >= 10_000L -> (n / 1000L).toString() + "K"
    n >= 1_000L -> String.format(Locale.US, "%.1fK", n / 1000.0)
    else -> n.toString()
}

/** Active users today, their share of all users, and a small chart of the last 7 days. */
@Composable
fun ActiveUsersCard(
    activeToday: Long,
    totalUsers: Long,
    days: List<DayActive>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary

    FlatCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn(
                label = "Active users today",
                value = formatCount(activeToday),
                modifier = Modifier.weight(1f),
                valueColor = primary
            )
            if (totalUsers > 0L) {
                StatColumn(
                    label = "Of all users",
                    value = (activeToday * 100L / totalUsers).toString() + "%",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Last 7 days",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        WeekBars(days = days)
    }
}

/** The small 7-day bar chart (today is the strong bar). Used by the Active users and New users cards. */
@Composable
fun WeekBars(days: List<DayActive>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val peak = days.maxOfOrNull { it.count } ?: 0L
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEachIndexed { index, day ->
            val isToday = index == days.lastIndex
            val fraction = if (peak > 0L) day.count.toFloat() / peak.toFloat() else 0f
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height((6f + 58f * fraction).dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isToday) primary else primary.copy(alpha = 0.35f))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = compactCount(day.count),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** A small chart of the new users of the last 7 days. */
@Composable
fun NewUsersChart(days: List<DayActive>, modifier: Modifier = Modifier) {
    FlatCard(modifier = modifier) {
        Text(
            text = "New users — last 7 days",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        WeekBars(days = days)
    }
}
