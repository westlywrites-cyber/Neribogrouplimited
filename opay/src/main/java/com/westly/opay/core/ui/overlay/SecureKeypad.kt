package com.westly.opay.core.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's own numeric keypad for phone numbers, codes and PINs. Fixed key order (no shuffling), no key preview
 * popups and no system keyboard, so nothing about the typed digits reaches the input method.
 * Each key press gives a short haptic tick.
 */
@Composable
fun SecureKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    NumericKeypad(
        onDigit = { digit ->
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onDigit(digit)
        },
        onDelete = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onBackspace()
        },
        onDone = onDone,
        modifier = modifier
    )
}
