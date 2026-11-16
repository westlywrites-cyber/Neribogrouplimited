package com.westly.opay.feature.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.SavingsBook
import com.westly.opay.data.SavingsKind
import com.westly.opay.data.SavingsPosition
import com.westly.opay.data.SavingsRates
import com.westly.opay.feature.wealth.WealthViewModel
import java.time.ZonedDateTime
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.rememberOPayToast

private data class AboutItem(val icon: OPayIcon, val title: String, val body: String)

private val AboutItems = listOf(
    AboutItem(OPayIcon.Chart, "Interest Yield", "Interest rate is " + SavingsBook.rateLabel(SavingsRates.TARGET_BPS) + "."),
    AboutItem(OPayIcon.Clock, "Savings Duration", "One to twelve months."),
    AboutItem(
        OPayIcon.Plus,
        "Savings Top-up",
        "Anytime (manual or automatic top-up options available)."
    ),
    AboutItem(
        OPayIcon.Save,
        "Withdrawal",
        "You can withdraw anytime. Accrued interest is paid out with your withdrawal."
    ),
    AboutItem(
        OPayIcon.Check,
        "Halal Compliant",
        "You have the option to choose not to receive interests on your savings."
    )
)

@Composable
fun TargetsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WealthViewModel = viewModel()
) {
    val toast = rememberOPayToast()
    val action = rememberSavingsAction(toast)
    var showPopular by rememberSaveable { mutableStateOf(false) }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var createName by rememberSaveable { mutableStateOf("") }
    var depositId by rememberSaveable { mutableStateOf<String?>(null) }
    var withdrawId by rememberSaveable { mutableStateOf<String?>(null) }
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val targets = SavingsBook.ofKind(positions, SavingsKind.Target)

    SavingsPage(
        title = "Targets",
        onBack = onBack,
        toast = toast,
        modifier = modifier,
        bottomText = "Create Target",
        onBottomClick = {
            action.clear()
            createName = ""
            createOpen = true
        },
        overlay = {
            if (showPopular) {
                PopularTargetsOverlay(
                    onClose = { showPopular = false },
                    onMessage = { toast.show(it) }
                )
            }
            CreateTargetSheet(
                visible = createOpen,
                initialName = createName,
                action = action,
                onDismiss = { createOpen = false },
                onCreate = { name, targetKobo, firstKobo, months ->
                    val maturesAt = months?.let { ZonedDateTime.now().plusMonths(it.toLong()).toInstant().toString() }
                    action.start(
                        call = { done ->
                            viewModel.create(
                                SavingsKind.Target,
                                name,
                                firstKobo,
                                targetKobo = targetKobo,
                                maturesAt = maturesAt,
                                onDone = done
                            )
                        },
                        onSuccess = {
                            createOpen = false
                            toast.show("Target created")
                        }
                    )
                }
            )
            SavingsAmountSheet(
                visible = depositId != null,
                title = "Add to " + (targets.firstOrNull { it.id == depositId }?.name ?: "target"),
                confirmText = "Save",
                action = action,
                onDismiss = { depositId = null },
                onConfirm = { kobo ->
                    val id = depositId
                    if (id != null) {
                        action.start(
                            call = { done -> viewModel.deposit(id, kobo, done) },
                            onSuccess = {
                                depositId = null
                                toast.show("Deposit successful")
                            }
                        )
                    }
                }
            )
            val withdrawTarget = targets.firstOrNull { it.id == withdrawId }
            SavingsAmountSheet(
                visible = withdrawTarget != null,
                title = "Withdraw from " + (withdrawTarget?.name ?: "target"),
                confirmText = "Withdraw",
                action = action,
                onDismiss = { withdrawId = null },
                note = "Accrued interest is paid out with a withdrawal.",
                allKobo = withdrawTarget?.principalKobo,
                onConfirm = { kobo ->
                    val id = withdrawId
                    if (id != null) {
                        action.start(
                            call = { done -> viewModel.withdraw(id, kobo, done) },
                            onSuccess = {
                                withdrawId = null
                                toast.show("Withdrawal successful")
                            }
                        )
                    }
                }
            )
        }
    ) {
        if (targets.isNotEmpty()) {
            SavingsCard {
                Text(
                    text = "My Targets",
                    style = OPayType.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SavingsColors.Ink
                )
                targets.forEachIndexed { index, target ->
                    if (index > 0) SavingsDivider()
                    TargetRow(
                        target = target,
                        onAdd = {
                            action.clear()
                            depositId = target.id
                        },
                        onWithdraw = {
                            action.clear()
                            withdrawId = target.id
                        }
                    )
                }
            }
        }
        SavingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Find Targets",
                    style = OPayType.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SavingsColors.Ink,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.clickable { showPopular = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "More", style = OPayType.bodySmall, color = SavingsColors.Muted)
                    OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = SavingsColors.Muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PopularTargets.take(3).forEach { target ->
                    FindTargetTile(target = target, onClick = {
                        action.clear()
                        createName = target.name
                        createOpen = true
                    })
                }
            }
        }

        SavingsCard {
            Text(
                text = "About Targets",
                style = OPayType.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SavingsColors.Ink
            )
            AboutItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(SavingsColors.Mint, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        OPayIconView(icon = item.icon, size = 16.dp, tint = SavingsColors.GreenDeep)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = OPayType.body,
                            fontWeight = FontWeight.Bold,
                            color = SavingsColors.Ink
                        )
                        Text(text = item.body, style = OPayType.caption, color = SavingsColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetRow(target: SavingsPosition, onAdd: () -> Unit, onWithdraw: () -> Unit) {
    val goal = target.targetKobo ?: 0L
    val fraction = if (goal > 0L) (target.principalKobo.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = target.name, style = OPayType.body, fontWeight = FontWeight.Bold, color = SavingsColors.Ink)
                Text(
                    text = formatNaira(target.principalKobo) + " of " + formatNaira(goal) + "  " +
                        SavingsBook.rateLabel(target.rateBps),
                    style = OPayType.caption,
                    color = SavingsColors.Muted
                )
                Text(
                    text = "Interest " + formatNaira(target.accruedKobo),
                    style = OPayType.captionSmall,
                    color = SavingsColors.Muted
                )
            }
            SavingsPill(text = "Add", onClick = onAdd)
            Spacer(Modifier.width(6.dp))
            SavingsPill(text = "Withdraw", onClick = onWithdraw)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(SavingsColors.Mint)
                .drawBehind {
                    drawRect(color = SavingsColors.Green, size = Size(size.width * fraction, size.height))
                }
        )
    }
}

@Composable
private fun FindTargetTile(target: TargetInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(OPayShapes.tile)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(OPayShapes.tile)
                .background(Brush.linearGradient(listOf(target.start, target.end))),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = target.icon, size = 28.dp, tint = Color.White)
            if (target.hot) {
                SavingsTag(
                    text = "Hot",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = target.name,
            style = OPayType.caption,
            fontWeight = FontWeight.Bold,
            color = SavingsColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = target.joined + " have joined",
            style = OPayType.captionSmall,
            color = SavingsColors.Muted
        )
    }
}
