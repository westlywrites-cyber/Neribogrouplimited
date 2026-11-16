package com.westly.opay.feature.statement

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayToggle
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.OverlayScrim
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.register.PAYMENT_PIN_LENGTH
import com.westly.opay.feature.register.PinDots
import kotlinx.coroutines.delay

private const val EXCEL_MESSAGE = "Excel statements are not available yet"

@Composable
fun DownloadStatementScreen(
    onBack: () -> Unit,
    onRecords: () -> Unit,
    onForgotPin: () -> Unit,
    onStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadStatementViewModel = viewModel<DownloadStatementViewModel>()
) {
    val c = OPay.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()
    val currentOnStarted by rememberUpdatedState(onStarted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DownloadStatementEvent.Toast -> toast.show(event.text)
                DownloadStatementEvent.Started -> currentOnStarted()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            OPayTopBar(
                title = "Download Statement",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Records",
                onAction = onRecords
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimeFrameCard(state, viewModel)
                EmailCard(state.email) { viewModel.showEmailSheet(true) }
                BasedOnCard(state, viewModel)
                if (state.account != null) {
                    FileTypeCard(state, viewModel)
                    AddressCard(state.showAddress, viewModel::setShowAddress)
                }
                Spacer(Modifier.height(4.dp))
            }
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).navigationBarsPadding()) {
                OPayButton(text = "Continue", onClick = viewModel::onContinue, enabled = state.ready)
            }
        }
        StatementDatePickerSheet(
            visible = state.picker != null,
            bounds = state.picker?.let { viewModel.pickerBounds(it) },
            initial = state.picker?.let { if (it == DateTarget.Start) state.start else state.end },
            onDismiss = viewModel::closePicker,
            onConfirm = { date -> state.picker?.let { viewModel.onDatePicked(it, date) } }
        )
        if (state.accountDialog) {
            AccountDialog(
                number = viewModel.accountNumber(),
                selected = state.account != null,
                onChoose = viewModel::chooseAccount,
                onDismiss = { viewModel.showAccountDialog(false) }
            )
        }
        FileTypeSheet(
            visible = state.fileSheet,
            current = state.fileType,
            onDismiss = { viewModel.showFileSheet(false) },
            onConfirm = viewModel::setFileType
        )
        EditEmailSheet(
            visible = state.emailSheet,
            current = state.email,
            onDismiss = { viewModel.showEmailSheet(false) },
            onSave = viewModel::saveEmail
        )
        if (state.confirm) {
            ConfirmDialog(
                account = state.account.orEmpty(),
                maskedEmail = StatementFormat.recordsEmail(state.email),
                onRecheck = viewModel::closeConfirm,
                onGenerate = viewModel::onGenerate
            )
        }
        PinSheet(state, viewModel, onForgotPin)
        state.notice?.let { text ->
            OPayDialog(
                title = text,
                message = null,
                primaryText = "OK",
                onPrimary = viewModel::clearNotice,
                onDismiss = viewModel::clearNotice
            )
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.card)
            .background(OPay.colors.card)
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun TimeFrameCard(state: DownloadStatementUiState, viewModel: DownloadStatementViewModel) {
    val c = OPay.colors
    Card {
        Text(text = "Time Frame", style = OPayType.title, fontWeight = FontWeight.Bold, color = c.text)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FrameChip("Custom", state.frame == TimeFrame.Custom, Modifier.weight(1f)) {
                viewModel.selectFrame(TimeFrame.Custom)
            }
            FrameChip("Last month", state.frame == TimeFrame.LastMonth, Modifier.weight(1f)) {
                viewModel.selectFrame(TimeFrame.LastMonth)
            }
            FrameChip("Last 2 years", state.frame == TimeFrame.LastTwoYears, Modifier.weight(1f)) {
                viewModel.selectFrame(TimeFrame.LastTwoYears)
            }
        }
        DashedDivider(Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField("Start Date", state.start, Modifier.weight(1f)) { viewModel.openPicker(DateTarget.Start) }
            DateField("End Date", state.end, Modifier.weight(1f)) { viewModel.openPicker(DateTarget.End) }
        }
    }
}

