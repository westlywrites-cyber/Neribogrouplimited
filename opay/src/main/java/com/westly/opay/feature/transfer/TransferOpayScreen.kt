package com.westly.opay.feature.transfer

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.data.model.Beneficiary
import com.westly.opay.data.model.ResolvedAccount
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val OPAY_INPUT_LENGTH = 11
private const val MAX_LIST_ROWS = 5
private const val NUMBER_LENGTH = 10

internal fun groupAccountNumber(number: String): String =
    if (number.length == NUMBER_LENGTH) {
        number.substring(0, 3) + " " + number.substring(3, 6) + " " + number.substring(6)
    } else {
        number
    }

private fun isCompleteOpayInput(digits: String): Boolean =
    if (digits.startsWith("0")) digits.length == OPAY_INPUT_LENGTH else digits.length == NUMBER_LENGTH

@Composable
internal fun NameAvatar(name: String, size: Dp = 30.dp) {
    val c = OPay.colors
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1) }
        .uppercase()
    Box(
        modifier = Modifier
            .size(size)
            .background(c.cardAlt, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = OPayType.caption,
            fontWeight = FontWeight.Bold,
            color = c.muted
        )
    }
}

@Composable
fun TransferOpayScreen(
    onBack: () -> Unit,
    onOpenAmount: () -> Unit,
    onHistory: () -> Unit,
    onBeneficiaries: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = viewModel<TransferViewModel>(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val c = OPay.colors
    val focus = LocalFocusManager.current
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val matching by viewModel.matching.collectAsStateWithLifecycle()
    val lookup by viewModel.lookupState.collectAsStateWithLifecycle()
    val recents by viewModel.opayRecents.collectAsStateWithLifecycle()
    val favourites by viewModel.opayFavourites.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var awaiting by rememberSaveable { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf(false) }
    var started by rememberSaveable { mutableStateOf(false) }
    var askVisible by rememberSaveable { mutableStateOf(false) }

    val resolved = draft.resolved

    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            viewModel.consumeOpayTransfer()
        }
    }
    LaunchedEffect(resolved) {
        if (awaiting && resolved != null) {
            awaiting = false
            opened = true
            focus.clearFocus()
            onOpenAmount()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (opened) {
            opened = false
            input = ""
            viewModel.consumeOpayTransfer()
        }
    }

    val goBack = {
        viewModel.consumeOpayTransfer()
        onBack()
    }
    BackHandler(onBack = goBack)

    fun begin(number: String) {
        input = number
        awaiting = true
        viewModel.startOpayTransfer(number)
    }

    fun change(raw: String) {
        val digits = raw.filter { it in '0'..'9' }.take(OPAY_INPUT_LENGTH)
        input = digits
        if (isCompleteOpayInput(digits)) {
            begin(digits)
        } else {
            awaiting = false
            viewModel.consumeOpayTransfer()
        }
    }

    val suggestions = remember(input) {
        val prefix = if (input.startsWith("0")) input.drop(1) else input
        if (input.length in 1 until NUMBER_LENGTH && prefix.isNotEmpty()) {
            viewModel.opaySuggestions(prefix).take(2)
        } else {
            emptyList()
        }
    }
    val source = if (tab == 0) recents else favourites
    val visible = source.take(MAX_LIST_ROWS)

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
                title = "Transfer to OPay Account",
                onBack = goBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "History",
                onAction = onHistory
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                PromoBanner()
                InstantStrip()
                SectionCard {
                    Text(
                        text = "Recipient Account",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    OPayUnderlineField(
                        value = input,
                        onValueChange = { change(it) },
                        placeholder = "Phone No./OPay Account No.",
                        keyboardType = KeyboardType.Number,
                        maxLength = OPAY_INPUT_LENGTH,
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .semantics { contentDescription = "Scan QR code"; role = Role.Button }
                                    .clickable(onClick = onScan),
                                contentAlignment = Alignment.Center
                            ) {
                                OPayIconView(icon = OPayIcon.Scan, size = 22.dp, tint = c.green)
                            }
                        }
                    )
                    when {
                        matching -> SearchingRow()
                        awaiting && resolved == null && lookup.isMessage() ->
                            LookupMessage(state = lookup, onRetry = viewModel::retryLookup)
                        suggestions.isNotEmpty() -> SuggestionList(
                            items = suggestions,
                            onPick = {
                                focus.clearFocus()
                                begin(it.accountNumber)
                            }
                        )
                    }
                    Text(
                        text = "Don't know the recipient's OPay account number? Ask them \u203A",
                        style = OPayType.caption,
                        color = c.muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { askVisible = true }
                            .padding(top = 14.dp, bottom = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabLabel(text = "Recents", selected = tab == 0, onClick = { tab = 0 })
                    Spacer(Modifier.width(20.dp))
                    TabLabel(text = "Favourites", selected = tab == 1, onClick = { tab = 1 })
                }
                SectionCard {
                    if (visible.isEmpty()) {
                        Text(
                            text = if (tab == 0) "No recents yet" else "No favourites yet",
                            style = OPayType.body,
                            color = c.muted,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        visible.forEachIndexed { index, item ->
                            if (index > 0) OPayRowDivider()
                            PersonRow(
                                item = item,
                                onClick = {
                                    focus.clearFocus()
                                    begin(item.accountNumber)
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "View All \u203A",
                            style = OPayType.caption,
                            color = c.text,
                            modifier = Modifier
                                .clip(OPayShapes.pill)
                                .background(c.cardAlt)
                                .clickable(onClick = onBeneficiaries)
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                        )
                    }
                    OPayRowDivider(modifier = Modifier.padding(top = 10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(c.greenSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            OPayIconView(icon = OPayIcon.User, size = 16.dp, tint = c.green)
                        }
                        Text(
                            text = "See who else is using OPay\u2026",
                            style = OPayType.body,
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                SectionCard {
                    Text(
                        text = "More Events",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OPayIconView(icon = OPayIcon.Gift, size = 18.dp, tint = c.green)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Super Voucher Package",
                                style = OPayType.body,
                                color = c.text
                            )
                            Text(
                                text = "Claim 15 Discounts with \u20A699 on any Bill",
                                style = OPayType.caption,
                                color = c.muted
                            )
                        }
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        if (askVisible) {
            OPayDialog(
                title = "OPay Account Number",
                message = "Ask them to open OPay and tap the profile picture to find their OPay account number.",
                primaryText = "OK",
                onPrimary = { askVisible = false },
                onDismiss = { askVisible = false }
            )
        }
        LookupRateLimitedDialog(state = lookup, onDismiss = viewModel::dismissLookup)
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(OPay.colors.card)
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun PromoBanner() {
    val c = OPay.colors
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OPayIconView(icon = OPayIcon.Gift, size = 18.dp, tint = c.green)
            Text(
                text = "Claim 15 Discounts with \u20A699 on any Bill",
                style = OPayType.bodySmall,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InstantStrip() {
    val c = OPay.colors
    Text(
        text = "Instant, Zero Issues, Free",
        style = OPayType.body,
        color = c.green,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.tile)
            .background(c.greenSoft)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

@Composable
private fun SearchingRow() {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OPayLoader(size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(text = "Searching\u2026", style = OPayType.body, color = c.green)
    }
}

@Composable
private fun SuggestionList(items: List<ResolvedAccount>, onPick: (ResolvedAccount) -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(OPayShapes.tile)
            .background(c.cardAlt)
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) OPayRowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(item) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NameAvatar(name = item.name)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = OPayType.body,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = groupAccountNumber(item.accountNumber),
                        style = OPayType.captionSmall,
                        color = c.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = OPayType.body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) c.green else c.muted
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(24.dp)
                .height(2.dp)
                .background(if (selected) c.green else Color.Transparent)
        )
    }
}

@Composable
private fun PersonRow(item: Beneficiary, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NameAvatar(name = item.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.uppercase(),
                style = OPayType.body,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = groupAccountNumber(item.accountNumber),
                style = OPayType.captionSmall,
                color = c.muted
            )
        }
    }
}
