package com.westly.neribogroup.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.westly.neribogroup.data.dayKey
import kotlinx.coroutines.delay

/**
 * The current time, refreshed when the day changes (12 am) while a screen stays open.
 * "New users today", Premium today and Pro today go back to 0 at that moment.
 */
@Composable
fun rememberNowMillis(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            val current = System.currentTimeMillis()
            if (dayKey(current) != dayKey(now)) {
                now = current
            }
        }
    }
    return now
}