@Composable
private fun FrameChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = OPay.colors
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(OPayShapes.tile)
            .background(if (selected) c.green.copy(alpha = 0.12f) else c.bg)
            .then(if (selected) Modifier.border(1.dp, c.green, OPayShapes.tile) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = OPayType.bodySmall,
            color = if (selected) c.green else c.text,
            maxLines = 1
        )
        if (selected) Tick(Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun BoxScope.Tick(modifier: Modifier) {
    val c = OPay.colors
    Canvas(modifier = modifier.size(14.dp)) {
        val path = Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, c.green)
        val stroke = 1.3.dp.toPx()
        drawLine(c.onGreen, Offset(size.width * 0.52f, size.height * 0.80f), Offset(size.width * 0.66f, size.height * 0.93f), stroke)
        drawLine(c.onGreen, Offset(size.width * 0.66f, size.height * 0.93f), Offset(size.width * 0.90f, size.height * 0.60f), stroke)
    }
}

@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    val color = OPay.colors.divider
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )
    }
}

@Composable
private fun DateField(label: String, value: java.time.LocalDate?, modifier: Modifier, onClick: () -> Unit) {
    val c = OPay.colors
    Column(modifier = modifier) {
        Text(text = label, style = OPayType.bodySmall, color = c.muted)
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(OPayShapes.tile)
                .background(c.bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value?.let { StatementFormat.fieldDate(it) } ?: "Select",
                style = OPayType.bodySmall,
                color = if (value == null) c.muted else c.text,
                modifier = Modifier.weight(1f)
            )
            Text(text = "\u25BE", style = OPayType.bodySmall, color = c.muted)
        }
    }
}

@Composable
private fun SelectRow(text: String, chosen: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(OPayShapes.tile)
            .background(c.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = OPayType.body,
            color = if (chosen) c.text else c.muted,
            modifier = Modifier.weight(1f)
        )
        OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = c.muted)
    }
}

@Composable
private fun EmailCard(email: String, onEdit: () -> Unit) {
    val c = OPay.colors
    Card {
        Text(text = "Email", style = OPayType.title, fontWeight = FontWeight.Bold, color = c.text)
        Text(
            text = "Your account statement will be sent to your email.",
            style = OPayType.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(OPayShapes.tile)
                .background(c.bg)
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (email.isEmpty()) "Add your email" else StatementFormat.cardEmail(email),
                style = OPayType.body,
                color = if (email.isEmpty()) c.muted else c.text,
                modifier = Modifier.weight(1f)
            )
            Text(text = "Edit >", style = OPayType.body, color = c.green)
        }
    }
}

@Composable
private fun BasedOnCard(state: DownloadStatementUiState, viewModel: DownloadStatementViewModel) {
    val c = OPay.colors
    Card {
        Text(text = "Based On", style = OPayType.title, fontWeight = FontWeight.Bold, color = c.text)
        Text(
            text = "Select the account number you want to generate a statement for",
            style = OPayType.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp)
        )
        SelectRow(state.account ?: "Select your account", state.account != null) {
            viewModel.showAccountDialog(true)
        }
    }
}

@Composable
private fun FileTypeCard(state: DownloadStatementUiState, viewModel: DownloadStatementViewModel) {
    val c = OPay.colors
    Card {
        Text(text = "File Type", style = OPayType.title, fontWeight = FontWeight.Bold, color = c.text)
        Text(
            text = "Select the format in which you would like to receive your account statement",
            style = OPayType.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp)
        )
        SelectRow(
            text = if (state.fileType == StatementFileType.Pdf) "PDF" else "Select file type",
            chosen = state.fileType != null
        ) { viewModel.showFileSheet(true) }
    }
}

@Composable
private fun AddressCard(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = OPay.colors
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Address shown on statement",
                style = OPayType.title,
                fontWeight = FontWeight.Bold,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
            OPayToggle(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun CenterDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = OPay.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RemoveWindowDim()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OverlayScrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.card)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(18.dp),
                content = content
            )
        }
    }
}

@Composable
private fun CloseRow(onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(modifier = Modifier.size(28.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            OPayIconView(icon = OPayIcon.Close, size = 18.dp, tint = OPay.colors.text)
        }
    }
}

@Composable
private fun Radio(selected: Boolean) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) c.green else c.muted, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(c.green))
    }
}

@Composable
private fun AccountDialog(number: String, selected: Boolean, onChoose: () -> Unit, onDismiss: () -> Unit) {
    val c = OPay.colors
    CenterDialog(onDismiss) {
        Text(
            text = "Select the account number",
            style = OPayType.title,
            fontWeight = FontWeight.Bold,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(OPayShapes.tile)
                .background(c.bg)
                .clickable(onClick = onChoose)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = number, style = OPayType.body, color = c.text, modifier = Modifier.weight(1f))
            Radio(selected)
        }
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.pill)
            .background(if (filled) c.green else c.green.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = OPayType.title,
            fontWeight = FontWeight.Bold,
            color = if (filled) c.onGreen else c.green
        )
    }
}

