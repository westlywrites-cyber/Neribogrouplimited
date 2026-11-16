package com.westly.opay.feature.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog

private const val SHEET_NONE = 0
private const val SHEET_STATE = 1
private const val SHEET_LGA = 2
private const val SHEET_AREA = 3
private const val SHEET_PERMANENT_STATE = 4
private const val SHEET_PERMANENT_LGA = 5
private const val SHEET_PERMANENT_AREA = 6

private const val LEAVE_TITLE = "Reminder"
private const val LEAVE_MESSAGE = "Do you want to leave registration? Your progress on this screen will be lost."
private val AREA_CHOICES = listOf(AREA_OTHER)

/** Registration step 2 of 2, first page: residential address, optional permanent address (reference B4 to B6). */
@Composable
fun RegisterAddressScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onLeave: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val viewModel = rememberRegistrationViewModel()
    val form by viewModel.address.collectAsState()
    val locations by produceState(initialValue = emptyMap<String, List<String>>()) {
        value = NigeriaLocations.load(context)
    }
    var sheet by rememberSaveable { mutableStateOf(SHEET_NONE) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val states = remember(locations) { NigeriaLocations.states(locations) }
    val lgas = remember(locations, form.state) { NigeriaLocations.lgasFor(locations, form.state) }
    val permanentLgas = remember(locations, form.permanentState) {
        NigeriaLocations.lgasFor(locations, form.permanentState)
    }

    fun requestBack() {
        if (form.hasProgress) confirmLeave = true else onBack()
    }

    BackHandler { if (!leaving) requestBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            OPayTopBar(
                title = "",
                onBack = { requestBack() },
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Help",
                onAction = onHelp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Residential Address",
                    style = OPayType.headline,
                    color = c.text
                )
                Text(
                    text = "Step 2 of 2",
                    style = OPayType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
                InfoPill("Please provide your complete residential address.")
                OPayCard {
                    AddressFields(
                        title = "Current address",
                        state = form.state,
                        lga = form.lga,
                        area = form.area,
                        fullAddress = form.fullAddress,
                        onPickState = { sheet = SHEET_STATE },
                        onPickLga = { sheet = SHEET_LGA },
                        onPickArea = { sheet = SHEET_AREA },
                        onFullAddress = viewModel::setFullAddress
                    )
                    AddressTextField(
                        label = "Landmark/Nearest Bus Stop (Optional)",
                        value = form.landmark,
                        onValueChange = viewModel::setLandmark,
                        placeholder = "Closest notable building or bus stop",
                        singleLine = true,
                        minLines = 1
                    )
                }
                CheckRow(
                    checked = form.permanentSame,
                    text = "My permanent address is the same as my residential address",
                    onToggle = { viewModel.setPermanentSame(!form.permanentSame) }
                )
                if (!form.permanentSame) {
                    Spacer(Modifier.height(10.dp))
                    OPayCard {
                        AddressFields(
                            title = "Permanent address",
                            state = form.permanentState,
                            lga = form.permanentLga,
                            area = form.permanentArea,
                            fullAddress = form.permanentFullAddress,
                            onPickState = { sheet = SHEET_PERMANENT_STATE },
                            onPickLga = { sheet = SHEET_PERMANENT_LGA },
                            onPickArea = { sheet = SHEET_PERMANENT_AREA },
                            onFullAddress = viewModel::setPermanentFullAddress
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OPayButton(text = "Next", onClick = onNext, enabled = form.isValid && !leaving)
            }
        }

        LocationSheet(
            visible = sheet == SHEET_STATE,
            items = states,
            selected = form.state,
            onPick = {
                viewModel.setState(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )
        LocationSheet(
            visible = sheet == SHEET_LGA,
            items = lgas,
            selected = form.lga,
            onPick = {
                viewModel.setLga(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )
        LocationSheet(
            visible = sheet == SHEET_AREA,
            items = AREA_CHOICES,
            selected = form.area,
            onPick = {
                viewModel.setArea(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )
        LocationSheet(
            visible = sheet == SHEET_PERMANENT_STATE,
            items = states,
            selected = form.permanentState,
            onPick = {
                viewModel.setPermanentState(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )
        LocationSheet(
            visible = sheet == SHEET_PERMANENT_LGA,
            items = permanentLgas,
            selected = form.permanentLga,
            onPick = {
                viewModel.setPermanentLga(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )
        LocationSheet(
            visible = sheet == SHEET_PERMANENT_AREA,
            items = AREA_CHOICES,
            selected = form.permanentArea,
            onPick = {
                viewModel.setPermanentArea(it)
                sheet = SHEET_NONE
            },
            onDismiss = { sheet = SHEET_NONE }
        )

        if (confirmLeave) {
            OPayDialog(
                title = LEAVE_TITLE,
                message = LEAVE_MESSAGE,
                primaryText = "Leave",
                onPrimary = {
                    confirmLeave = false
                    leaving = true
                    viewModel.leaveRegistration(onLeave)
                },
                onDismiss = { confirmLeave = false },
                secondaryText = "Stay",
                onSecondary = { confirmLeave = false }
            )
        }
        if (leaving) BusyOverlay()
    }
}

@Composable
private fun AddressFields(
    title: String,
    state: String,
    lga: String,
    area: String,
    fullAddress: String,
    onPickState: () -> Unit,
    onPickLga: () -> Unit,
    onPickArea: () -> Unit,
    onFullAddress: (String) -> Unit
) {
    val c = OPay.colors
    Text(
        text = title,
        style = OPayType.titleSmall,
        fontWeight = FontWeight.Bold,
        color = c.text
    )
    SelectRow(label = "State", value = state, placeholder = "Select state", enabled = true, onClick = onPickState)
    SelectRow(label = "LGA", value = lga, placeholder = "Select LGA", enabled = state.isNotEmpty(), onClick = onPickLga)
    SelectRow(label = "Area", value = area, placeholder = "Select area", enabled = true, onClick = onPickArea)
    AddressTextField(
        label = "Full Address",
        value = fullAddress,
        onValueChange = onFullAddress,
        placeholder = "House number and street name",
        singleLine = false,
        minLines = 3
    )
    Text(
        text = "${fullAddress.length}/$ADDRESS_MAX_LENGTH",
        style = OPayType.caption,
        color = c.muted,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
    val touched = fullAddress.isNotEmpty()
    RuleRow("Minimum $ADDRESS_MIN_LENGTH characters", AddressRules.longEnough(fullAddress), touched)
    RuleRow("Not purely numbers", AddressRules.notPurelyNumbers(fullAddress), touched)
    RuleRow("House number + Street", AddressRules.houseAndStreet(fullAddress), touched)
}

@Composable
private fun InfoPill(text: String) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.greenSoft)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(text = text, style = OPayType.bodySmall, color = c.green)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = OPayType.caption,
        color = OPay.colors.muted,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SelectRow(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val c = OPay.colors
    val shape = RoundedCornerShape(8.dp)
    FieldLabel(label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cardAlt)
            .border(1.dp, c.divider, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            style = OPayType.titleSmall,
            color = if (value.isEmpty() || !enabled) c.muted else c.text,
            modifier = Modifier.weight(1f)
        )
        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
    }
}

@Composable
private fun AddressTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int
) {
    val c = OPay.colors
    val shape = RoundedCornerShape(8.dp)
    val style = TextStyle(fontSize = 15.sp, color = c.text)
    var focused by remember { mutableStateOf(false) }
    FieldLabel(label)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cardAlt)
            .border(1.5.dp, if (focused) c.green else c.divider, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = style, color = c.muted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            singleLine = singleLine,
            minLines = minLines,
            textStyle = style,
            cursorBrush = SolidColor(c.green),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
    }
}

@Composable
private fun RuleRow(text: String, ok: Boolean, touched: Boolean) {
    val c = OPay.colors
    val dot: Color = when {
        ok -> c.green
        touched -> c.danger
        else -> c.muted
    }
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = OPayType.caption, color = c.muted)
    }
}

@Composable
private fun CheckRow(checked: Boolean, text: String, onToggle: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) c.green else Color.Transparent)
                .border(1.5.dp, if (checked) c.green else c.muted, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                OPayIconView(icon = OPayIcon.Check, size = 13.dp, tint = c.onGreen)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = OPayType.bodySmall,
            color = c.text,
            modifier = Modifier.weight(1f)
        )
    }
}
