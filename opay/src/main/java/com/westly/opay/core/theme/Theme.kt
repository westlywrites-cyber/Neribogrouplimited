package com.westly.opay.core.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object OPay {
    val colors: OPayColors
        @Composable @ReadOnlyComposable
        get() = LocalOPayColors.current
}

@Composable
fun OPayTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) OPayDarkColors else OPayLightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.green,
            onPrimary = colors.onGreen,
            secondary = colors.greenDark,
            onSecondary = colors.text,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.card,
            onSurface = colors.text,
            surfaceVariant = colors.cardAlt,
            onSurfaceVariant = colors.muted,
            outline = colors.divider,
            error = colors.danger,
            onError = colors.onGreen
        )
    } else {
        lightColorScheme(
            primary = colors.green,
            onPrimary = colors.onGreen,
            secondary = colors.greenDark,
            onSecondary = colors.card,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.card,
            onSurface = colors.text,
            surfaceVariant = colors.cardAlt,
            onSurfaceVariant = colors.muted,
            outline = colors.divider,
            error = colors.danger,
            onError = colors.onGreen
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    val baseDensity = LocalDensity.current
    val textDensity = remember(baseDensity) { Density(baseDensity.density, baseDensity.fontScale * TextScale) }
    CompositionLocalProvider(
        LocalOPayColors provides colors,
        LocalDensity provides textDensity
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OPayMaterialTypography,
            shapes = OPayMaterialShapes,
            content = content
        )
    }
}

private var darkHeaderOwners = 0

/**
 * Keeps the status-bar icons light while a screen with a dark-green header is shown,
 * in both themes, and restores the theme default once the last such screen leaves.
 */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val themeUsesLightIcons = OPay.colors.bg.luminance() < 0.5f
    DisposableEffect(themeUsesLightIcons) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        darkHeaderOwners += 1
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            darkHeaderOwners -= 1
            if (darkHeaderOwners == 0) {
                controller?.isAppearanceLightStatusBars = !themeUsesLightIcons
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
