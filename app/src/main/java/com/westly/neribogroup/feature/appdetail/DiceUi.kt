package com.westly.neribogroup.feature.appdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.DiceKind
import com.westly.neribogroup.data.RollBlock
import com.westly.neribogroup.data.RollRecord
import com.westly.neribogroup.data.dayKey
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.formatFullDate
import com.westly.neribogroup.ui.formatNaira
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "2026-11-09" as "09 Nov 2026". */
fun prettyDay(key: String): String = runCatching {
    LocalDate.parse(key).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))
}.getOrDefault(key)

fun diceColor(kind: DiceKind): Color = when (kind) {
    DiceKind.NEW -> Color(0xFF2EAD4B)
    DiceKind.ACTIVE -> Color(0xFF1E6FE0)
    DiceKind.PREMIUM -> Color(0xFFF2B600)
    DiceKind.PRO -> Color(0xFF7B2FE0)
    DiceKind.RENEW -> Color(0xFFF57C00)
    DiceKind.LOST -> Color(0xFFE53935)
    DiceKind.ADS -> Color(0xFFC9A227)
}

fun diceLabel(kind: DiceKind): String = when (kind) {
    DiceKind.NEW -> "New"
    DiceKind.ACTIVE -> "Active"
    DiceKind.PREMIUM -> "Premium"
    DiceKind.PRO -> "Pro"
    DiceKind.RENEW -> "Renew"
    DiceKind.LOST -> "Lost"
    DiceKind.ADS -> "Ads"
}

/** One tiny dice. [face] is 0 to 5 (0 is an empty face). A negative face shows a question mark. */
@Composable
fun DiceCube(
    face: Int,
    color: Color,
    modifier: Modifier = Modifier,
    cubeSize: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(cubeSize)
            .clip(RoundedCornerShape(10.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (face < 0) {
            Text(text = "?", color = Color.White, fontWeight = FontWeight.Bold)
        } else if (face > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val radius = w * 0.085f
                val spots = when (face) {
                    1 -> listOf(0.5f to 0.5f)
                    2 -> listOf(0.28f to 0.28f, 0.72f to 0.72f)
                    3 -> listOf(0.28f to 0.28f, 0.5f to 0.5f, 0.72f to 0.72f)
                    4 -> listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.72f, 0.72f to 0.72f)
                    else -> listOf(
                        0.28f to 0.28f, 0.72f to 0.28f, 0.5f to 0.5f, 0.28f to 0.72f, 0.72f to 0.72f
                    )
                }
                for (spot in spots) {
                    drawCircle(
                        color = Color.White,
                        radius = radius,
                        center = Offset(w * spot.first, h * spot.second)
                    )
                }
            }
        }
    }
}

/**
 * The daily roll for one app: the tiny dice that can bring something real, and one Roll button.
 * The roll is allowed once per calendar day, by the phone's date.
 */
@Composable
fun DicePanel(app: AppData, now: Long, onRoll: () -> Unit) {
    val today = dayKey(now)
    val block = DiceEngine.rollBlock(app, now)
    val visible = DiceEngine.visibleDice(app, now)
    val record = app.rolls.lastOrNull { it.dayKey == today }
    val campaignToday = app.campaign?.isActiveOn(today) == true

    var rolling by remember { mutableStateOf(false) }
    var spinFaces by remember { mutableStateOf<List<Int>?>(null) }
    val scope = rememberCoroutineScope()

    val kinds: List<DiceKind> = if (record != null) {
        DiceKind.values().filter { record.faces.getOrElse(it.ordinal) { -1 } >= 0 }
    } else {
        visible
    }
    val spin = spinFaces

    FlatCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Today's roll",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatFullDate(now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (campaignToday) {
                StatusDot(text = "Campaign", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            kinds.forEachIndexed { index, kind ->
                val face = when {
                    spin != null && index < spin.size -> spin[index]
                    record != null -> record.faces.getOrElse(kind.ordinal) { -1 }
                    else -> -1
                }
                Column(
                    modifier = Modifier.width(42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DiceCube(face = face, color = diceColor(kind))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = diceLabel(kind),
                        // The cubes have a fixed size, so this label keeps a fixed 10dp size at every font step.
                        fontSize = with(LocalDensity.current) { 10.dp.toSp() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (record != null && !rolling) {
            Spacer(modifier = Modifier.height(14.dp))
            RollResults(record = record, adsOn = record.faces.getOrElse(DiceKind.ADS.ordinal) { -1 } >= 0)
        }

        Spacer(modifier = Modifier.height(14.dp))

        TintedButton(
            text = when {
                rolling -> "Rolling..."
                block == RollBlock.OK -> "Roll all dice"
                block == RollBlock.ALREADY_ROLLED -> "Already rolled today"
                else -> "Rolling is blocked"
            },
            onClick = {
                if (!rolling && block == RollBlock.OK) {
                    rolling = true
                    scope.launch {
                        repeat(8) {
                            spinFaces = kinds.map { Random.nextInt(0, 6) }
                            delay(70)
                        }
                        spinFaces = null
                        rolling = false
                        onRoll()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = block == RollBlock.OK && !rolling
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (block) {
                RollBlock.OK -> "One roll per day, by your phone's date. A day you skip stays empty."
                RollBlock.ALREADY_ROLLED -> "You can roll again after 12 am."
                RollBlock.DATE_WENT_BACK ->
                    "Your phone's date is earlier than the last roll (" + prettyDay(app.lastRollDayKey) +
                        "). Set the right date to roll again."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** What today's roll did, one line each for the dice that were in play. */
@Composable
private fun RollResults(record: RollRecord, adsOn: Boolean) {
    fun has(kind: DiceKind): Boolean = record.faces.getOrElse(kind.ordinal) { -1 } >= 0
    Column {
        ResultLine("New users", "+" + formatCount(record.newUsers))
        ResultLine("Active users", formatCount(record.active))
        if (has(DiceKind.PREMIUM)) ResultLine("Upgraded to Premium", formatCount(record.premium))
        if (has(DiceKind.PRO)) ResultLine("Upgraded to Pro", formatCount(record.pro))
        if (has(DiceKind.RENEW)) ResultLine("Renewed", formatCount(record.renewed))
        if (has(DiceKind.LOST)) ResultLine("Did not renew", formatCount(record.lapsed))
        ResultLine("Subscriptions to Play Balance", formatNaira(record.subsNet))
        if (adsOn) ResultLine("Ads to Play Balance", formatNaira(record.adsNet))
    }
}

/** One day in the roll history. */
@Composable
fun RollHistoryRow(record: RollRecord) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = prettyDay(record.dayKey),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            if (record.campaign) {
                StatusDot(text = "Campaign", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = "+" + formatCount(record.newUsers) + " new · " +
                formatCount(record.active) + " active · " +
                formatCount(record.premium) + " Premium · " +
                formatCount(record.pro) + " Pro · " +
                formatCount(record.renewed) + " renewed · " +
                formatCount(record.lapsed) + " lost",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Play Balance " + formatNaira(record.subsNet + record.adsNet),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
