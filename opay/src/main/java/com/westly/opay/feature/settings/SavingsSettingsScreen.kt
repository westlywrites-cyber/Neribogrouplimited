package com.westly.opay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayToggle
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.data.SettingsKeys
import com.westly.opay.data.SettingsMerge

private const val SHEET_NONE = 0
private const val SHEET_AUTOMATIC = 1
private const val SHEET_INTEREST = 2

@Composable
fun SavingsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val settings by repository.appSettings.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(SHEET_NONE) }
    val automaticOn = SettingsMerge.flag(settings, SettingsKeys.SAVINGS_AUTOMATIC_INVESTMENT, default = false)
    val interestOn = SettingsMerge.flag(settings, SettingsKeys.SAVINGS_INTEREST, default = true)

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
                title = "Savings settings",
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
                Text(
                    text = "Savings settings",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = c.green,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
                Text(
                    text = "Manage your savings with the following options:",
                    fontSize = 11.sp,
                    color = c.muted,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 14.dp)
                )
                SavingsOption(
                    title = "Automatic investment",
                    icon = OPayIcon.Zap,
                    onClick = { sheet = SHEET_AUTOMATIC }
                )
                SavingsOption(
                    title = "Interest setting",
                    subtitle = "Enable or disable interest on your savings",
                    showNewTag = true,
                    onClick = { sheet = SHEET_INTEREST }
                )
            }
        }
        OPayBottomSheet(
            visible = sheet == SHEET_AUTOMATIC,
            onDismiss = { sheet = SHEET_NONE }
        ) {
            SheetToggleRow(
                label = "Automatic investment",
                checked = automaticOn,
                onCheckedChange = {
                    repository.setSettings(mapOf(SettingsKeys.SAVINGS_AUTOMATIC_INVESTMENT to it))
                }
            )
        }
        OPayBottomSheet(
            visible = sheet == SHEET_INTEREST,
            onDismiss = { sheet = SHEET_NONE }
        ) {
            SheetToggleRow(
                label = "Receive interest on savings",
                checked = interestOn,
                onCheckedChange = { repository.setSettings(mapOf(SettingsKeys.SAVINGS_INTEREST to it)) }
            )
        }
    }
}

@Composable
private fun SavingsOption(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: OPayIcon? = null,
    showNewTag: Boolean = false
) {
    val c = OPay.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .border(1.dp, c.divider, OPayShapes.card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            OPayIconView(icon = icon, size = 20.dp, tint = c.green)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = title, style = OPayType.body, color = c.text)
                if (showNewTag) {
                    PinkTag(text = "NEW")
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = OPayType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
    }
}

@Composable
private fun SheetToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = OPayType.body,
            color = OPay.colors.text,
            modifier = Modifier.weight(1f)
        )
        OPayToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}
