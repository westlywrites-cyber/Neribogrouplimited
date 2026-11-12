package com.westly.neribogroup.feature.group

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.ChannelRules
import com.westly.neribogroup.data.CompanyAssets
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.ToolItem
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.NumberField
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatDate
import java.io.File
import java.util.UUID

// ---------------------------------------------------------------------------------------------
// Equipment: everything the company owns, with exact details, who uses it, what for, and photos
// ---------------------------------------------------------------------------------------------

private val TYPE_SUGGESTIONS = listOf(
    "Laptop", "Desktop", "Monitor", "Phone", "Tablet", "Router", "Network switch", "Server", "Printer",
    "Keyboard and mouse", "USB-C hub", "Storage drive", "Camera", "Other"
)

private val DEPARTMENT_SUGGESTIONS = listOf(
    "Engineering", "Design", "Marketing", "Customer Support", "Finance and Administration", "Management", "Servers"
)

/** Every word typed must be found somewhere in the item's details. */
private fun matches(tool: ToolItem, query: String): Boolean {
    val words = query.trim().lowercase().split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val text = listOf(
        tool.name, tool.category, tool.brand, tool.model, tool.specs,
        tool.department, tool.assignedTo, tool.usedFor, tool.note
    ).joinToString(" ").lowercase()
    return words.all { text.contains(it) }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(text = value, style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun SuggestionRow(options: List<String>, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (option in options) {
            AssistChip(onClick = { onPick(option) }, label = { Text(option) })
        }
    }
}

@Composable
fun EquipmentScreen(
    ledger: Ledger,
    photoFile: (id: String, index: Int) -> File,
    photoTick: Int,
    onBack: () -> Unit,
    onSave: (CompanyAssets) -> Unit,
    onPickPhoto: (id: String, index: Int, uri: Uri, done: (String) -> Unit) -> Unit,
    onRemovePhoto: (id: String, index: Int, count: Int) -> Unit,
    onDeletePhotos: (id: String) -> Unit
) {
    val context = LocalContext.current
    val assets = ledger.assets
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ToolItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val openTool = openId?.let { id -> assets.tools.firstOrNull { it.id == id } }

    val pickPhoto = rememberSinglePhotoPicker { uri ->
        val tool = openTool
        if (tool != null && tool.photoCount < ToolItem.MAX_PHOTOS) {
            val index = tool.photoCount
            onPickPhoto(tool.id, index, uri) { message ->
                if (message.isEmpty()) {
                    onSave(
                        assets.copy(
                            tools = assets.tools.map {
                                if (it.id == tool.id) it.copy(photos = index + 1, hasPhoto = true) else it
                            }
                        )
                    )
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (openTool != null) {
        PageFrame(openTool.name, onBack = { openId = null }) {
            item {
                FlatCard {
                    Text(
                        "Photos (" + openTool.photoCount + "/" + ToolItem.MAX_PHOTOS + ")",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (openTool.photoCount > 0) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (index in 0 until openTool.photoCount) {
                                Column {
                                    AssetPhoto(photoFile(openTool.id, index), photoTick, Modifier.size(110.dp))
                                    TextButton(onClick = {
                                        val left = openTool.photoCount - 1
                                        onRemovePhoto(openTool.id, index, openTool.photoCount)
                                        onSave(
                                            assets.copy(
                                                tools = assets.tools.map {
                                                    if (it.id == openTool.id) it.copy(photos = left, hasPhoto = left > 0) else it
                                                }
                                            )
                                        )
                                    }) { Text("Remove") }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            "No photos yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (openTool.photoCount < ToolItem.MAX_PHOTOS) {
                        OutlinedButton(onClick = pickPhoto) { Text("Add photo") }
                    } else {
                        Text(
                            "This item has all 5 photos. Remove one to add another.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                FlatCard {
                    DetailRow("Type", openTool.category)
                    DetailRow("Brand", openTool.brand)
                    DetailRow("Model", openTool.model)
                    DetailRow("Specifications", openTool.specs)
                    DetailRow("Quantity", openTool.quantity.toString())
                    DetailRow("Department", openTool.department)
                    DetailRow("Assigned to", openTool.assignedTo)
                    DetailRow("Used for", openTool.usedFor)
                    DetailRow("Note", openTool.note)
                    DetailRow("Added", formatDate(openTool.addedMillis))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { editing = openTool }) { Text("Edit") }
                    TintedButton(text = "Delete", destructive = true, onClick = { confirmDelete = true })
                }
            }
        }
    } else {
        val shown = assets.tools.filter { matches(it, query) }.sortedBy { it.name.lowercase() }
        PageFrame("Equipment", onBack) {
            item {
                Text(
                    "Everything the company owns: laptops, computers, phones, network gear and other gadgets. " +
                        "Tap one to see everything about it. Photos stay on this phone for now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                LabeledField(
                    label = "Search by name, type, brand, specs, department, person or use",
                    value = query,
                    onValueChange = { query = it.take(60) }
                )
            }
            item {
                TintedButton(
                    text = "Add equipment",
                    onClick = { adding = true },
                    enabled = assets.tools.size < ChannelRules.MAX_TOOLS,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = if (query.isBlank()) {
                        assets.tools.size.toString() + (if (assets.tools.size == 1) " item" else " items") +
                            " · " + assets.tools.sumOf { it.quantity } + " units"
                    } else {
                        shown.size.toString() + " of " + assets.tools.size + " match"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (assets.tools.isEmpty()) {
                item {
                    Text(
                        "No equipment yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (shown.isEmpty()) {
                item {
                    Text(
                        "Nothing matches your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(shown, key = { it.id }) { tool ->
                FlatCard(modifier = Modifier.clickable { openId = tool.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tool.photoCount > 0) {
                            AssetPhoto(photoFile(tool.id, 0), photoTick, Modifier.size(72.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tool.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(tool.category, "Quantity " + tool.quantity).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val who = listOf(tool.department, tool.assignedTo).filter { it.isNotBlank() }.joinToString(" · ")
                            if (who.isNotEmpty()) {
                                Text(
                                    who,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (tool.specs.isNotBlank()) {
                                Text(
                                    tool.specs,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val existing = editing
        EquipmentDialog(
            existing = existing,
            staffNames = ledger.staff.map { it.name }.filter { it.isNotBlank() },
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { tool ->
                val list = if (existing == null) assets.tools + tool else assets.tools.map { if (it.id == tool.id) tool else it }
                onSave(assets.copy(tools = list))
                if (existing == null) openId = tool.id
                adding = false
                editing = null
            }
        )
    }
    if (confirmDelete && openTool != null) {
        ConfirmDialog(
            heading = "Delete " + openTool.name + "?",
            message = "This removes the item and its photos from this phone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDeletePhotos(openTool.id)
                onSave(assets.copy(tools = assets.tools.filter { it.id != openTool.id }))
                openId = null
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun EquipmentDialog(
    existing: ToolItem?,
    staffNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (ToolItem) -> Unit
) {
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var brand by remember { mutableStateOf(existing?.brand ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var specs by remember { mutableStateOf(existing?.specs ?: "") }
    var quantity by remember { mutableStateOf((existing?.quantity ?: 1).toLong()) }
    var department by remember { mutableStateOf(existing?.department ?: "") }
    var assignedTo by remember { mutableStateOf(existing?.assignedTo ?: "") }
    var usedFor by remember { mutableStateOf(existing?.usedFor ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    NeriboDialog(
        onDismiss = onDismiss,
        title = if (existing == null) "Add equipment" else "Edit equipment",
        subtitle = "Type the exact details. You can add up to 5 photos after saving."
    ) {
        LabeledField(label = "Name", value = name, onValueChange = { name = it.take(80) })
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(label = "Type", value = category, onValueChange = { category = it.take(40) })
        Spacer(modifier = Modifier.height(6.dp))
        SuggestionRow(TYPE_SUGGESTIONS) { category = it }
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(label = "Brand", value = brand, onValueChange = { brand = it.take(40) })
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(label = "Model", value = model, onValueChange = { model = it.take(60) })
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(
            label = "Specifications (processor, memory, storage, size...)",
            value = specs,
            onValueChange = { specs = it.take(300) },
            singleLine = false,
            minLines = 2
        )
        Spacer(modifier = Modifier.height(12.dp))
        NumberField(label = "Quantity", value = quantity, onChange = { quantity = it }, maxDigits = 5)
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(label = "Department", value = department, onValueChange = { department = it.take(60) })
        Spacer(modifier = Modifier.height(6.dp))
        SuggestionRow(DEPARTMENT_SUGGESTIONS) { department = it }
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(label = "Assigned to", value = assignedTo, onValueChange = { assignedTo = it.take(60) })
        if (staffNames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            SuggestionRow(staffNames) { assignedTo = it }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(
            label = "What it is used for",
            value = usedFor,
            onValueChange = { usedFor = it.take(300) },
            singleLine = false,
            minLines = 2
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledField(
            label = "Note",
            value = note,
            onValueChange = { note = it.take(200) },
            singleLine = false,
            minLines = 2
        )
        DialogActions {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Save",
                enabled = name.trim().length >= 2,
                onClick = {
                    onSave(
                        ToolItem(
                            id = id,
                            name = name.trim(),
                            quantity = maxOf(1L, quantity).toInt(),
                            note = note.trim(),
                            addedMillis = existing?.addedMillis ?: System.currentTimeMillis(),
                            hasPhoto = existing?.hasPhoto ?: false,
                            category = category.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            specs = specs.trim(),
                            department = department.trim(),
                            assignedTo = assignedTo.trim(),
                            usedFor = usedFor.trim(),
                            photos = existing?.photos ?: 0
                        )
                    )
                }
            )
        }
    }
}
