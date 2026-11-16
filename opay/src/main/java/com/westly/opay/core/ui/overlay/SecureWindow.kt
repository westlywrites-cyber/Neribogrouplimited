package com.westly.opay.core.ui.overlay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private var secureOwners = 0

/**
 * Blocks screenshots, screen recording and the recent-apps preview (FLAG_SECURE) while this composable is part of
 * the composition. Screens that show or collect secrets call it once. Two screens can overlap during a navigation
 * animation, so the flag is removed only when the last caller leaves.
 */
@Composable
fun SecureWindow() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        secureOwners += 1
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            secureOwners -= 1
            if (secureOwners == 0) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
