package com.westly.neribogroup.feature.group

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AdvertEngine
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.SocialAccount
import com.westly.neribogroup.data.SocialEdit
import com.westly.neribogroup.data.SocialStat
import com.westly.neribogroup.data.SocialsEngine
import com.westly.neribogroup.data.SocialsRollInfo
import com.westly.neribogroup.data.SocialsRollKind
import com.westly.neribogroup.data.SocialsRules
import com.westly.neribogroup.feature.appdetail.prettyDay
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.NumberField
import com.westly.neribogroup.feature.common.SectionScaffold
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatColumn
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.formatFullDate

/**
 * The Socials page: today's roll, one card for every social account and the adverts that count today.
 * Editing follows the app's pattern (three dots, then Edit). Followers can never be typed.
 */
@Composable
fun SocialsScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    rollInfoFor: (Long) -> SocialsRollInfo,
    todayKeyFor: (Long) -> String,
    aiOn: Boolean,
    aiChecking: Boolean,
    onAiCheck: () -> Unit,
    /** Returns one plain sentence when the account is not allowed, or null when it was added. */
    onAddAccount: (String, String, String) -> String?,
    /** Why an account cannot be deleted right now, or null when it can. */
    deleteBlockFor: (String, Long) -> String?,
    onDeleteAccount: (String) -> String?,
    onSaveEdits: (List<SocialEdit>) -> Unit
) {
    val now = rememberNowMillis()
    val today = todayKeyFor(now)
    val socials = ledger.socials

    var editing by remember { mutableStateOf(false) }
    var drafts by remember { mutableStateOf(emptyMap<String, SocialEdit>()) }
    var showAdd by remember { mutableStateOf(false) }
    var historyId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var blockedText by remember { mutableStateOf<String?>(null) }

    fun draftOf(a: SocialAccount): SocialEdit =
        drafts[a.id] ?: SocialEdit(a.id, a.handle, a.displayName, a.following, a.posts, a.likes)

    SectionScaffold(
        title = "Socials",
        editing = editing,
        onBack = onBack,
        onStartEdit = {
            drafts = emptyMap()
            editing = true
        },
        onCancelEdit = {
            drafts = emptyMap()
            editing = false
        },
        onSave = {
            onSaveEdits(drafts.values.toList())
            drafts = emptyMap()
            editing = false
        }
    ) {
        if (!editing) {
            item(key = "soc-roll") {
                RollCard(
                    info = rollInfoFor(now),
                    now = now,
                    aiOn = aiOn,
                    checking = aiChecking,
                    onCheck = onAiCheck
                )
            }

            val missing = SocialsEngine.platformsWithoutAccount(ledger, today)
            if (missing.isNotEmpty()) {
                item(key = "soc-missing") {
                    FlatCard {
                        for ((index, platform) in missing.withIndex()) {
                            if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (SocialsRules.platformKey(platform) == "other") {
                                    "\"Other\" has running adverts, but followers listed as Other are not given to any account."
                                } else {
                                    platform + " has running adverts but no " + platform +
                                        " account. Add one to receive its followers."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (socials.accounts.isEmpty()) {
            item(key = "soc-none") {
                Text(
                    text = "No accounts yet. Open the three-dot menu, tap Edit, then Add account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val bases = SocialsEngine.basesOn(ledger, today)
        for (account in socials.accounts) {
            item(key = "soc-" + account.id) {
                if (editing) {
                    EditCard(
                        account = account,
                        draft = draftOf(account),
                        onChange = { drafts = drafts + (account.id to it) },
                        onDelete = {
                            val block = deleteBlockFor(account.id, now)
                            if (block != null) blockedText = block else deleteId = account.id
                        }
                    )
                } else {
                    val base = bases.firstOrNull { it.key == SocialsRules.platformKey(account.name) }
                    AccountCard(
                        account = account,
                        today = today,
                        baseLine = if (base == null) "" else SocialsEngine.baseLine(base),
                        onHistory = { historyId = account.id }
                    )
                }
            }
        }

        if (editing) {
            item(key = "soc-add") {
                TintedButton(
                    text = "Add account",
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = socials.accounts.size < com.westly.neribogroup.data.MAX_SOCIAL_ACCOUNTS
                )
            }
        } else {
            item(key = "soc-adv-title") { SectionTitle("Running adverts") }
            item(key = "soc-adv") { RunningAdverts(ledger, today) }
        }
    }

    if (showAdd) {
        AddAccountDialog(
            ledger = ledger,
            onDismiss = { showAdd = false },
            onAdd = { name, handle, display ->
                val error = onAddAccount(name, handle, display)
                if (error == null) showAdd = false
                error
            }
        )
    }

    val historyAccount = socials.accounts.firstOrNull { it.id == historyId }
    if (historyAccount != null) {
        HistoryDialog(account = historyAccount, onDismiss = { historyId = null })
    }

    val deleting = socials.accounts.firstOrNull { it.id == deleteId }
    if (deleting != null) {
        ConfirmDialog(
            heading = "Delete " + deleting.name + "?",
            message = "The follower history of " + deleting.name + " will be lost. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                val account = deleting
                deleteId = null
                drafts = drafts - account.id
                val error = onDeleteAccount(account.id)
                if (error != null) blockedText = error
            },
            onDismiss = { deleteId = null }
        )
    }

    val blocked = blockedText
    if (blocked != null) {
        NeriboDialog(onDismiss = { blockedText = null }, title = "Cannot delete") {
            Text(
                text = blocked,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DialogActions {
                TextButton(onClick = { blockedText = null }) { Text("OK") }
            }
        }
    }
}

/** The empty circle with a thin outline. No silhouette, no letters, no placeholder picture. */
@Composable
private fun EmptyProfileCircle() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

@Composable
private fun RollCard(
    info: SocialsRollInfo,
    now: Long,
    aiOn: Boolean,
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

        val headline = when (info.kind) {
            SocialsRollKind.ROLLED_TODAY -> "Rolled by Claude today ✔"
            SocialsRollKind.NOTHING_TO_ROLL -> "Nothing to roll today (no running adverts)"
            SocialsRollKind.AI_OFF -> "Turn on cloud sign-in and AI mode in Settings so Claude can roll"
            SocialsRollKind.WAITING_APPS -> "Waiting for the apps to roll first"
            SocialsRollKind.WAITING_CLAUDE -> "Waiting for Claude"
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (info.kind == SocialsRollKind.ROLLED_TODAY) {
            Spacer(modifier = Modifier.height(8.dp))
            for ((name, gain) in info.gainsToday) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+" + formatCount(gain),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (info.missedDays > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            val n = info.missedDays
            Text(
                text = (if (n == 1) "1 missed day" else "$n missed days") + " will be rolled when Claude is available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val canCheck = aiOn && (
            info.kind == SocialsRollKind.WAITING_APPS ||
                info.kind == SocialsRollKind.WAITING_CLAUDE ||
                info.missedDays > 0
            )
        if (canCheck) {
            Spacer(modifier = Modifier.height(12.dp))
            TintedButton(
                text = if (checking) "Checking…" else "Check now",
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking
            )
        }
        // There is no Roll button: only Claude rolls the social dice.
    }
}

@Composable
private fun AccountCard(
    account: SocialAccount,
    today: String,
    baseLine: String,
    onHistory: () -> Unit
) {
    FlatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmptyProfileCircle()
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account.handle.isNotBlank()) {
                    Text(
                        text = "@" + account.handle.removePrefix("@"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (stat in SocialsRules.statsFor(account.name)) {
                StatColumn(
                    label = stat.label,
                    value = AdvertEngine.compactFollowers(SocialsRules.statValue(account, stat)),
                    modifier = Modifier.weight(1f),
                    valueColor = if (stat == SocialStat.FOLLOWERS) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val gain = account.rolls.firstOrNull { it.dayKey == today }?.gain
        Text(
            text = if (gain == null) "Today —" else "Today +" + formatCount(gain),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (baseLine.isEmpty()) "Follower base today: —" else "Follower base today: $baseLine",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onHistory) { Text("History") }
    }
}

@Composable
private fun EditCard(
    account: SocialAccount,
    draft: SocialEdit,
    onChange: (SocialEdit) -> Unit,
    onDelete: () -> Unit
) {
    FlatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmptyProfileCircle()
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Followers: " + formatCount(account.followers) + " (they change only through the daily roll)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField(
            label = "Handle",
            value = draft.handle,
            onValueChange = { onChange(draft.copy(handle = it.take(40))) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField(
            label = "Display name",
            value = draft.displayName,
            onValueChange = { onChange(draft.copy(displayName = it.take(60))) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        for (stat in SocialsRules.statsFor(account.name)) {
            when (stat) {
                SocialStat.FOLLOWING -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    NumberField(
                        label = "Following",
                        value = draft.following,
                        onChange = { onChange(draft.copy(following = it)) },
                        maxDigits = 12
                    )
                }
                SocialStat.POSTS -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    NumberField(
                        label = "Posts",
                        value = draft.posts,
                        onChange = { onChange(draft.copy(posts = it)) },
                        maxDigits = 12
                    )
                }
                SocialStat.LIKES -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    NumberField(
                        label = "Likes",
                        value = draft.likes,
                        onChange = { onChange(draft.copy(likes = it)) },
                        maxDigits = 12
                    )
                }
                SocialStat.FOLLOWERS -> {}
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("Delete account")
        }
    }
}

@Composable
private fun RunningAdverts(ledger: Ledger, today: String) {
    val counting = SocialsEngine.countingAdverts(ledger, today)
    FlatCard {
        if (counting.isEmpty()) {
            Text(
                text = "No adverts are running today. Social followers come only from the adverts inside the apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        for ((index, ca) in counting.withIndex()) {
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = ca.advert.celebrityName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ca.appName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val listed = ca.advert.platforms.filter { it.followers > 0L }
            Text(
                text = listed.joinToString(" · ") { it.name + " " + AdvertEngine.compactFollowers(it.followers) },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AddAccountDialog(
    ledger: Ledger,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> String?
) {
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var display by remember { mutableStateOf(com.westly.neribogroup.data.DEFAULT_SOCIAL_DISPLAY_NAME) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val error = SocialsRules.addError(ledger.socials, name, handle)
    NeriboDialog(
        onDismiss = onDismiss,
        title = "Add account",
        subtitle = "The name cannot be changed later because adverts use it."
    ) {
        LabeledField(
            label = "Platform name",
            value = name,
            onValueChange = {
                name = it.take(30)
                saveError = null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField(
            label = "Handle (optional)",
            value = handle,
            onValueChange = { handle = it.take(40) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField(
            label = "Display name",
            value = display,
            onValueChange = { display = it.take(60) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        val shownError = saveError ?: (if (name.isNotBlank()) error else null)
        if (shownError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = shownError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        DialogActions(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Add",
                onClick = { saveError = onAdd(name, handle, display) },
                enabled = error == null
            )
        }
    }
}

@Composable
private fun HistoryDialog(account: SocialAccount, onDismiss: () -> Unit) {
    NeriboDialog(
        onDismiss = onDismiss,
        title = account.name + " history",
        subtitle = "The last 30 rolls"
    ) {
        val rolls = account.rolls.takeLast(30).reversed()
        if (rolls.isEmpty()) {
            Text(
                text = "No rolls yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (roll in rolls) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = prettyDay(roll.dayKey), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Face " + roll.face + " · base " + AdvertEngine.compactFollowers(roll.base),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "+" + formatCount(roll.gain),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}
