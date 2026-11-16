package com.westly.opay.feature.register

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog

private const val PEP_QUESTION = "Are you or any of your relatives a Politically Exposed Person?"
private const val PEP_EXPLANATION =
    "A Politically Exposed Person (PEP) is someone currently or previously holding a high-profile " +
        "political position or public office."

/**
 * Registration step 2 of 2, third page: the PEP question (reference B8) and its confirmation box (reference B9).
 * "No, I'm not" goes straight on. "Yes, I am" first asks for confirmation.
 */
@Composable
fun RegisterPepScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onNotPep: () -> Unit,
    onPep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberRegistrationViewModel()
    var confirming by rememberSaveable { mutableStateOf(false) }

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
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Help",
                onAction = onHelp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(c.greenSoft),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Doc, size = 32.dp, tint = c.green)
                }
                Text(
                    text = PEP_QUESTION,
                    style = OPayType.title,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    text = PEP_EXPLANATION,
                    style = OPayType.bodySmall,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OPayButton(
                    text = "Yes, I am",
                    onClick = { confirming = true },
                    modifier = Modifier.weight(1f),
                    style = ButtonStyle.Outline
                )
                OPayButton(
                    text = "No, I'm not",
                    onClick = {
                        viewModel.answerNotPep()
                        onNotPep()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (confirming) {
            OPayDialog(
                title = "Confirm PEP Status",
                message = PEP_QUESTION,
                primaryText = "YES",
                onPrimary = {
                    confirming = false
                    viewModel.answerPep()
                    onPep()
                },
                onDismiss = { confirming = false },
                secondaryText = "NO",
                onSecondary = { confirming = false }
            )
        }
    }
}
