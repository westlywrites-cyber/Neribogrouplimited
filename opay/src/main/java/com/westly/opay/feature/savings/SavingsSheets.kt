package com.westly.opay.feature.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.overlay.NumericKeypad
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayToastState
import com.westly.opay.core.util.formatNaira

private const val MAX_NAIRA_DIGITS = 9
private const val KOBO_PER_NAIRA = 100L

/** Tells one savings call at a time apart and keeps the text of the last failure for the open sheet. */
@Stable
class SavingsAction(private val toast: OPayToastState) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun clear() {
        error = null
    }

    /**
     * Starts [call] unless another call is running. [call] reports its outcome once: null on success, otherwise the
     * text to show, which is also shown as a toast (a missing connection gives "No internet connection").
     */
    fun start(call: ((String?) -> Unit) -> Unit, onSuccess: () -> Unit) {
        if (busy) return
        busy = true
        error = null
        call { message ->
            busy = false
            if (message == null) {
                onSuccess()
            } else {
                error = message
                toast.show(message)
            }
        }
    }
}

@Composable
fun rememberSavingsAction(toast: OPayToastState): SavingsAction = remember(toast) { SavingsAction(toast) }

/** Whole naira as kobo, or null when [digits] holds no amount. */
internal fun nairaDigitsToKobo(digits: String): Long? = digits.toLongOrNull()?.takeIf { it > 0L }?.times(KOBO_PER_NAIRA)

/**
 * Asks for an amount in whole naira on the secure keypad. [allKobo], when given, adds a link that sends that exact
 * amount, used to take everything out of a position.
 */
@Composable
internal fun SavingsAmountSheet(
    visible: Boolean,
    title: String,
    confirmText: String,
    action: SavingsAction,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    note: String? = null,
    allKobo: Long? = null,
    allText: String = "Withdraw all"
) {
    val c = OPay.colors
    var digits by rememberSaveable(visible) { mutableStateOf("") }
    val kobo = nairaDigitsToKobo(digits)
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        Text(text = title, style = OPayType.title, color = c.text, modifier = Modifier.fillMaxWidth())
        if (note != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = note, style = OPayType.caption, color = c.muted)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "\u20A6" + (digits.ifEmpty { "0" }),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        val failure = action.error
        if (failure != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = failure,
                style = OPayType.caption,
                color = c.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (allKobo != null && allKobo > 0L) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = allText + " (" + formatNaira(allKobo) + ")",
                style = OPayType.bodySmall,
                fontWeight = FontWeight.Bold,
                color = c.green,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!action.busy) onConfirm(allKobo) }
                    .padding(vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        NumericKeypad(
            onDigit = { digit ->
                action.clear()
                if (digits.length < MAX_NAIRA_DIGITS && !(digits.isEmpty() && digit == '0')) digits += digit
            },
            onDelete = {
                action.clear()
                digits = digits.dropLast(1)
            },
            onDone = { kobo?.let { if (!action.busy) onConfirm(it) } }
        )
        Spacer(Modifier.height(12.dp))
        SavingsButton(
            text = if (action.busy) "Please wait" else confirmText,
            onClick = { kobo?.let { if (!action.busy) onConfirm(it) } }
        )
    }
}

/** Collects the name, the target and an optional first deposit of a new target. */
@Composable
internal fun CreateTargetSheet(
    visible: Boolean,
    initialName: String,
    action: SavingsAction,
    onDismiss: () -> Unit,
    onCreate: (name: String, targetKobo: Long, firstKobo: Long, months: Int?) -> Unit
) {
    val c = OPay.colors
    var name by rememberSaveable(visible, initialName) { mutableStateOf(initialName) }
    var target by rememberSaveable(visible) { mutableStateOf("") }
    var first by rememberSaveable(visible) { mutableStateOf("") }
    var months by rememberSaveable(visible) { mutableStateOf(0) }
    val targetKobo = nairaDigitsToKobo(target)
    val firstKobo = if (first.isEmpty()) 0L else nairaDigitsToKobo(first)
    val ready = name.isNotBlank() && targetKobo != null && firstKobo != null
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        Text(text = "Create Target", style = OPayType.title, color = c.text, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OPayUnderlineField(value = name, onValueChange = { name = it; action.clear() }, placeholder = "Target name", maxLength = 50)
        OPayUnderlineField(
            value = target,
            onValueChange = { target = it.filter { ch -> ch in '0'..'9' }.take(MAX_NAIRA_DIGITS); action.clear() },
            placeholder = "Target amount (\u20A6)",
            keyboardType = KeyboardType.Number
        )
        OPayUnderlineField(
            value = first,
            onValueChange = { first = it.filter { ch -> ch in '0'..'9' }.take(MAX_NAIRA_DIGITS); action.clear() },
            placeholder = "First deposit (\u20A6, optional)",
            keyboardType = KeyboardType.Number
        )
        Spacer(Modifier.height(10.dp))
        Text(text = "Savings duration", style = OPayType.caption, color = c.muted)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 3, 6, 12).forEach { option ->
                val selected = months == option
                Text(
                    text = if (option == 0) "No end" else "$option mo",
                    style = OPayType.caption,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) c.onGreen else c.text,
                    modifier = Modifier
                        .background(if (selected) c.green else c.cardAlt, OPayShapes.chip)
                        .clickable { months = option }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        val failure = action.error
        if (failure != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = failure, style = OPayType.caption, color = c.danger, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(14.dp))
        SavingsButton(
            text = if (action.busy) "Please wait" else "Create Target",
            onClick = {
                if (ready && !action.busy) {
                    onCreate(name.trim(), targetKobo ?: 0L, firstKobo ?: 0L, months.takeIf { it > 0 })
                }
            }
        )
    }
}

/** Chooses the share of each spend that Spend & Save sets aside, then the first amount to put in. */
@Composable
internal fun SpendSaveSheet(
    visible: Boolean,
    action: SavingsAction,
    onDismiss: () -> Unit,
    onCreate: (percent: Int, firstKobo: Long) -> Unit
) {
    val c = OPay.colors
    var percent by rememberSaveable(visible) { mutableStateOf(10) }
    var first by rememberSaveable(visible) { mutableStateOf("") }
    val firstKobo = nairaDigitsToKobo(first)
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        Text(text = "Spend & Save", style = OPayType.title, color = c.text, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(text = "Percentage to save", style = OPayType.caption, color = c.muted)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20).forEach { option ->
                val selected = percent == option
                Text(
                    text = "$option%",
                    style = OPayType.caption,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) c.onGreen else c.text,
                    modifier = Modifier
                        .background(if (selected) c.green else c.cardAlt, OPayShapes.chip)
                        .clickable { percent = option }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OPayUnderlineField(
            value = first,
            onValueChange = { first = it.filter { ch -> ch in '0'..'9' }.take(MAX_NAIRA_DIGITS); action.clear() },
            placeholder = "Amount to save now (\u20A6)",
            keyboardType = KeyboardType.Number
        )
        val failure = action.error
        if (failure != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = failure, style = OPayType.caption, color = c.danger, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(14.dp))
        SavingsButton(
            text = if (action.busy) "Please wait" else "Turn on",
            onClick = { firstKobo?.let { if (!action.busy) onCreate(percent, it) } }
        )
    }
}
