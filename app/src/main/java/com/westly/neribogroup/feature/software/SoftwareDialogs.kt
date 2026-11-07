package com.westly.neribogroup.feature.software

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.NGN_RATE_MAX
import com.westly.neribogroup.data.NGN_RATE_MIN
import com.westly.neribogroup.data.RateInfo
import com.westly.neribogroup.data.RateSource
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FieldGap
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatDateTime
import com.westly.neribogroup.ui.formatNaira
import com.westly.neribogroup.ui.formatRate
import com.westly.neribogroup.ui.formatUsd
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.UUID

/** Keeps only digits and one dot, with at most [maxDecimals] digits after the dot. */
internal fun cleanDecimal(raw: String, maxDecimals: Int): String {
    val builder = StringBuilder()
    var dotSeen = false
    var decimals = 0
    for (c in raw) {
        if (c.isDigit()) {
            if (!dotSeen) {
                builder.append(c)
            } else if (decimals < maxDecimals) {
                builder.append(c)
                decimals++
            }
        } else if (c == '.' && !dotSeen && maxDecimals > 0) {
            dotSeen = true
            builder.append(c)
        }
    }
    return builder.toString().take(14)
}

/** A rate as plain text for a field: up to 4 decimals, no grouping, no trailing zeros. */
internal fun plainRate(value: Double): String =
    String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')

/** Dollars typed as text to cents. Null when the text is not a number. */
private fun dollarsToCents(text: String): Long? {
    if (text.isBlank() || text == ".") return null
    return runCatching {
        BigDecimal(text).movePointRight(2).setScale(0, RoundingMode.DOWN).toLong()
    }.getOrNull()
}

/** The short line under a rate: where it came from. */
internal fun rateSourceText(info: RateInfo): String = when (info.source) {
    RateSource.LIVE -> "Live rate, updated " + formatDateTime(info.updatedMillis)
    RateSource.MANUAL -> "Your typed rate"
    RateSource.DEFAULT -> "Default rate"
}

/**
 * Converts dollars from the $ account into naira income. The preview and the confirm use the SAME
 * rate value. One request id is made when the dialog opens, so a repeated tap cannot convert twice.
 */
@Composable
fun ConvertUsdDialog(
    balanceCents: Long,
    ngnRate: RateInfo,
    ratesLoading: Boolean,
    onRefreshRates: () -> Unit,
    onConvert: (usdCents: Long, ngnPerUsd: Double, requestId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val requestId = remember { UUID.randomUUID().toString() }
    var amountText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf(plainRate(ngnRate.perUsd)) }
    var rateEdited by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    // A new live rate fills the field, unless the owner typed their own for this conversion.
    LaunchedEffect(ngnRate.perUsd) {
        if (!rateEdited) rateText = plainRate(ngnRate.perUsd)
    }

    val cents = dollarsToCents(amountText)
    val rate = rateText.toDoubleOrNull()
    val rateOk = rate != null && rate >= NGN_RATE_MIN && rate <= NGN_RATE_MAX
    val amountOk = cents != null && cents >= 1L && cents <= balanceCents
    val ngn: Long = if (amountOk && rateOk && cents != null && rate != null) {
        Math.round(cents / 100.0 * rate)
    } else {
        0L
    }

    val amountError: String? = when {
        amountText.isEmpty() -> null
        (cents ?: 0L) < 1L -> "Enter an amount above \$0.00."
        (cents ?: 0L) > balanceCents -> "You only have " + formatUsd(balanceCents) + " in your \$ account."
        else -> null
    }
    val rateError: String? = when {
        rateText.isEmpty() -> "Enter a rate."
        !rateOk -> "The rate must be between " + formatNaira(NGN_RATE_MIN.toLong()) + " and " +
            formatNaira(NGN_RATE_MAX.toLong()) + " per \$1."
        else -> null
    }
    val canConvert = amountOk && rateOk && ngn >= 1L && !done

    val rateNote = when {
        rateEdited -> "Typed for this conversion"
        else -> rateSourceText(ngnRate)
    }

    NeriboDialog(
        onDismiss = onDismiss,
        title = "Convert to naira",
        subtitle = "Balance " + formatUsd(balanceCents)
    ) {
        LabeledField(
            label = "Amount in \$",
            value = amountText,
            onValueChange = { amountText = cleanDecimal(it, 2) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            errorText = amountError
        )
        TextButton(
            onClick = {
                val whole = balanceCents / 100L
                val rest = (balanceCents % 100L).toString().padStart(2, '0')
                amountText = whole.toString() + "." + rest
            },
            enabled = balanceCents > 0L
        ) {
            Text("Convert all")
        }

        Spacer(modifier = Modifier.height(FieldGap / 2))

        LabeledField(
            label = "Rate (₦ per \$1)",
            value = rateText,
            onValueChange = {
                rateText = cleanDecimal(it, 4)
                rateEdited = true
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            errorText = rateError
        )
        Text(
            text = rateNote,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRefreshRates, enabled = !ratesLoading) {
            Text(if (ratesLoading) "Refreshing…" else "Refresh rates")
        }

        Spacer(modifier = Modifier.height(FieldGap / 2))

        Text(
            text = if (ngn >= 1L) "You receive " + formatNaira(ngn) else "You receive ₦0",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "This is added to your income. The dollars leave the \$ account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Convert",
                onClick = {
                    if (canConvert && cents != null && rate != null) {
                        done = true
                        onConvert(cents, rate, requestId)
                        onDismiss()
                    }
                },
                enabled = canConvert
            )
        }
    }
}

/** The owner types a rate for one currency ("Units per $1"), or goes back to the live rate. */
@Composable
fun RateDialog(
    code: String,
    current: RateInfo,
    onSave: (Double) -> Unit,
    onUseLive: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(plainRate(current.perUsd)) }
    val value = text.toDoubleOrNull()
    val valid = value != null && SoftwareEngine.isValidRate(code, value)
    val error: String? = when {
        text.isEmpty() -> null
        valid -> null
        code == "NGN" -> "The rate must be between " + formatNaira(NGN_RATE_MIN.toLong()) + " and " +
            formatNaira(NGN_RATE_MAX.toLong()) + "."
        else -> "Enter a rate above 0."
    }

    NeriboDialog(
        onDismiss = onDismiss,
        title = "$code rate",
        subtitle = "Now " + formatRate(current.perUsd) + " per \$1 · " + rateSourceText(current) +
            (if (current.source != RateSource.LIVE && current.updatedMillis > 0L) {
                " (" + formatDate(current.updatedMillis) + ")"
            } else {
                ""
            })
    ) {
        LabeledField(
            label = "Units per \$1",
            value = text,
            onValueChange = { text = cleanDecimal(it, 4) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            errorText = error
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                onUseLive()
                onDismiss()
            }) {
                Text("Use live rate")
            }
        }
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Save",
                onClick = {
                    if (valid && value != null) {
                        onSave(value)
                        onDismiss()
                    }
                },
                enabled = valid
            )
        }
    }
}
