package com.westly.neribogroup.feature.software

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.cloud.AiRollKind
import com.westly.neribogroup.cloud.AiRollState
import com.westly.neribogroup.data.DiceKind
import com.westly.neribogroup.data.SoftwareRoll
import com.westly.neribogroup.feature.appdetail.DiceCube
import com.westly.neribogroup.feature.appdetail.diceColor
import com.westly.neribogroup.feature.appdetail.prettyDay
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatFullDate
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "2 clients contacted you. 1 sent a real proposal." */
internal fun rollResultText(roll: SoftwareRoll): String {
    val contacted = if (roll.contacts == 1) "1 client contacted you." else roll.contacts.toString() + " clients contacted you."
    val sent = if (roll.offers == 1) "1 sent a real proposal." else roll.offers.toString() + " sent a real proposal."
    return contacted + " " + sent
}

/**
 * The 2 dice of the software app: how many clients contacted you, and how many of them sent a real
 * proposal. One roll per calendar day. The dice only bring proposals. They never bring money.
 *
 * [todayRoll] is the roll of today, or null when today's roll is still to be made.
 */
@Composable
fun SoftwareDicePanel(
    now: Long,
    canRoll: Boolean,
    todayRoll: SoftwareRoll?,
    onRoll: () -> Unit
) {
    var rolling by remember { mutableStateOf(false) }
    var spinContact by remember { mutableStateOf(-1) }
    var spinOffer by remember { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()

    val contactFace = when {
        rolling -> spinContact
        todayRoll != null -> todayRoll.contactFace
        else -> -1
    }
    val offerFace = when {
        rolling -> spinOffer
        todayRoll != null -> todayRoll.offerFace
        else -> -1
    }

    FlatCard {
        Text(
            text = "Daily dice",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = formatFullDate(now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DiceCube(face = contactFace, color = diceColor(DiceKind.ACTIVE))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Clients who contacted you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DiceCube(face = offerFace, color = diceColor(DiceKind.NEW))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Real proposals",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (todayRoll != null && !rolling) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = rollResultText(todayRoll),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (todayRoll.note.isNotBlank()) {
                Text(
                    text = todayRoll.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        TintedButton(
            text = when {
                rolling -> "Rolling..."
                canRoll -> "Roll the dice"
                else -> "Already rolled today"
            },
            onClick = {
                if (!rolling && canRoll) {
                    rolling = true
                    scope.launch {
                        repeat(8) {
                            spinContact = Random.nextInt(0, 6)
                            spinOffer = Random.nextInt(0, 6)
                            delay(70)
                        }
                        rolling = false
                        onRoll()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canRoll && !rolling
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (canRoll) {
                "One roll per day, by your phone's date. The dice only bring proposals. " +
                    "No money is added until you accept one."
            } else {
                "Already rolled today. Next roll tomorrow."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One day in the software roll history: the two dice and what they brought. */
@Composable
fun SoftwareRollRow(roll: SoftwareRoll) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = prettyDay(roll.dayKey),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiceCube(face = roll.contactFace, color = diceColor(DiceKind.ACTIVE), cubeSize = 28.dp)
            Spacer(modifier = Modifier.width(6.dp))
            DiceCube(face = roll.offerFace, color = diceColor(DiceKind.NEW), cubeSize = 28.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = rollResultText(roll),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (roll.note.isNotBlank()) {
            Text(
                text = roll.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Part C: "Today's roll" of the software app. Claude rolls both dice and writes the proposals in ONE job a
 * day. There is no button that rolls here: only "Check now", which looks for Claude's answer.
 */
@Composable
fun SoftwareClaudeCard(
    now: Long,
    aiOn: Boolean,
    state: AiRollState,
    attemptsToday: Int,
    todayRoll: SoftwareRoll?,
    checking: Boolean,
    onCheck: () -> Unit
) {
    FlatCard {
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
        Spacer(modifier = Modifier.height(12.dp))

        if (!aiOn) {
            Text(
                text = "Claude rolls the software dice, once a day. Turn on the cloud and AI mode in " +
                    "Settings, then proposals will arrive here.",
                style = MaterialTheme.typography.bodyMedium
            )
            return@FlatCard
        }

        val rolled = state.kind == AiRollKind.ROLLED_CLAUDE || state.kind == AiRollKind.ROLLED_BUILTIN ||
            state.kind == AiRollKind.ROLLED_OTHER
        val headline = when {
            rolled -> "Rolled today ✔"
            state.kind == AiRollKind.OFFLINE -> "Waiting for internet"
            state.kind == AiRollKind.WAITING && attemptsToday > 1 ->
                "Claude's last answer was not accepted. Trying again (attempt " + attemptsToday + " of 3)."
            state.kind == AiRollKind.WAITING -> "Waiting for Claude"
            attemptsToday >= 3 ->
                "Claude's answers were not accepted 3 times today, so there are no new proposals today."
            else -> "Not rolled yet. Open Claude and say \"roll next\"."
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (rolled && todayRoll != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    DiceCube(face = todayRoll.contactFace, color = diceColor(DiceKind.ACTIVE))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Clients who contacted you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    DiceCube(face = todayRoll.offerFace, color = diceColor(DiceKind.NEW))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Real proposals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = rollResultText(todayRoll),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (todayRoll.note.isNotBlank()) {
                Text(
                    text = todayRoll.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!rolled) {
            Spacer(modifier = Modifier.height(12.dp))
            TintedButton(
                text = if (checking) "Checking..." else "Check now",
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Claude rolls both dice and writes the proposals, once a day. " +
                "No money is added until you accept one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
