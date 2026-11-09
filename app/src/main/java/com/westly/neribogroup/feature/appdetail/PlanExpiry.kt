package com.westly.neribogroup.feature.appdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.PLAN_DAYS
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatDate

/** "12 days left", "Expires today", "Expired 3 days ago" or "No date set". */
internal fun planStatusText(left: Long?): String {
    if (left == null) return "No date set"
    return when {
        left < 0L -> "Expired " + (-left) + (if (-left == 1L) " day ago" else " days ago")
        left == 0L -> "Expires today"
        left == 1L -> "1 day left"
        else -> "$left days left"
    }
}

/**
 * One Premium or Pro user with the day their plan ends. Tap a user you added yourself to say if they
 * renewed. Users made by the dice renew or leave by themselves, so they are not tappable.
 */
@Composable
internal fun ExpiryRow(user: AppUser, now: Long, onClick: () -> Unit, onHistory: (() -> Unit)? = null) {
    val left = user.planDaysLeft(now)
    val cs = MaterialTheme.colorScheme
    val statusColor = when {
        left == null -> cs.onSurfaceVariant
        left != null && left <= 2L -> cs.error
        left != null && left <= 7L -> cs.tertiary
        else -> cs.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (user.simulated) {
                    if (onHistory != null) Modifier.clickable(onClick = onHistory) else Modifier
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = planText(user.plan) +
                    (if (user.simulated) " · auto" else "") +
                    (if (user.planExpiryMillis > 0L) " · ends " + formatDate(user.planExpiryMillis) else ""),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        StatusDot(text = planStatusText(left), color = statusColor)
    }
    HorizontalDivider(color = cs.outlineVariant)
}

/**
 * Asks if a user you added renewed. Renewed adds the next period, Not renewed moves them to Free.
 * No money is recorded, because users you add yourself do not earn money.
 */
@Composable
internal fun RenewPlanDialog(
    user: AppUser,
    now: Long,
    onRenewed: () -> Unit,
    onNotRenewed: () -> Unit,
    onDismiss: () -> Unit,
    /** True in a Manual app: Renewed records the plan price as app income. */
    recordsMoney: Boolean = false
) {
    NeriboDialog(
        onDismiss = onDismiss,
        title = "Did " + user.name + " renew?",
        subtitle = planText(user.plan) + " · " + planStatusText(user.planDaysLeft(now))
    ) {
        Text(
            text = "Renewed adds " + PLAN_DAYS + " more days. Not renewed moves them to Free. " +
                (if (recordsMoney) "Renewed records the plan price as income."
                else "No money is recorded for users you add yourself."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        TintedButton(
            text = "Renewed",
            onClick = onRenewed,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        TintedButton(
            text = "Not renewed - move to Free",
            onClick = onNotRenewed,
            modifier = Modifier.fillMaxWidth(),
            destructive = true
        )
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
