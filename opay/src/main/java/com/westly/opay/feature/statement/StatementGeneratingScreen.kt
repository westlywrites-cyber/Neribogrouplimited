package com.westly.opay.feature.statement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView

internal val ProcessingOrange = Color(0xFFFF9800)

private enum class StepState { Done, Active, Pending }

@Composable
fun StatementGeneratingScreen(
    onBack: () -> Unit,
    onOkay: () -> Unit,
    onRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val engine = remember { StatementEngine.get(context.applicationContext as OPayApplication) }
    val records by engine.records.collectAsStateWithLifecycle()
    val currentId by engine.currentId.collectAsStateWithLifecycle()
    val failures by engine.failures.collectAsStateWithLifecycle()
    val record = records.firstOrNull { it.id == currentId }
    val status = record?.status ?: StatementStatus.Processing

    if (status == StatementStatus.Sent) {
        SentPage(onBack = onBack, onOkay = onOkay, modifier = modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            OPayTopBar(title = "", onBack = onBack, modifier = Modifier.padding(horizontal = 8.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val failed = status == StatementStatus.Failed
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (failed) c.danger else ProcessingOrange),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(
                        icon = if (failed) OPayIcon.Close else OPayIcon.Clock,
                        size = 22.dp,
                        tint = Color.White
                    )
                }
                Text(
                    text = "Generating your file",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    modifier = Modifier.padding(top = 14.dp)
                )
                if (failed) {
                    Text(
                        text = failures[currentId] ?: StatementErrors.GENERATE_FAILED,
                        style = OPayType.body,
                        color = c.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                } else {
                    Text(
                        text = "We'll notify you when it's ready. You can leave this page and check its status anytime under",
                        style = OPayType.title,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = "Download Records>",
                        style = OPayType.title,
                        color = c.green,
                        modifier = Modifier.clickable(onClick = onRecords).padding(vertical = 4.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth()
                        .clip(OPayShapes.card)
                        .background(c.card)
                        .padding(14.dp)
                ) {
                    Step(StepState.Done, "Request Received", record?.let { StatementFormat.requestReceived(it.requestedAtMillis) }, false)
                    Step(
                        state = StepState.Active,
                        title = "Generating your PDF statement",
                        sub = "ready in a few seconds",
                        last = false
                    )
                    Step(
                        state = StepState.Pending,
                        title = "Sent to your email",
                        sub = record?.sendToSteps?.takeIf { it.isNotEmpty() },
                        last = true
                    )
                }
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()) {
                OPayButton(text = "Okay", onClick = onOkay)
            }
        }
    }
}

/** The page that replaces the Generating page once the email is sent. */
@Composable
private fun SentPage(onBack: () -> Unit, onOkay: () -> Unit, modifier: Modifier) {
    val c = OPay.colors
    Box(modifier = modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            OPayTopBar(title = "", onBack = onBack, modifier = Modifier.padding(horizontal = 8.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.padding(top = 16.dp).size(44.dp).clip(CircleShape).background(c.green),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Check, size = 24.dp, tint = Color.White)
                }
                Text(
                    text = "Successful",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    text = "Your statement request has been sent to your email",
                    style = OPayType.title,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()) {
                OPayButton(text = "Okay", onClick = onOkay)
            }
        }
    }
}

@Composable
private fun Step(state: StepState, title: String, sub: String?, last: Boolean) {
    val c = OPay.colors
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(modifier = Modifier.width(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (state == StepState.Pending) c.divider else c.green)
            )
            if (!last) {
                Box(modifier = Modifier.width(1.5.dp).weight(1f).background(c.divider))
            }
        }
        Column(
            modifier = Modifier.padding(start = 10.dp, bottom = if (last) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = OPayType.body,
                fontWeight = if (state == StepState.Active) FontWeight.Bold else FontWeight.Normal,
                color = if (state == StepState.Pending) c.muted else c.text
            )
            if (sub != null) Text(text = sub, style = OPayType.bodySmall, color = c.muted)
        }
    }
}
