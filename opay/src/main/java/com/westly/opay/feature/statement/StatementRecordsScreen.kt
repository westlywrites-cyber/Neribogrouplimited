package com.westly.opay.feature.statement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.westly.opay.core.ui.overlay.EmptyState
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import kotlinx.coroutines.launch

@Composable
fun StatementRecordsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    val context = LocalContext.current
    val engine = remember { StatementEngine.get(context.applicationContext as OPayApplication) }
    val records by engine.records.collectAsStateWithLifecycle()
    val resending by engine.resending.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()
    val scope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<StatementRecord?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val record = pendingSave
        pendingSave = null
        if (uri != null && record != null) {
            scope.launch {
                val saved = StatementFiles.saveTo(context, record, uri)
                toast.show(if (saved) "Statement saved" else "Could not save the statement")
            }
        }
    }
    LaunchedEffect(Unit) { engine.purge() }
    LaunchedEffect(engine) { engine.messages.collect { toast.show(it) } }

    val open: (StatementRecord) -> Unit = { record ->
        if (!StatementFiles.open(context, record)) toast.show("No app found to open PDF files")
    }
    val download: (StatementRecord) -> Unit = { record ->
        pendingSave = record
        saver.launch(record.fileName)
    }

    Box(modifier = modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            OPayTopBar(title = "Download Records", onBack = onBack, modifier = Modifier.padding(horizontal = 8.dp))
            if (records.isEmpty()) {
                EmptyState(message = "No download records")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        RecordCard(record, record.id in resending, open, engine::resend, download)
                    }
                    item(key = "footer") {
                        Text(
                            text = "Only the last 30 days of download records are displayed",
                            style = OPayType.caption,
                            color = c.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
        OPayToastHost(state = toast, bottomPadding = 24.dp)
    }
}

@Composable
private fun RecordCard(
    record: StatementRecord,
    resending: Boolean,
    onOpen: (StatementRecord) -> Unit,
    onResend: (String) -> Unit,
    onDownload: (StatementRecord) -> Unit
) {
    val c = OPay.colors
    val hasFile = record.status != StatementStatus.Processing && record.filePath.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.card)
            .background(c.card)
            .then(if (hasFile) Modifier.clickable { onOpen(record) } else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val badge = when (record.status) {
            StatementStatus.Processing -> "Processing" to ProcessingOrange
            StatementStatus.Sent -> "Sent" to c.green
            StatementStatus.Failed -> "Failed" to c.danger
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Status", style = OPayType.bodySmall, color = c.muted, modifier = Modifier.weight(1f))
            Text(
                text = badge.first,
                style = OPayType.caption,
                color = badge.second,
                modifier = Modifier
                    .background(badge.second.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Line("Own Account", StatementFormat.maskedAccount(record.accountNumber))
        Line("Transactions with", "All")
        Line("Period", StatementFormat.periodText(record.period))
        if (record.sendTo.isNotEmpty()) Line("Send to", record.sendTo)
        Line("Requested on", StatementFormat.requestedOn(record.requestedAtMillis))
        Line("Format", record.format)
        if (hasFile) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                SmallButton(
                    text = if (resending) "Sending..." else "Resend Email",
                    filled = false,
                    enabled = !resending
                ) { onResend(record.id) }
                SmallButton("Download", filled = true) { onDownload(record) }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val c = OPay.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = OPayType.bodySmall, color = c.muted, modifier = Modifier.weight(1f))
        Text(text = value, style = OPayType.bodySmall, color = c.text)
    }
}

@Composable
private fun SmallButton(text: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .clip(OPayShapes.pill)
            .background(if (filled) c.green else c.green.copy(alpha = 0.16f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = OPayType.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (filled) c.onGreen else c.green
        )
    }
}
