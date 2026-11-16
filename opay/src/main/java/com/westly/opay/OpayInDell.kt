package com.westly.opay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.westly.opay.core.theme.OPayTheme
import com.westly.opay.navigation.MainScaffold

/** The whole OPay app, as one screen. NCO shows it inside a window of the Dell desktop. */
@Composable
fun OpayInDell() {
    val repository = (LocalContext.current.applicationContext as OPayApplication).repository
    val themeMode by repository.themeMode.collectAsState()
    OPayTheme(mode = themeMode) {
        MainScaffold(exitOnBackTwice = false)
    }
}
