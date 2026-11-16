package com.westly.opay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.theme.ThemeMode
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView

@Composable
fun ThemesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val current by repository.themeMode.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            OPayTopBar(
                title = "Themes",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                ThemeOption(
                    title = "Light Mode",
                    selected = current == ThemeMode.Light,
                    onClick = { repository.setThemeMode(ThemeMode.Light) }
                )
                ThemeOption(
                    title = "Dark Mode",
                    selected = current == ThemeMode.Dark,
                    onClick = { repository.setThemeMode(ThemeMode.Dark) }
                )
                ThemeOption(
                    title = "System Default",
                    subtitle = "This will use your device settings",
                    selected = current == ThemeMode.System,
                    onClick = { repository.setThemeMode(ThemeMode.System) }
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(c.card)
            .border(1.dp, if (selected) c.green else Color.Transparent, OPayShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = OPayType.body, color = c.text)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = OPayType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (selected) {
            OPayIconView(icon = OPayIcon.Check, size = 18.dp, tint = c.green)
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(1.dp, c.muted, CircleShape)
            )
        }
    }
}
