package com.westly.opay.feature.transfer

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.data.model.Beneficiary
import com.westly.opay.data.model.ResolvedAccount
import com.westly.opay.feature.receipt.BankLogoBadge
import com.westly.opay.navigation.Routes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val MAX_LIST_ROWS = 5
private val FreeStripBackground = Color(0xFF3B2A52)
private val FreeStripText = Color(0xFFB9A2FF)

@Composable
fun TransferBankScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = viewModel<TransferViewModel>(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val c = OPay.colors
    val focus = LocalFocusManager.current
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val lookup by viewModel.lookupState.collectAsStateWithLifecycle()
    val freeLeft by viewModel.freeTransfersLeft.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf(draft.accountNumber) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(draft.accountNumber) {
        if (input != draft.accountNumber) input = draft.accountNumber
    }

    val goBack = {
        viewModel.reset()
        onBack()
    }
    BackHandler(onBack = goBack)

    val suggestions = remember(input) {
        if (input.length in 1 until ACCOUNT_LENGTH) viewModel.suggestions(input).take(2) else emptyList()
    }
    val source = if (tab == 0) recents else favourites
    val visible = source
        .filter {
            query.isBlank() ||
                it.name.contains(query.trim(), ignoreCase = true) ||
                it.accountNumber.contains(query.trim())
        }
        .take(MAX_LIST_ROWS)

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
                title = "Transfer to Bank Account",
                onBack = goBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "History",
                onAction = { onNavigate(Routes.Transactions) }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                PromoBanner(onClaim = { onNavigate(Routes.AllService) })
                FreeTransfersStrip(left = freeLeft)
                SectionCard {
                    Text(
                        text = "Recipient Account",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    OPayUnderlineField(
                        value = input,
                        onValueChange = { raw ->
                            val digits = raw.filter { it in '0'..'9' }.take(ACCOUNT_LENGTH)
                            input = digits
                            viewModel.onAccountChanged(digits)
                        },
                        placeholder = "Enter 10 digits Account Number",
                        keyboardType = KeyboardType.Number,
                        maxLength = ACCOUNT_LENGTH
                    )
                    val resolved = draft.resolved
                    when {
                        lookup is LookupState.Loading -> NameSkeleton()
                        resolved != null -> ResolvedRow(resolved)
                        lookup.isMessage() -> LookupMessage(state = lookup, onRetry = viewModel::retryLookup)
                        suggestions.isNotEmpty() -> SuggestionList(
                            items = suggestions,
                            onPick = {
                                focus.clearFocus()
                                viewModel.onAccountChanged(it.accountNumber)
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onNavigate(Routes.SelectBank) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "${draft.bank?.name ?: "Select Bank"} \u203A",
                            style = OPayType.body,
                            color = if (draft.bank != null) c.text else c.muted
                        )
                    }
                    OPayButton(
                        text = "Next",
                        onClick = {
                            focus.clearFocus()
                            onNavigate(Routes.TransferAmount)
                        },
                        modifier = Modifier.padding(top = 10.dp),
                        enabled = resolved != null
                    )
                }
                SectionCard(onClick = { onNavigate(Routes.SuccessRate) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OPayIconView(icon = OPayIcon.Chart, size = 16.dp, tint = c.text)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Bank Transfer Success Rate Monitor",
                            style = OPayType.body,
                            color = c.text
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabLabel(text = "Recents", selected = tab == 0, onClick = { tab = 0 })
                    Spacer(Modifier.width(16.dp))
                    TabLabel(text = "Favourites", selected = tab == 1, onClick = { tab = 1 })
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = if (searching) "Close search" else "Search"; role = Role.Button }
                            .clickable {
                                searching = !searching
                                if (!searching) query = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SearchIcon(tint = if (searching) c.green else c.muted)
                    }
                }
                if (searching) {
                    OPayUnderlineField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search name or account number",
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                    )
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
                            BeneficiaryRow(
                                item = item,
                                onClick = {
                                    focus.clearFocus()
                                    viewModel.selectBeneficiary(item)
                                }
                            )
                        }
                    }
                    OPayRowDivider()
                    OPayRow(title = "View All", onClick = { onNavigate(Routes.Beneficiaries) })
                }
                SectionCard(onClick = { onNavigate(Routes.AllService) }) {
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
                        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        LookupRateLimitedDialog(state = lookup, onDismiss = viewModel::dismissLookup)
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(OPay.colors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun PromoBanner(onClaim: () -> Unit) {
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
            Text(
                text = "Claim",
                style = OPayType.caption,
                fontWeight = FontWeight.Bold,
                color = c.onGreen,
                modifier = Modifier
                    .clip(OPayShapes.pill)
                    .background(c.green)
                    .clickable(onClick = onClaim)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun FreeTransfersStrip(left: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(FreeStripBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = "\u2726 Free transfers for the day: ",
            style = OPayType.body,
            color = FreeStripText
        )
        Text(
            text = left.toString(),
            style = OPayType.body,
            fontWeight = FontWeight.Bold,
            color = FreeStripText
        )
    }
}

@Composable
private fun InitialAvatar(name: String) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(c.greenSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = OPayType.bodySmall,
            fontWeight = FontWeight.Bold,
            color = c.green
        )
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
                InitialAvatar(name = item.name)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = OPayType.body,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.accountNumber,
                        style = OPayType.captionSmall,
                        color = c.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun NameSkeleton() {
    SkeletonBlock(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        height = 42.dp
    )
}

@Composable
private fun ResolvedRow(account: ResolvedAccount) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(OPayShapes.tile)
            .background(c.green.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(c.green, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Check, size = 14.dp, tint = c.onGreen)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name.uppercase(),
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = c.green
            )
            Text(text = account.bank.name, style = OPayType.caption, color = c.muted)
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Text(
        text = text,
        style = OPayType.body,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) c.green else c.muted,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun BeneficiaryRow(item: Beneficiary, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BankLogoBadge(bankName = item.bankName, opay = item.bankName == "OPay", size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = OPayType.body,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.bankName} | ${item.accountNumber}",
                style = OPayType.captionSmall,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round)
        drawCircle(
            color = tint,
            radius = 6.5f * unit,
            center = Offset(10f * unit, 10f * unit),
            style = stroke
        )
        drawLine(
            color = tint,
            start = Offset(15f * unit, 15f * unit),
            end = Offset(21f * unit, 21f * unit),
            strokeWidth = 2f * unit,
            cap = StrokeCap.Round
        )
    }
}
