package com.westly.opay.feature.savings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.SavingsBook
import com.westly.opay.data.SavingsKind
import com.westly.opay.data.SavingsRates
import com.westly.opay.feature.wealth.ActivityItem
import com.westly.opay.feature.wealth.WealthViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.OPayToastState
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.navigation.Routes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

internal const val NOT_AVAILABLE = ""

internal object SavingsColors {
    val Page = Color(0xFFE6F6EF)
    val Card = Color.White
    val Ink = Color(0xFF1B1B1B)
    val Muted = Color(0xFF7A7A80)
    val Green = Color(0xFF1FE0A4)
    val GreenDeep = Color(0xFF1D7A5A)
    val OnGreen = Color(0xFF042619)
    val Mint = Color(0xFFD3F1E4)
    val Red = Color(0xFFFF4D4F)
    val Divider = Color(0xFFE6E6E9)
    val HeroStart = Color(0xFF0F5C43)
    val HeroEnd = Color(0xFF1D8F68)
    val Disabled = Color(0xFFD5D8DA)
}

private var lightBarOwners = 0

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun LightStatusBars() {
    val view = LocalView.current
    val restoreLight = OPay.colors.bg.luminance() >= 0.5f
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        lightBarOwners += 1
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            lightBarOwners -= 1
            if (lightBarOwners == 0) {
                controller?.isAppearanceLightStatusBars = restoreLight
            }
        }
    }
}

@Composable
internal fun SavingsPage(
    title: String,
    onBack: () -> Unit,
    toast: OPayToastState,
    modifier: Modifier = Modifier,
    bottomText: String? = null,
    onBottomClick: () -> Unit = {},
    showFooter: Boolean = true,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    LightStatusBars()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SavingsColors.Page)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Back"; role = Role.Button }
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = SavingsColors.Ink)
                }
                Text(
                    text = title,
                    style = OPayType.title,
                    color = SavingsColors.Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "More",
                    style = OPayType.titleSmall,
                    color = SavingsColors.Muted,
                    modifier = Modifier.clickable { toast.show(NOT_AVAILABLE) }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                content()
                if (showFooter) {
                    Text(
                        text = "Powered by OPay Microfinance Bank Insured by NDIC",
                        style = OPayType.captionSmall,
                        color = SavingsColors.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 16.dp)
                    )
                }
            }
            if (bottomText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    SavingsButton(text = bottomText, onClick = onBottomClick)
                }
            }
        }
        overlay?.invoke(this)
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
internal fun SavingsButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(OPayShapes.pill)
            .background(SavingsColors.Green)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = OPayType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = SavingsColors.OnGreen
        )
    }
}

@Composable
internal fun SavingsPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Text(
        text = text,
        style = OPayType.bodySmall,
        fontWeight = FontWeight.Bold,
        color = if (enabled) SavingsColors.OnGreen else SavingsColors.Muted,
        modifier = modifier
            .background(
                if (enabled) SavingsColors.Green else SavingsColors.Disabled,
                RoundedCornerShape(16.dp)
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
internal fun SavingsChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = OPayType.caption,
        color = SavingsColors.GreenDeep,
        modifier = modifier
            .background(SavingsColors.Mint, OPayShapes.chip)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
internal fun HeroChip(text: String) {
    Text(
        text = text,
        style = OPayType.caption,
        color = Color.White,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.2f), OPayShapes.chip)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
internal fun SavingsTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .background(SavingsColors.Red, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
internal fun SavingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(SavingsColors.Card, OPayShapes.card)
            .padding(12.dp),
        content = content
    )
}

@Composable
internal fun SavingsHero(
    art: @Composable BoxScope.() -> Unit,
    minHeight: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(Brush.linearGradient(listOf(SavingsColors.HeroStart, SavingsColors.HeroEnd)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
            content = content
        )
        art()
    }
}

@Composable
internal fun SavingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SavingsColors.Divider)
    )
}

private val ActivityZone: ZoneId = ZoneId.of("Africa/Lagos")
private val ActivityFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.US)

