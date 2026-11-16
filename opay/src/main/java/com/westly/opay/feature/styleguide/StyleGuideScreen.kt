package com.westly.opay.feature.styleguide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.theme.ThemeMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayRingAvatar
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTile
import com.westly.opay.core.ui.components.OPayToggle
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.util.formatNaira
import com.westly.opay.core.util.maskAccount
import com.westly.opay.core.util.parseNairaToKobo
import com.westly.opay.core.ui.overlay.EmptyState
import com.westly.opay.core.ui.overlay.NumericKeypad
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.core.ui.overlay.rememberOPayToast

@Composable
fun StyleGuideScreen(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val swatches = listOf(
        "bg" to c.bg,
        "card" to c.card,
        "cardAlt" to c.cardAlt,
        "green" to c.green,
        "greenDark" to c.greenDark,
        "greenSoft" to c.greenSoft,
        "text" to c.text,
        "muted" to c.muted,
        "divider" to c.divider,
        "danger" to c.danger,
        "warn" to c.warn,
        "onGreen" to c.onGreen
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = "Style guide",
            style = OPayType.headline,
            color = c.text,
            modifier = Modifier.padding(vertical = 14.dp)
        )

        SectionTitle("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.System).forEach { option ->
                ModeOption(
                    label = option.name,
                    selected = option == mode,
                    onClick = { onModeChange(option) }
                )
            }
        }

        SectionTitle("Colours")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.card, OPayShapes.card)
                .padding(12.dp)
        ) {
            swatches.forEach { (name, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color, OPayShapes.tile)
                            .border(1.dp, c.divider, OPayShapes.tile)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = name,
                        style = OPayType.body,
                        color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "#%06X".format(color.toArgb() and 0xFFFFFF),
                        style = OPayType.caption,
                        color = c.muted
                    )
                }
            }
        }

        SectionTitle("Text styles")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.card, OPayShapes.card)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StyleSample("display 26", OPayType.display, c.text, "Good evening")
            StyleSample("headline 22", OPayType.headline, c.text, "Transfer to bank")
            StyleSample("money 22 tabular", OPayType.money, c.green, "\u20A612,480.50")
            StyleSample("title 17", OPayType.title, c.text, "Transaction details")
            StyleSample("titleSmall 15", OPayType.titleSmall, c.text, "View all")
            StyleSample("body 14", OPayType.body, c.text, "Send money to any bank account")
            StyleSample("bodySmall 13", OPayType.bodySmall, c.text, "Free transfers left this month")
            StyleSample("bodyMuted 14", OPayType.bodyMuted, c.muted, "Account number")
            StyleSample("caption 12", OPayType.caption, c.muted, "Success rate 98%")
            StyleSample("captionSmall 11", OPayType.captionSmall, c.muted, "Home")
        }

        // BEGIN PHASE 3 SECTION
        var toggleOn by remember { mutableStateOf(true) }
        var pickedChip by remember { mutableStateOf(0) }
        var fieldText by remember { mutableStateOf("") }

        SectionTitle("Buttons")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OPayButton(text = "Primary", onClick = {}, style = ButtonStyle.Primary)
            OPayButton(text = "Dark", onClick = {}, style = ButtonStyle.Dark)
            OPayButton(text = "Outline", onClick = {}, style = ButtonStyle.Outline)
            OPayButton(text = "Disabled", onClick = {}, enabled = false)
        }

        SectionTitle("Top bar")
        OPayCard {
            OPayTopBar(
                title = "Transfer to Bank",
                onBack = {},
                actionText = "History",
                onAction = {}
            )
        }

        SectionTitle("Card and rows")
        OPayCard {
            OPayRow(title = "Bank Transfer", onClick = {}, icon = OPayIcon.Bank, value = "Free")
            OPayRowDivider()
            OPayRow(title = "Airtime", onClick = {}, icon = OPayIcon.Phone)
            OPayRowDivider()
            OPayRow(
                title = "Hide balance",
                icon = OPayIcon.Lock,
                showChevron = false,
                trailing = { OPayToggle(checked = toggleOn, onCheckedChange = { toggleOn = it }) }
            )
        }

        SectionTitle("Chips")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Food", "Bills", "Transport").forEachIndexed { index, label ->
                OPayChip(text = label, selected = pickedChip == index, onClick = { pickedChip = index })
            }
        }

        SectionTitle("Underline field (10 digits max)")
        OPayUnderlineField(
            value = fieldText,
            onValueChange = { fieldText = it },
            placeholder = "Enter account number",
            keyboardType = KeyboardType.Number,
            maxLength = 10,
            leading = { OPayIconView(icon = OPayIcon.Bank, size = 18.dp, tint = c.green) }
        )

        SectionTitle("Ring avatar")
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OPayRingAvatar(size = 24.dp)
            OPayRingAvatar()
            OPayRingAvatar(size = 54.dp)
        }

        SectionTitle("Tiles")
        OPayCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    OPayIcon.Phone to "Airtime",
                    OPayIcon.Data to "Data",
                    OPayIcon.Tv to "TV",
                    OPayIcon.Bank to "To Bank"
                ).forEach { (tileIcon, tileLabel) ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        OPayTile(icon = tileIcon, label = tileLabel, onClick = {})
                    }
                }
            }
        }

        SectionTitle("Icons")
        OPayCard {
            OPayIcon.entries.chunked(4).forEach { rowIcons ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    rowIcons.forEach { icon ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OPayIconView(icon = icon, size = 24.dp, tint = c.text)
                            Text(text = icon.name, style = OPayType.captionSmall, color = c.muted)
                        }
                    }
                    repeat(4 - rowIcons.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SectionTitle("Money helpers")
        OPayCard {
            Text(text = "formatNaira(1248050) = ${formatNaira(1248050L)}", style = OPayType.bodySmall, color = c.text)
            Text(text = "formatNaira(-50000) = ${formatNaira(-50000L)}", style = OPayType.bodySmall, color = c.text)
            Text(text = "maskAccount(6012345678) = ${maskAccount("6012345678")}", style = OPayType.bodySmall, color = c.text)
            Text(text = "parseNairaToKobo(1,500) = ${parseNairaToKobo("1,500")}", style = OPayType.bodySmall, color = c.text)
            Text(text = "parseNairaToKobo(1500.5) = ${parseNairaToKobo("1500.5")}", style = OPayType.bodySmall, color = c.text)
            Text(text = "parseNairaToKobo(abc) = ${parseNairaToKobo("abc")}", style = OPayType.bodySmall, color = c.text)
        }
        // END PHASE 3 SECTION

        // BEGIN PHASE 4 SECTION
        var dialogIndex by remember { mutableStateOf(-1) }
        var sheetVisible by remember { mutableStateOf(false) }
        var keypadOpen by remember { mutableStateOf(false) }
        var keypadText by remember { mutableStateOf("") }
        var keypadDone by remember { mutableStateOf(0) }
        var loaderOn by remember { mutableStateOf(false) }
        val toast = rememberOPayToast()

        SectionTitle("Dialogs")
        OPayCard {
            OPayRow(title = "Reminder (Recheck / Continue)", onClick = { dialogIndex = 0 })
            OPayRowDivider()
            OPayRow(title = "Log out (No / Yes)", onClick = { dialogIndex = 1 })
            OPayRowDivider()
            OPayRow(title = "Cancel payment (Continue to pay / Cancel)", onClick = { dialogIndex = 2 })
            OPayRowDivider()
            OPayRow(title = "Transaction alerts (Disable / Active Now)", onClick = { dialogIndex = 3 })
            OPayRowDivider()
            OPayRow(title = "Verification (Exit / Continue)", onClick = { dialogIndex = 4 })
            OPayRowDivider()
            OPayRow(title = "Single button", onClick = { dialogIndex = 5 })
        }

        SectionTitle("Bottom sheet")
        OPayCard {
            OPayRow(title = "Open payment sheet (drag down or press back to close)", onClick = { sheetVisible = true })
        }

        SectionTitle("Numeric keypad")
        OPayCard {
            OPayRow(
                title = if (keypadOpen) "Hide keypad" else "Show keypad",
                onClick = { keypadOpen = !keypadOpen }
            )
            Text(
                text = "Digits: $keypadText   Done pressed: $keypadDone",
                style = OPayType.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (keypadOpen) {
            NumericKeypad(
                onDigit = { digit -> if (keypadText.length < 12) keypadText += digit },
                onDelete = { keypadText = keypadText.dropLast(1) },
                onDone = { keypadDone += 1 }
            )
            Spacer(Modifier.height(10.dp))
        }

        SectionTitle("Loader")
        OPayCard {
            OPayRow(
                title = if (loaderOn) "Hide loader" else "Show loader",
                onClick = { loaderOn = !loaderOn }
            )
            if (loaderOn) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OPayLoader()
                }
            }
        }

        SectionTitle("Toast")
        OPayCard {
            OPayRow(title = "Show \"PDF Saved\"", onClick = { toast.show("PDF Saved") })
            OPayRowDivider()
            OPayRow(title = "Show \"Click again to exit\"", onClick = { toast.show("Click again to exit") })
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(c.card, OPayShapes.card)
        ) {
            OPayToastHost(state = toast, bottomPadding = 20.dp)
        }

        SectionTitle("Skeleton")
        OPayCard {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 18.dp)
            Spacer(Modifier.height(10.dp))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.7f), height = 14.dp)
            Spacer(Modifier.height(10.dp))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.45f), height = 14.dp)
        }

        SectionTitle("Empty state")
        OPayCard {
            EmptyState(message = "No transactions yet")
        }

        when (dialogIndex) {
            0 -> OPayDialog(
                title = "Reminder",
                message = "Double check the transfer details before you proceed. Please note that successful transfers cannot be reversed.",
                primaryText = "Continue",
                onPrimary = { dialogIndex = -1 },
                onDismiss = { dialogIndex = -1 },
                secondaryText = "Recheck",
                onSecondary = { dialogIndex = -1 }
            )
            1 -> OPayDialog(
                title = "Are you sure to log out of OPay?",
                message = null,
                primaryText = "Yes",
                onPrimary = { dialogIndex = -1 },
                onDismiss = { dialogIndex = -1 },
                secondaryText = "No",
                onSecondary = { dialogIndex = -1 }
            )
            2 -> OPayDialog(
                title = "Reminder",
                message = "Do you want to cancel this payment?",
                primaryText = "Continue to pay",
                onPrimary = { dialogIndex = -1 },
                onDismiss = { dialogIndex = -1 },
                secondaryText = "Cancel",
                onSecondary = { dialogIndex = -1 }
            )
            3 -> OPayDialog(
                title = "Disable Transaction Alerts?",
                message = null,
                primaryText = "Active Now",
                onPrimary = { dialogIndex = -1 },
                onDismiss = { dialogIndex = -1 },
                secondaryText = "Disable",
                onSecondary = { dialogIndex = -1 }
            )
            4 -> OPayDialog(
                title = "Verification is not completed",
                message = null,
                primaryText = "Continue",
                onPrimary = { dialogIndex = -1 },
                onDismiss = { dialogIndex = -1 },
                secondaryText = "Exit",
                onSecondary = { dialogIndex = -1 }
            )
            5 -> OPayDialog(
                title = "Single button dialog",
                message = "One full-width button.",
                primaryText = "OK",
                onPrimary = { dialogIndex = -1 },
                onDismiss = { dialogIndex = -1 }
            )
        }

        OPayBottomSheet(visible = sheetVisible, onDismiss = { sheetVisible = false }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OPayIconView(
                    icon = OPayIcon.Close,
                    size = 18.dp,
                    tint = c.text,
                    modifier = Modifier.clickable { sheetVisible = false }
                )
                Spacer(Modifier.weight(1f))
                Text(text = "Use Payment PIN", style = OPayType.body, color = c.green)
            }
            Text(
                text = formatNaira(500000L),
                style = OPayType.headline.copy(fontSize = 26.sp),
                color = c.text,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
            )
            listOf(
                "Bank" to "Sample Bank",
                "Account Number" to maskAccount("6012345678"),
                "Fee" to formatNaira(0L)
            ).forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = label, style = OPayType.body, color = c.muted)
                    Text(text = value, style = OPayType.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                }
            }
            Spacer(Modifier.height(12.dp))
            OPayButton(text = "Pay", onClick = { sheetVisible = false })
        }
        // END PHASE 4 SECTION

        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = OPayType.caption,
        color = OPay.colors.muted,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .background(if (selected) c.green else c.cardAlt, OPayShapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = OPayType.bodySmall,
            color = if (selected) c.onGreen else c.text
        )
    }
}

@Composable
private fun StyleSample(label: String, style: TextStyle, color: Color, sample: String) {
    Column {
        Text(text = label, style = OPayType.captionSmall, color = OPay.colors.muted)
        Text(text = sample, style = style, color = color)
    }
}
