package com.westly.neribogroup.feature.group

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Channel
import com.westly.neribogroup.data.CompanyAssets
import com.westly.neribogroup.data.CompanyDoc
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.StorageRules
import com.westly.neribogroup.data.ToolItem
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.LocalBiometricGate
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.NumberField
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.staff.decodePhoto
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/** 1,536 KB -> "1.5 MB". The unit changes by itself: KB, MB, GB, TB or PB. */
internal fun storageText(kb: Long): String = StorageRules.text(kb)

private fun mbText(mb: Long): String = storageText(mb * 1024L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageFrame(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Apple Store Balance and Paystack Balance
// ---------------------------------------------------------------------------------------------

/**
 * The Apple Store Balance (channel APPLE) or the Paystack Balance (channel WEBSITE). Subscription and renewal money
 * waits here after that channel's own fee, per app and in total, until it is withdrawn into profit.
 */
@Composable
fun ChannelBalanceScreen(
    ledger: Ledger,
    channel: Channel,
    onBack: () -> Unit,
    onWithdrawApp: (String) -> Unit,
    onWithdrawAll: () -> Unit
) {
    fun balanceOf(app: AppData): Long = if (channel == Channel.APPLE) app.appleBalance else app.paystackBalance
    val apps = ledger.apps.filter { it.googlePlayEnabled && (if (channel == Channel.APPLE) it.appleOn else it.websiteOn) }
    val total = ledger.apps.filter { it.googlePlayEnabled }.sumOf { balanceOf(it) }
    val gate = LocalBiometricGate.current
    var confirmAll by remember { mutableStateOf(false) }
    val feeText = if (channel == Channel.APPLE) {
        "Apple keeps 15% of every subscription and renewal."
    } else {
        "Paystack keeps 1.5% + ₦100 of every payment (no ₦100 on payments of ₦2,500 or less), at most ₦2,000 each."
    }
    PageFrame(channel.balanceName, onBack) {
        item {
            FlatCard {
                Text("Waiting to withdraw", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatNaira(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(feeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                TintedButton(text = "Withdraw all", onClick = { confirmAll = true }, enabled = total > 0L, modifier = Modifier.fillMaxWidth())
            }
        }
        item { SectionTitle("Apps") }
        if (apps.isEmpty()) {
            item {
                Text(
                    text = "No app is on the " + channel.label + " yet. Turn it on in an app's Edit page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(apps, key = { it.id }) { app ->
            val lines = (if (channel == Channel.APPLE) app.appleTx else app.paystackTx).takeLast(5).reversed()
            FlatCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            formatCount(app.usersOfChannel(channel)) + " users from the " + channel.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(formatNaira(balanceOf(app)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                for (line in lines) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.label, style = MaterialTheme.typography.bodySmall)
                            Text(
                                formatDate(line.dateMillis) + " · kept " + formatNaira(line.fee) + " of " + formatNaira(line.gross),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(formatNaira(line.net), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        gate.verify(
                            title = "Withdraw " + channel.label + " balance?",
                            subtitle = formatNaira(balanceOf(app)) + " moves into " + app.name + "'s profit.",
                            logText = channel.balanceName + " withdrawn for " + app.name + ": " + formatNaira(balanceOf(app))
                        ) { onWithdrawApp(app.id) }
                    },
                    enabled = balanceOf(app) > 0L
                ) { Text("Withdraw") }
            }
        }
    }
    if (confirmAll) {
        ConfirmDialog(
            heading = "Withdraw everything?",
            message = formatNaira(total) + " moves into profit.",
            confirmLabel = "Withdraw",
            onConfirm = {
                confirmAll = false
                gate.verify(
                    title = "Withdraw all " + channel.label + " balances?",
                    subtitle = formatNaira(total) + " moves into the apps' profit.",
                    logText = channel.balanceName + " withdrawn for all apps: " + formatNaira(total)
                ) { onWithdrawAll() }
            },
            onDismiss = { confirmAll = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Photos
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AssetPhoto(file: File, tick: Int, modifier: Modifier = Modifier) {
    val bitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, file.path, tick) {
        value = withContext(Dispatchers.IO) { if (file.exists()) decodePhoto(file, 640)?.asImageBitmap() else null }
    }.value
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Company documents
// ---------------------------------------------------------------------------------------------

@Composable
fun DocumentsScreen(
    ledger: Ledger,
    photoFile: (String) -> File,
    photoTick: Int,
    onBack: () -> Unit,
    onSave: (CompanyAssets) -> Unit,
    onPickPhoto: (id: String, uri: Uri, done: (String) -> Unit) -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    val assets = ledger.assets
    val gate = LocalBiometricGate.current
    var editing by remember { mutableStateOf<CompanyDoc?>(null) }
    var adding by remember { mutableStateOf(false) }
    PageFrame("Company documents", onBack) {
        item {
            Text(
                "Registration papers and other company documents. Add a photo of each paper. Photos stay on this phone for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { TintedButton(text = "Add document", onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) }
        if (assets.documents.isEmpty()) {
            item { Text("No documents yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(assets.documents.sortedByDescending { it.addedMillis }, key = { it.id }) { doc ->
            FlatCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (doc.hasPhoto) {
                        AssetPhoto(photoFile(doc.id), photoTick, Modifier.size(72.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (doc.kind.isNotBlank()) Text(doc.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (doc.note.isNotBlank()) Text(doc.note, style = MaterialTheme.typography.bodySmall)
                        Text("Added " + formatDate(doc.addedMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { editing = doc }) { Text("Edit") }
            }
        }
    }
    if (adding || editing != null) {
        val existing = editing
        DocumentDialog(
            existing = existing,
            photoFile = photoFile,
            photoTick = photoTick,
            onPickPhoto = onPickPhoto,
            onDeletePhoto = onDeletePhoto,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { doc ->
                val list = if (existing == null) assets.documents + doc else assets.documents.map { if (it.id == doc.id) doc else it }
                onSave(assets.copy(documents = list.takeLast(com.westly.neribogroup.data.ChannelRules.MAX_DOCUMENTS)))
                adding = false
                editing = null
            },
            onDelete = existing?.let { doc ->
                {
                    gate.verify(
                        title = "Delete " + doc.title + "?",
                        subtitle = "This document and its photo cannot be brought back.",
                        logText = "Document deleted: " + doc.title
                    ) {
                        onDeletePhoto(doc.id)
                        onSave(assets.copy(documents = assets.documents.filter { it.id != doc.id }))
                        adding = false
                        editing = null
                    }
                }
            }
        )
    }
}

@Composable
private fun DocumentDialog(
    existing: CompanyDoc?,
    photoFile: (String) -> File,
    photoTick: Int,
    onPickPhoto: (String, Uri, (String) -> Unit) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (CompanyDoc) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var hasPhoto by remember { mutableStateOf(existing?.hasPhoto ?: false) }
    var tick by remember { mutableStateOf(photoTick) }
    val pick = rememberSinglePhotoPicker { uri ->
        onPickPhoto(id, uri) { message ->
            if (message.isEmpty()) {
                hasPhoto = true
                tick += 1
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val dismiss = {
        if (existing == null && hasPhoto) onDeletePhoto(id)
        onDismiss()
    }
    NeriboDialog(onDismiss = dismiss, title = if (existing == null) "Add document" else "Edit document") {
        LabeledField(label = "Title", value = title, onValueChange = { title = it.take(80) })
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(label = "Type (for example Registration, Tax, Licence)", value = kind, onValueChange = { kind = it.take(40) })
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(label = "Number or note", value = note, onValueChange = { note = it.take(200) }, singleLine = false, minLines = 2)
        Spacer(modifier = Modifier.height(12.dp))
        if (hasPhoto) {
            AssetPhoto(photoFile(id), tick, Modifier.fillMaxWidth().height(180.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(onClick = pick) { Text(if (hasPhoto) "Change photo" else "Add photo") }
        DialogActions {
            if (onDelete != null) TintedButton(text = "Delete", onClick = onDelete, destructive = true)
            OutlinedButton(onClick = dismiss) { Text("Cancel") }
            TintedButton(
                text = "Save",
                enabled = title.trim().length >= 2,
                onClick = {
                    onSave(
                        CompanyDoc(
                            id = id, title = title.trim(), kind = kind.trim(), note = note.trim(),
                            addedMillis = existing?.addedMillis ?: System.currentTimeMillis(), hasPhoto = hasPhoto
                        )
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tools and appliances
// ---------------------------------------------------------------------------------------------

@Composable
fun ToolsScreen(
    ledger: Ledger,
    photoFile: (String) -> File,
    photoTick: Int,
    onBack: () -> Unit,
    onSave: (CompanyAssets) -> Unit,
    onPickPhoto: (id: String, uri: Uri, done: (String) -> Unit) -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    val assets = ledger.assets
    var editing by remember { mutableStateOf<ToolItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    PageFrame("Tools and appliances", onBack) {
        item {
            Text(
                "Everything the company uses. Add a photo of each one. Photos stay on this phone for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { TintedButton(text = "Add item", onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) }
        if (assets.tools.isEmpty()) {
            item { Text("No items yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(assets.tools.sortedByDescending { it.addedMillis }, key = { it.id }) { tool ->
            FlatCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tool.hasPhoto) {
                        AssetPhoto(photoFile(tool.id), photoTick, Modifier.size(72.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tool.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Quantity: " + tool.quantity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (tool.note.isNotBlank()) Text(tool.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { editing = tool }) { Text("Edit") }
            }
        }
    }
    if (adding || editing != null) {
        val existing = editing
        ToolDialog(
            existing = existing,
            photoFile = photoFile,
            photoTick = photoTick,
            onPickPhoto = onPickPhoto,
            onDeletePhoto = onDeletePhoto,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { tool ->
                val list = if (existing == null) assets.tools + tool else assets.tools.map { if (it.id == tool.id) tool else it }
                onSave(assets.copy(tools = list.takeLast(com.westly.neribogroup.data.ChannelRules.MAX_TOOLS)))
                adding = false
                editing = null
            },
            onDelete = existing?.let { tool ->
                {
                    onDeletePhoto(tool.id)
                    onSave(assets.copy(tools = assets.tools.filter { it.id != tool.id }))
                    adding = false
                    editing = null
                }
            }
        )
    }
}

@Composable
private fun ToolDialog(
    existing: ToolItem?,
    photoFile: (String) -> File,
    photoTick: Int,
    onPickPhoto: (String, Uri, (String) -> Unit) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ToolItem) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var quantity by remember { mutableStateOf((existing?.quantity ?: 1).toLong()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var hasPhoto by remember { mutableStateOf(existing?.hasPhoto ?: false) }
    var tick by remember { mutableStateOf(photoTick) }
    val pick = rememberSinglePhotoPicker { uri ->
        onPickPhoto(id, uri) { message ->
            if (message.isEmpty()) {
                hasPhoto = true
                tick += 1
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val dismiss = {
        if (existing == null && hasPhoto) onDeletePhoto(id)
        onDismiss()
    }
    NeriboDialog(onDismiss = dismiss, title = if (existing == null) "Add item" else "Edit item") {
        LabeledField(label = "Name", value = name, onValueChange = { name = it.take(80) })
        Spacer(modifier = Modifier.height(12.dp))
        NumberField(label = "Quantity", value = quantity, onChange = { quantity = it }, maxDigits = 5)
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(label = "Note", value = note, onValueChange = { note = it.take(200) }, singleLine = false, minLines = 2)
        Spacer(modifier = Modifier.height(12.dp))
        if (hasPhoto) {
            AssetPhoto(photoFile(id), tick, Modifier.fillMaxWidth().height(180.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(onClick = pick) { Text(if (hasPhoto) "Change photo" else "Add photo") }
        DialogActions {
            if (onDelete != null) TintedButton(text = "Delete", onClick = onDelete, destructive = true)
            OutlinedButton(onClick = dismiss) { Text("Cancel") }
            TintedButton(
                text = "Save",
                enabled = name.trim().length >= 2,
                onClick = {
                    onSave(
                        ToolItem(
                            id = id, name = name.trim(), quantity = maxOf(1L, quantity).toInt(), note = note.trim(),
                            addedMillis = existing?.addedMillis ?: System.currentTimeMillis(), hasPhoto = hasPhoto
                        )
                    )
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Neribo Deploy sites
// ---------------------------------------------------------------------------------------------

/**
 * The sites deployed on Neribo Deploy style apps (apps with Deploy tracking switched on). The total is never
 * typed here: it comes from the users. Users you add yourself have the sites you typed (at least 1) and are always
 * Active; the dice only give sites to the users the dice add.
 */
@Composable
fun DeploySitesScreen(ledger: Ledger, onBack: () -> Unit) {
    val apps = ledger.apps.filter { it.googlePlayEnabled && it.deployTracking }
    val users = apps.sumOf { it.totalUsers }
    val sites = apps.sumOf { it.totalSites }
    PageFrame("Neribo Deploy sites", onBack) {
        item {
            FlatCard {
                Text("Sites deployed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCount(sites), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    formatCount(users) + " users",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (apps.isEmpty()) {
            item {
                Text(
                    "No app tracks sites yet. Open the Neribo Deploy app, tap Edit, and switch on \"Neribo Deploy sites\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(apps, key = { it.id }) { app ->
            val mine = app.users.filter { !it.simulated }
            val diceUsers = app.users.filter { it.simulated }
            FlatCard {
                Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(formatCount(app.totalSites) + " sites for " + formatCount(app.totalUsers) + " users")
                Text(
                    "Users you added: " + mine.size + " with " + mine.sumOf { maxOf(1, it.sites) } + " sites",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Users the dice added: " + formatCount(diceUsers.size.toLong() + app.audienceFree) + " with " +
                        formatCount(diceUsers.sumOf { maxOf(1, it.sites).toLong() } + app.audienceFree) + " sites",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Storage
// ---------------------------------------------------------------------------------------------

/** The total storage the company has (typed by the owner) and what every app uses (it grows by itself). */
@Composable
fun StorageScreen(ledger: Ledger, onBack: () -> Unit, onSave: (CompanyAssets) -> Unit) {
    val assets = ledger.assets
    var editTotal by remember { mutableStateOf(false) }
    val apps = ledger.apps.filter { it.googlePlayEnabled && (it.storageCapacityMb > 0L || it.storageUsedKb > 0L) }
    val usedKb = apps.sumOf { it.storageUsedKb }
    val totalKb = assets.storageTotalMb * 1024L
    PageFrame("Storage", onBack) {
        item {
            FlatCard {
                Text("Company storage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(mbText(assets.storageTotalMb), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Used by all apps: " + storageText(usedKb))
                Text(
                    if (totalKb > 0L) "Free: " + storageText(maxOf(0L, totalKb - usedKb)) else "Type the total storage you have.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { editTotal = true }) { Text("Set total storage") }
            }
        }
        item { SectionTitle("Used by each app") }
        if (apps.isEmpty()) {
            item {
                Text(
                    "No app offers storage yet. In an app's Edit page, type what Free, Pro and Premium users get.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(apps.sortedByDescending { it.storageUsedKb }, key = { it.id }) { app ->
            FlatCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(storageText(app.storageUsedKb), fontWeight = FontWeight.Bold)
                }
                Text(
                    "Of " + mbText(app.storageCapacityMb) + " its users and sites could fill",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (app.deployTracking) {
                    Text(
                        "Websites: " + formatCount(app.totalSites) + " sites" +
                            (if (app.storageSiteMb > 0L) " · " + mbText(app.storageSiteMb) + " offered per site" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (editTotal) {
        var value by remember { mutableStateOf(assets.storageTotalMb) }
        NeriboDialog(onDismiss = { editTotal = false }, title = "Total storage", subtitle = "In MB. 1 GB is 1024 MB.") {
            NumberField(label = "Total storage (MB)", value = value, onChange = { value = it }, maxDigits = 10)
            DialogActions {
                OutlinedButton(onClick = { editTotal = false }) { Text("Cancel") }
                TintedButton(text = "Save", onClick = {
                    onSave(assets.copy(storageTotalMb = value))
                    editTotal = false
                })
            }
        }
    }
}
