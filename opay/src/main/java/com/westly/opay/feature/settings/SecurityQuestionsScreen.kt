package com.westly.opay.feature.settings

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.SettingsKeys
import com.westly.opay.data.SettingsMerge
import com.westly.opay.feature.register.BusyOverlay

private val TOAST_ABOVE_KEYPAD = 340.dp
private val TOAST_ABOVE_BUTTON = 120.dp

/**
 * Security Questions. The first page shows whether questions are set. Setting them for the first time opens the form;
 * resetting them asks for the payment PIN first. Answers are typed here, sent once and never shown again.
 */
@Composable
fun SecurityQuestionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val application = LocalContext.current.applicationContext as Application
    val repository = remember(application) { (application as OPayApplication).repository }
    val factory = remember(application) { SecurityQuestionsViewModel.factory(application) }
    val viewModel = viewModel<SecurityQuestionsViewModel>(factory = factory)
    val state by viewModel.state.collectAsState()
    val settings by repository.appSettings.collectAsStateWithLifecycle()
    val isSet = SettingsMerge.flag(settings, SettingsKeys.SECURITY_QUESTIONS_SET)
    val toast = rememberOPayToast()

    if (state.step != SecurityQuestionsStep.Overview) SecureWindow()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SecurityQuestionsEvent.ShowToast -> toast.show(event.message)
            }
        }
    }

    fun requestBack() {
        if (!viewModel.stepBack()) onBack()
    }

    BackHandler { requestBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            OPayTopBar(
                title = "",
                onBack = { requestBack() },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            when (state.step) {
                SecurityQuestionsStep.Overview -> OverviewPage(
                    isSet = isSet,
                    onAction = { viewModel.onStart(isSet) }
                )
                SecurityQuestionsStep.Pin -> PinPage(state = state, viewModel = viewModel)
                SecurityQuestionsStep.Form -> FormPage(state = state, viewModel = viewModel)
            }
        }
        if (state.busy) BusyOverlay()
        OPayToastHost(
            state = toast,
            bottomPadding = if (state.step == SecurityQuestionsStep.Pin) TOAST_ABOVE_KEYPAD else TOAST_ABOVE_BUTTON
        )
    }
}

@Composable
private fun ColumnScope.OverviewPage(
    isSet: Boolean,
    onAction: () -> Unit
) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(88.dp)) {
                    drawShield(color = c.green)
                }
                OPayIconView(
                    icon = OPayIcon.Help,
                    size = 38.dp,
                    tint = c.onGreen
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (isSet) "Security Questions have Configured" else "Security Questions are not set",
                style = OPayType.title.copy(fontWeight = FontWeight.Bold),
                color = c.text,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isSet) "Set" else "Not set",
                style = OPayType.caption,
                color = if (isSet) c.green else c.muted,
                textAlign = TextAlign.Center
            )
        }
        OPayCard {
            Text(
                text = "Forgot Security Questions?",
                style = OPayType.body.copy(fontWeight = FontWeight.Bold),
                color = c.text
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "If forgotten, you may reset them after identity verification.",
                style = OPayType.caption,
                color = c.muted
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.warn.copy(alpha = 0.18f), OPayShapes.chip)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Important:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.text
                )
                Text(
                    text = "Old questions will be deactivated immediately upon reset. " +
                        "New questions & answers will authenticate critical operations " +
                        "(e.g. transactions) to safeguard your account.",
                    style = OPayType.caption,
                    color = c.text
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OPayButton(
            text = if (isSet) "Reset Security Questions" else "Set Security Questions",
            onClick = onAction
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Licensed by the CBN and insured by the NDIC",
            style = OPayType.captionSmall,
            color = c.muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ColumnScope.PinPage(
    state: SecurityQuestionsUiState,
    viewModel: SecurityQuestionsViewModel
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        PaymentPinPage(
            title = "Enter payment PIN",
            subtitle = "Enter your 4-digit payment PIN to reset your security questions",
            filled = state.filled,
            message = state.message,
            shakeCount = state.shakeCount,
            showHint = false
        )
    }
    SecureKeypad(
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        modifier = Modifier.navigationBarsPadding()
    )
}

@Composable
private fun ColumnScope.FormPage(
    state: SecurityQuestionsUiState,
    viewModel: SecurityQuestionsViewModel
) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(text = "Set Security Questions", style = OPayType.headline, color = c.text)
        Text(
            text = "Choose ${SecurityQuestionOptions.COUNT} questions. Your answers are not shown again.",
            style = OPayType.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        repeat(SecurityQuestionOptions.COUNT) { slot ->
            QuestionRow(
                number = slot + 1,
                question = state.picks[slot]?.let { SecurityQuestionOptions.all[it] },
                onClick = { viewModel.openPicker(slot) }
            )
            OPayUnderlineField(
                value = state.answers[slot],
                onValueChange = { viewModel.onAnswerChanged(slot, it) },
                placeholder = "Your answer",
                maxLength = SecurityQuestionOptions.MAX_ANSWER_LENGTH,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        OPayButton(
            text = "Save",
            onClick = viewModel::onSave,
            enabled = state.canSave && !state.busy
        )
    }
    val slot = state.pickerSlot
    OPayBottomSheet(
        visible = slot != null,
        onDismiss = viewModel::closePicker
    ) {
        Text(
            text = "Select a question",
            style = OPayType.title,
            color = c.text,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (slot != null) {
            state.availableFor(slot).forEach { index ->
                Text(
                    text = SecurityQuestionOptions.all[index],
                    style = OPayType.body,
                    color = c.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onPick(index) }
                        .padding(vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun QuestionRow(
    number: Int,
    question: String?,
    onClick: () -> Unit
) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Question $number", style = OPayType.caption, color = c.muted)
            Text(
                text = question ?: "Select a question",
                style = OPayType.body,
                color = if (question == null) c.muted else c.text,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
    }
}