@Composable
private fun ConfirmDialog(account: String, maskedEmail: String, onRecheck: () -> Unit, onGenerate: () -> Unit) {
    val c = OPay.colors
    CenterDialog(onRecheck) {
        CloseRow(onRecheck)
        Text(
            text = "Kindly Confirm",
            style = OPayType.headline,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = buildAnnotatedString {
                append("Your account statement for $account will be sent to your email in ")
                withStyle(SpanStyle(color = c.green)) { append(".pdf") }
                append(" file")
            },
            style = OPayType.title,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        )
        Box(
            modifier = Modifier.fillMaxWidth().clip(OPayShapes.tile).background(c.bg).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = maskedEmail,
                style = OPayType.title,
                fontWeight = FontWeight.Bold,
                color = c.green,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "Make sure it is correct.",
            style = OPayType.title,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        )
        PillButton("Recheck Details", filled = false, onClick = onRecheck)
        Spacer(Modifier.height(10.dp))
        PillButton("Noted, Send Statement", filled = true, onClick = onGenerate)
    }
}

@Composable
private fun FileTypeSheet(
    visible: Boolean,
    current: StatementFileType?,
    onDismiss: () -> Unit,
    onConfirm: (StatementFileType) -> Unit
) {
    val c = OPay.colors
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        var pending by remember { mutableStateOf(current) }
        var message by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(message) {
            if (message != null) {
                delay(1500)
                message = null
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(28.dp))
            Text(
                text = "Select file type",
                style = OPayType.title,
                fontWeight = FontWeight.Bold,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.size(28.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                OPayIconView(icon = OPayIcon.Close, size = 18.dp, tint = c.text)
            }
        }
        Spacer(Modifier.height(14.dp))
        FileOption("Excel", "Your file will be downloaded as an .xls file", false) { message = EXCEL_MESSAGE }
        Spacer(Modifier.height(10.dp))
        FileOption("PDF", "Your file will be downloaded as a .pdf file", pending == StatementFileType.Pdf) {
            pending = StatementFileType.Pdf
        }
        message?.let {
            Text(
                text = it,
                style = OPayType.body,
                color = c.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
        OPayButton(
            text = "Confirm",
            onClick = { pending?.let(onConfirm) },
            modifier = Modifier.padding(top = 14.dp),
            enabled = pending != null
        )
    }
}

@Composable
private fun FileOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.card)
            .background(c.bg)
            .then(if (selected) Modifier.border(1.dp, c.green, OPayShapes.card) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(OPayShapes.tile).background(c.cardAlt),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Doc, size = 18.dp, tint = c.green)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = title, style = OPayType.body, fontWeight = FontWeight.Bold, color = c.text)
            Text(text = subtitle, style = OPayType.caption, color = c.muted)
        }
        Radio(selected)
    }
}

@Composable
private fun PinSheet(state: DownloadStatementUiState, viewModel: DownloadStatementViewModel, onForgotPin: () -> Unit) {
    val c = OPay.colors
    OPayBottomSheet(visible = state.pin, onDismiss = viewModel::cancelPin) {
        SecureWindow()
        SecureSheetWindow()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(28.dp))
            Text(
                text = "Enter Payment PIN",
                style = OPayType.title,
                fontWeight = FontWeight.Bold,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.size(28.dp).clickable(onClick = viewModel::cancelPin), contentAlignment = Alignment.Center) {
                OPayIconView(icon = OPayIcon.Close, size = 18.dp, tint = c.text)
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            PinDots(
                length = PAYMENT_PIN_LENGTH,
                filled = state.filled,
                hasError = state.pinMessage != null,
                shakeCount = state.shakeCount
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
            if (state.pinMessage != null) {
                Text(text = state.pinMessage, style = OPayType.caption, color = c.danger, textAlign = TextAlign.Center)
            }
        }
        Text(
            text = "Forgot Payment PIN?",
            style = OPayType.bodySmall,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.cancelPin()
                    onForgotPin()
                }
                .padding(vertical = 10.dp)
        )
        SecureKeypad(
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onDone = viewModel::cancelPin,
            modifier = Modifier.bleed(16.dp)
        )
    }
}

/** Blocks screenshots of the sheet's own window while the PIN keypad is shown. */
@Composable
private fun SecureSheetWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val inset = horizontal.roundToPx()
    val width = constraints.maxWidth + inset * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-inset, 0)
    }
}