@Composable
fun SafeBoxScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WealthViewModel = viewModel()
) {
    val toast = rememberOPayToast()
    val action = rememberSavingsAction(toast)
    var regularOn by rememberSaveable { mutableStateOf(false) }
    var depositOpen by rememberSaveable { mutableStateOf(false) }
    var withdrawOpen by rememberSaveable { mutableStateOf(false) }
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val activities by remember(viewModel) { viewModel.activities(SavingsKind.SafeBox) }.collectAsStateWithLifecycle()
    val box = SavingsBook.ofKind(positions, SavingsKind.SafeBox).firstOrNull()
    val totals = summary.of(SavingsKind.SafeBox)

    SavingsPage(
        title = "SafeBox",
        onBack = onBack,
        toast = toast,
        modifier = modifier,
        bottomText = "Deposit Now",
        onBottomClick = {
            action.clear()
            depositOpen = true
        },
        overlay = {
            SavingsAmountSheet(
                visible = depositOpen,
                title = "Deposit to SafeBox",
                confirmText = "Deposit",
                action = action,
                onDismiss = { depositOpen = false },
                onConfirm = { kobo ->
                    action.start(
                        call = { done ->
                            if (box == null) {
                                viewModel.create(SavingsKind.SafeBox, "SafeBox", kobo, onDone = done)
                            } else {
                                viewModel.deposit(box.id, kobo, done)
                            }
                        },
                        onSuccess = {
                            depositOpen = false
                            toast.show("Deposit successful")
                        }
                    )
                }
            )
            SavingsAmountSheet(
                visible = withdrawOpen,
                title = "Withdraw from SafeBox",
                confirmText = "Withdraw",
                action = action,
                onDismiss = { withdrawOpen = false },
                note = "Accrued interest is paid out with a withdrawal.",
                allKobo = box?.principalKobo,
                onConfirm = { kobo ->
                    val id = box?.id
                    if (id != null) {
                        action.start(
                            call = { done -> viewModel.withdraw(id, kobo, done) },
                            onSuccess = {
                                withdrawOpen = false
                                toast.show("Withdrawal successful")
                            }
                        )
                    }
                }
            )
        }
    ) {
        SavingsHero(
            art = {
                SafeIllustration(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(92.dp)
                )
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SafeBox",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                HeroChip(text = SavingsBook.rateLabel(box?.rateBps ?: SavingsRates.SAFEBOX_BPS))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatNaira(totals.totalKobo),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Save daily, weekly or monthly with discipline",
                style = OPayType.caption,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(0.62f)
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interest",
                    style = OPayType.caption,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(text = formatNaira(totals.accruedKobo), style = OPayType.caption, color = Color.White)
            }
        }

        SavingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Regular Save",
                    style = OPayType.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SavingsColors.Ink
                )
                Spacer(Modifier.width(8.dp))
                SavingsChip(text = if (regularOn) "On" else "Off")
                Spacer(Modifier.weight(1f))
                SavingsPill(
                    text = if (regularOn) "Turn off" else "Turn on",
                    onClick = { regularOn = !regularOn }
                )
            }
        }

        SavingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SafeBoxTile(icon = OPayIcon.Bank, label = "Withdraw") {
                    if (box == null) {
                        toast.show("No SafeBox savings yet")
                    } else {
                        action.clear()
                        withdrawOpen = true
                    }
                }
                SafeBoxTile(icon = OPayIcon.Chart, label = "Interests") {
                    toast.show("Interest: " + formatNaira(totals.accruedKobo))
                }
                SafeBoxTile(icon = OPayIcon.List, label = "Settings") { toast.show(NOT_AVAILABLE) }
            }
        }

        SavingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent activities",
                    style = OPayType.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SavingsColors.Ink,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.clickable { onNavigate(Routes.Transactions) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "View All", style = OPayType.bodySmall, color = SavingsColors.Muted)
                    OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = SavingsColors.Muted)
                }
            }
            if (activities.isEmpty()) {
                Text(
                    text = "No recent activities",
                    style = OPayType.bodySmall,
                    color = SavingsColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp)
                )
            } else {
                activities.forEachIndexed { index, item ->
                    if (index > 0) SavingsDivider()
                    SafeBoxActivity(item = item)
                }
            }
        }
    }
}

@Composable
private fun SafeBoxTile(icon: OPayIcon, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(OPayShapes.tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SavingsColors.Mint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 20.dp, tint = SavingsColors.GreenDeep)
        }
        Spacer(Modifier.height(5.dp))
        Text(text = label, style = OPayType.caption, color = SavingsColors.Ink)
    }
}

@Composable
internal fun SafeBoxActivity(item: ActivityItem) {
    val sign = if (item.amountKobo < 0) "- " else "+ "
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = OPayType.body, color = SavingsColors.Ink)
            Text(
                text = ActivityFormat.format(Instant.ofEpochMilli(item.timestampMillis).atZone(ActivityZone)),
                style = OPayType.captionSmall,
                color = SavingsColors.Muted
            )
        }
        Text(
            text = sign + formatNaira(abs(item.amountKobo)),
            style = OPayType.body,
            fontWeight = FontWeight.Bold,
            color = SavingsColors.Ink
        )
    }
}

@Composable
private fun SafeIllustration(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 90f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)

        drawRoundRect(
            color = Color(0xFF0B3F2E),
            topLeft = o(14f, 78f),
            size = sz(12f, 8f),
            cornerRadius = CornerRadius(2f * s)
        )
        drawRoundRect(
            color = Color(0xFF0B3F2E),
            topLeft = o(64f, 78f),
            size = sz(12f, 8f),
            cornerRadius = CornerRadius(2f * s)
        )
        drawRoundRect(
            color = Color(0xFFBFF2DC),
            topLeft = o(6f, 8f),
            size = sz(78f, 72f),
            cornerRadius = CornerRadius(9f * s)
        )
        drawRoundRect(
            color = Color(0xFF2FB88A),
            topLeft = o(14f, 16f),
            size = sz(62f, 56f),
            cornerRadius = CornerRadius(6f * s)
        )
        drawCircle(color = Color(0xFFE6F6EF), radius = 16f * s, center = o(45f, 44f))
        drawCircle(
            color = Color(0xFF0F5C43),
            radius = 16f * s,
            center = o(45f, 44f),
            style = Stroke(width = 2.4f * s)
        )
        drawCircle(color = Color(0xFF0F5C43), radius = 4f * s, center = o(45f, 44f))
        drawLine(
            color = Color(0xFF0F5C43),
            start = o(45f, 44f),
            end = o(45f, 32f),
            strokeWidth = 2.4f * s
        )
        drawRoundRect(
            color = Color(0xFFE6F6EF),
            topLeft = o(66f, 40f),
            size = sz(5f, 12f),
            cornerRadius = CornerRadius(2f * s)
        )
    }
}
