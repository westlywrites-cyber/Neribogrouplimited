package com.westly.opay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.feature.register.PAYMENT_PIN_LENGTH
import com.westly.opay.feature.register.PinDots

private const val PIN_HINT_TEXT = "Do not use repeated or consecutive numbers"

/**
 * One 4-digit payment PIN page: a heading, a grey line, four boxes and either the error line or the hint. Used by
 * the change flow and the forgot flow. The digits are never shown, only how many were typed.
 */
@Composable
internal fun PaymentPinPage(
    title: String,
    subtitle: String,
    filled: Int,
    message: String?,
    shakeCount: Int,
    showHint: Boolean
) {
    val c = OPay.colors
    Text(text = title, style = OPayType.headline, color = c.text)
    Text(
        text = subtitle,
        style = OPayType.bodySmall,
        color = c.muted,
        modifier = Modifier.padding(top = 6.dp)
    )
    PinDots(
        length = PAYMENT_PIN_LENGTH,
        filled = filled,
        hasError = message != null,
        shakeCount = shakeCount,
        modifier = Modifier.padding(top = 24.dp)
    )
    if (message != null) {
        Text(
            text = message,
            style = OPayType.caption,
            color = c.danger,
            modifier = Modifier.padding(top = 12.dp)
        )
    } else if (showHint) {
        Text(
            text = PIN_HINT_TEXT,
            style = OPayType.caption,
            color = c.muted,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
