package com.westly.opay.feature.transfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.opay.backend.BackendCodes
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.overlay.OPayDialog

private const val UNVERIFIED_TEXT = "Could not verify account. Check your connection"

/** True for the states that show a red line instead of a name. */
internal fun LookupState.isMessage(): Boolean =
    this === LookupState.NotFound || this === LookupState.Unverified || this === LookupState.OwnAccount

/** The red line under the account box for a lookup that did not give a name. Draws nothing for other states. */
@Composable
internal fun LookupMessage(state: LookupState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    when (state) {
        LookupState.NotFound -> Text(
            text = BackendCodes.messageFor(BackendCodes.ACCOUNT_NOT_FOUND),
            style = OPayType.bodySmall,
            color = c.danger,
            modifier = modifier.padding(top = 8.dp)
        )
        LookupState.OwnAccount -> Text(
            text = BackendCodes.messageFor(BackendCodes.SELF_TRANSFER),
            style = OPayType.bodySmall,
            color = c.danger,
            modifier = modifier.padding(top = 8.dp)
        )
        LookupState.Unverified -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = UNVERIFIED_TEXT,
                style = OPayType.bodySmall,
                color = c.danger,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Retry",
                style = OPayType.bodySmall,
                fontWeight = FontWeight.Bold,
                color = c.green,
                modifier = Modifier
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        else -> Unit
    }
}

/** The "Operation Too Frequent" box, shown only while the lookup state is rate limited. */
@Composable
internal fun LookupRateLimitedDialog(state: LookupState, onDismiss: () -> Unit) {
    if (state != LookupState.RateLimited) return
    OPayDialog(
        title = BackendCodes.messageFor(BackendCodes.RATE_LIMITED),
        message = null,
        primaryText = "OK",
        onPrimary = onDismiss,
        onDismiss = onDismiss
    )
}
