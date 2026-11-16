package com.westly.opay.feature.transfer

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendResult
import com.westly.opay.backend.BeneficiaryLists
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.EmptyState
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.model.Beneficiary
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

private const val FAVOURITE_FAILED = "Could not update favourite"
private const val COULD_NOT_VERIFY = "Could not verify account. Check your connection"

private fun lookupMessage(state: LookupState): String? = when (state) {
    LookupState.NotFound -> BackendCodes.messageFor(BackendCodes.ACCOUNT_NOT_FOUND)
    LookupState.OwnAccount -> BackendCodes.messageFor(BackendCodes.SELF_TRANSFER)
    LookupState.Unverified -> COULD_NOT_VERIFY
    else -> null
}

@Composable
fun BeneficiariesScreen(
    onBack: () -> Unit,
    onOpenAmount: () -> Unit,
    onOpenBank: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = viewModel<TransferViewModel>(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val recents by repository.recents.collectAsStateWithLifecycle()
    val favourites by repository.favourites.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val lookup by viewModel.lookupState.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var awaiting by rememberSaveable { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf(false) }

    val resolved = draft.resolved

    LaunchedEffect(Unit) {
        repository.refresh()
    }
    LaunchedEffect(resolved) {
        if (awaiting && resolved != null) {
            awaiting = false
            opened = true
            onOpenAmount()
        }
    }
    LaunchedEffect(lookup) {
        if (awaiting && (lookup.isMessage() || lookup == LookupState.RateLimited)) {
            awaiting = false
            lookupMessage(lookup)?.let { toast.show(it) }
            if (lookup.isMessage()) viewModel.consumeOpayTransfer()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (opened) {
            opened = false
            viewModel.consumeOpayTransfer()
        }
    }

    val goBack = {
        if (awaiting) viewModel.consumeOpayTransfer()
        onBack()
    }
    BackHandler(onBack = goBack)

    fun pick(item: Beneficiary) {
        if (awaiting) return
        if (BeneficiaryLists.isAt(item, repository.internalBank)) {
            awaiting = true
            viewModel.startOpayTransfer(item.accountNumber)
        } else {
            viewModel.consumeOpayTransfer()
            viewModel.selectBeneficiary(item)
            onOpenBank()
        }
    }

    val needle = query.trim()
    val visible = (if (tab == 0) recents else favourites).filter {
        needle.isEmpty() ||
            it.name.contains(needle, ignoreCase = true) ||
            it.accountNumber.contains(needle)
    }

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
                title = "Beneficiaries",
                onBack = goBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            OPayUnderlineField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Account number or name",
                modifier = Modifier.padding(horizontal = 16.dp),
                leading = { MagnifierIcon(tint = c.muted) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabLabel(text = "Recents", selected = tab == 0, onClick = { tab = 0 })
                Spacer(Modifier.width(20.dp))
                TabLabel(text = "Favourites", selected = tab == 1, onClick = { tab = 1 })
            }
            if (visible.isEmpty()) {
                EmptyState(message = "No beneficiaries yet")
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    visible.forEachIndexed { index, item ->
                        if (index > 0) OPayRowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pick(item) }
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
                                    text = "${item.bankName} | ${item.accountNumber}",
                                    style = OPayType.captionSmall,
                                    color = c.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .semantics {
                                        contentDescription =
                                            if (item.isFavourite) "Remove from favourites" else "Add to favourites"
                                        role = Role.Button
                                    }
                                    .clickable {
                                        scope.launch {
                                            val result = repository.toggleFavourite(item.accountNumber, item.bankCode)
                                            if (result is BackendResult.Failure) toast.show(FAVOURITE_FAILED)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                OPayIconView(
                                    icon = OPayIcon.Star,
                                    size = 20.dp,
                                    tint = if (item.isFavourite) c.green else c.muted
                                )
                            }
                        }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        if (awaiting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OPayLoader(size = 32.dp)
            }
        }
        LookupRateLimitedDialog(state = lookup, onDismiss = viewModel::dismissLookup)
        OPayToastHost(state = toast)
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
private fun MagnifierIcon(tint: Color) {
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
