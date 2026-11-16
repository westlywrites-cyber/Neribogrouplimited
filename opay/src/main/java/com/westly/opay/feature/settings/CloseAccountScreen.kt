package com.westly.opay.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayDialog

private const val NOTICE_TAG = "notice"
private const val NOTICE_LINK = "‘OPay Account Cancellation Notice’"
private const val CONTACT_SUPPORT_TEXT = "Please contact support to close your account."

private val AlertRed = Color(0xFFE0245E)

private val cancellationConsequences = listOf(
    "Permanently Unable to Login or Use the Account",
    "ID, Account and Transaction Information will be cleared",
    "All Gained Benefits such as Cashback will be cleared"
)

@Composable
fun CloseAccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    var noticeOpen by rememberSaveable { mutableStateOf(false) }
    var contactOpen by rememberSaveable { mutableStateOf(false) }

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
                title = "Close Account",
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
                ConfirmStep(onOpenNotice = { noticeOpen = true })
                Spacer(Modifier.height(16.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OPayButton(
                    text = "Confirm and Continue",
                    onClick = { contactOpen = true }
                )
            }
        }
    }

    if (noticeOpen) {
        OPayDialog(
            title = "OPay Account Cancellation Notice",
            message = "This is a demo notice for the account cancellation page of this demo " +
                "application. No real account exists, so nothing can be cancelled.",
            primaryText = "OK",
            onPrimary = { noticeOpen = false },
            onDismiss = { noticeOpen = false }
        )
    }
    if (contactOpen) {
        OPayDialog(
            title = "Close Account",
            message = CONTACT_SUPPORT_TEXT,
            primaryText = "OK",
            onPrimary = { contactOpen = false },
            onDismiss = { contactOpen = false }
        )
    }
}

@Composable
private fun ConfirmStep(onOpenNotice: () -> Unit) {
    val c = OPay.colors
    val note = buildAnnotatedString {
        append("For more details on Account Cancellation, please refer to ")
        pushStringAnnotation(tag = NOTICE_TAG, annotation = NOTICE_TAG)
        withStyle(
            SpanStyle(color = c.green, textDecoration = TextDecoration.Underline)
        ) {
            append(NOTICE_LINK)
        }
        pop()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AlertRed),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "After Successful Account Cancellation:",
            style = OPayType.title.copy(fontWeight = FontWeight.Bold),
            color = c.text,
            textAlign = TextAlign.Center
        )
    }
    OPayCard {
        cancellationConsequences.forEach { line ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "•", style = OPayType.titleSmall, color = c.text)
                Text(
                    text = line,
                    style = OPayType.titleSmall.copy(lineHeight = 24.sp),
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    Spacer(Modifier.height(30.dp))
    ClickableText(
        text = note,
        style = OPayType.body.copy(color = c.muted),
        modifier = Modifier.padding(horizontal = 4.dp),
        onClick = { offset ->
            if (note.getStringAnnotations(NOTICE_TAG, offset, offset).isNotEmpty()) {
                onOpenNotice()
            }
        }
    )
}
