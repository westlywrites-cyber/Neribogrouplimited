package com.westly.neribogroup.feature.appdetail

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LocalBiometricGate
import com.westly.neribogroup.feature.common.StatColumn
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatNaira

/**
 * One app's Google Play Balance: subscription money and ad money on separate lines, and a Withdraw
 * button. The balance is not profit until it is withdrawn.
 */
@Composable
fun PlayAppCard(app: AppData, onWithdraw: () -> Unit, showName: Boolean = true) {
    val context = LocalContext.current
    val gate = LocalBiometricGate.current
    var confirm by remember { mutableStateOf(false) }
    val total = app.playBalance

    FlatCard {
        if (showName) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn(
                label = "Subscriptions",
                value = formatNaira(app.playSubsBalance),
                modifier = Modifier.weight(1f)
            )
            StatColumn(
                label = "Google Ads",
                value = if (app.adsEnabled || app.playAdsBalance > 0L) formatNaira(app.playAdsBalance) else "Off",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Balance " + formatNaira(total),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        TintedButton(
            text = "Withdraw",
            onClick = { confirm = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = total > 0L
        )
    }

    if (confirm) {
        ConfirmDialog(
            heading = "Withdraw " + formatNaira(total) + "?",
            message = "The money moves from the Google Play Balance into " + app.name +
                "'s profit, and the balance goes back to ₦0.",
            confirmLabel = "Withdraw",
            onConfirm = {
                confirm = false
                gate.verify(
                    title = "Withdraw Google Play balance?",
                    subtitle = formatNaira(total) + " moves into " + app.name + "'s profit.",
                    logText = "Google Play balance withdrawn for " + app.name + ": " + formatNaira(total)
                ) {
                    onWithdraw()
                    Toast.makeText(context, "Withdrawn successfully", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { confirm = false }
        )
    }
}
