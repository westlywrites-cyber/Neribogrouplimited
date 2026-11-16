package com.westly.opay.feature.invite

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.OverlayScrim
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import com.westly.opay.core.ui.overlay.rememberOPayToast
import kotlinx.coroutines.delay
import java.util.Locale

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
private const val VALIDITY_MILLIS = 7L * DAY_MILLIS

private val LightMint = Color(0xFFE3F7EE)
private val LightMintBox = Color(0xFFCDF2E1)
private val NoteTop = Color(0xFF1D7A5A)
private val NoteBottom = Color(0xFF0F4D38)
private val NoteText = Color(0xFF7BF0C8)
private val Skin = Color(0xFFE0A87C)
private val Sleeve = Color(0xFF3A3A3D)

private var celebrationShownThisLaunch = false

@Composable
fun InviteScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val profile by repository.profile.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()

    val dark = c.bg.luminance() < 0.5f
    val pageBackground = if (dark) c.bg else LightMint
    val accent = if (dark) c.green else c.greenDark
    val code = invitationCode(profile.phone, profile.accountNumber)

    var showCelebration by rememberSaveable { mutableStateOf(!celebrationShownThisLaunch) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showHowTo by rememberSaveable { mutableStateOf(false) }
    var showLeave by rememberSaveable { mutableStateOf(false) }
    val startMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { celebrationShownThisLaunch = true }

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000L)
            value = System.currentTimeMillis()
        }
    }

    BackHandler { showLeave = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            OPayTopBar(
                title = "",
                onBack = { showLeave = true },
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Rules",
                onAction = { showRules = true }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "Invite friends to OPay to earn rewards",
                    style = OPayType.headline,
                    color = accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                BanknoteGraphic()
                Text(
                    text = highlighted(
                        before = "Invite friends who have not used OPay or have been inactive for ",
                        highlight = "Over 180 Days",
                        after = " to join, and earn rewards!",
                        color = accent
                    ),
                    style = OPayType.bodySmall,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
                StepsCard(onEarn = { shareInvite(context, code) })
                CodeCard(
                    code = code,
                    boxColor = if (dark) c.cardAlt else LightMintBox,
                    accent = accent,
                    onCopy = {
                        clipboard.setText(AnnotatedString(code))
                        toast.show("Copied")
                    },
                    onHowTo = { showHowTo = true }
                )
                MilestonesCard(
                    remainingMillis = (startMillis + VALIDITY_MILLIS - now).coerceAtLeast(0L),
                    accent = accent,
                    onInvite = { shareInvite(context, code) },
                    onRecord = { toast.show("No records yet") }
                )
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }

    if (showCelebration) {
        CelebrationDialog(
            onInvite = {
                showCelebration = false
                shareInvite(context, code)
            },
            onDismiss = { showCelebration = false }
        )
    }
    if (showRules) {
        OPayDialog(
            title = "Rules",
            message = RULES.joinToString(separator = "\n") { "• $it" },
            primaryText = "Got it",
            onPrimary = { showRules = false },
            onDismiss = { showRules = false }
        )
    }
    if (showHowTo) {
        OPayDialog(
            title = "How to use invitation code?",
            message = "Share your invitation code with a friend. " +
                "Your friend enters it when signing up for OPay.",
            primaryText = "Got it",
            onPrimary = { showHowTo = false },
            onDismiss = { showHowTo = false }
        )
    }
    if (showLeave) {
        OPayDialog(
            title = "Are you sure you want to leave?",
            message = "Invite 1 more friend to get ₦1,800",
            primaryText = "Invite Now",
            onPrimary = {
                showLeave = false
                shareInvite(context, code)
            },
            onDismiss = { showLeave = false },
            secondaryText = "Maybe Later",
            onSecondary = {
                showLeave = false
                onBack()
            }
        )
    }
}

private val RULES = listOf(
    "Invite friends who have not used OPay or have been inactive for over 180 days.",
    "Your friend must sign up with your invitation code.",
    "Your friend must transact ₦100 or more during the validity period.",
    "Rewards are credited to your wallet once a milestone is reached.",
    "This is a demo. No real rewards are paid."
)

private fun invitationCode(phone: String, accountNumber: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.startsWith("234") && digits.length > 3 -> digits.drop(3)
        digits.startsWith("0") && digits.length > 1 -> digits.drop(1)
        digits.isNotEmpty() -> digits
        else -> accountNumber
    }
}

private fun shareInvite(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join me on OPay with my invitation code $code")
    }
    context.startActivity(
        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun highlighted(
    before: String,
    highlight: String,
    after: String,
    color: Color
): AnnotatedString = buildAnnotatedString {
    append(before)
    withStyle(SpanStyle(color = color)) { append(highlight) }
    append(after)
}

private fun countdownText(remainingMillis: Long): String {
    val totalSeconds = remainingMillis / 1000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d days %02d:%02d:%02d expired", days, hours, minutes, seconds)
}

@Composable
private fun BanknoteGraphic() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val noteLeft = w * 0.18f
            val noteTop = h * 0.10f
            val noteWidth = w * 0.64f
            val noteHeight = h * 0.66f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(NoteTop, NoteBottom),
                    start = Offset(noteLeft, noteTop),
                    end = Offset(noteLeft + noteWidth, noteTop + noteHeight)
                ),
                topLeft = Offset(noteLeft, noteTop),
                size = Size(noteWidth, noteHeight),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            val inset = 8.dp.toPx()
            drawRoundRect(
                color = NoteText.copy(alpha = 0.5f),
                topLeft = Offset(noteLeft + inset, noteTop + inset),
                size = Size(noteWidth - inset * 2f, noteHeight - inset * 2f),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = NoteText.copy(alpha = 0.35f),
                radius = noteHeight * 0.12f,
                center = Offset(noteLeft + noteWidth * 0.14f, noteTop + noteHeight / 2f)
            )
            drawCircle(
                color = NoteText.copy(alpha = 0.35f),
                radius = noteHeight * 0.12f,
                center = Offset(noteLeft + noteWidth * 0.86f, noteTop + noteHeight / 2f)
            )

            val handWidth = w * 0.16f
            val handTop = h * 0.56f
            val handHeight = h * 0.30f
            val leftX = noteLeft - handWidth * 0.25f
            val rightX = noteLeft + noteWidth - handWidth * 0.75f
            for (x in listOf(leftX, rightX)) {
                drawRoundRect(
                    color = Skin,
                    topLeft = Offset(x, handTop),
                    size = Size(handWidth, handHeight),
                    cornerRadius = CornerRadius(handWidth * 0.4f)
                )
                drawRoundRect(
                    color = Sleeve,
                    topLeft = Offset(x, handTop + handHeight * 0.7f),
                    size = Size(handWidth, h - handTop - handHeight * 0.7f),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
            }
            drawOval(
                color = Skin,
                topLeft = Offset(leftX + handWidth * 0.55f, handTop - handHeight * 0.18f),
                size = Size(handWidth * 0.5f, handHeight * 0.42f)
            )
            drawOval(
                color = Skin,
                topLeft = Offset(rightX - handWidth * 0.05f, handTop - handHeight * 0.18f),
                size = Size(handWidth * 0.5f, handHeight * 0.42f)
            )
        }
        Text(
            text = "₦6,300",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = NoteText
        )
    }
}

@Composable
private fun StepsCard(onEarn: () -> Unit) {
    OPayCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            InviteStep(
                modifier = Modifier.weight(1f),
                icon = OPayIcon.Share,
                label = "Share invitation link/code with friends"
            )
            StepConnector()
            InviteStep(
                modifier = Modifier.weight(1f),
                icon = OPayIcon.Zap,
                label = "Friend transacts ₦100 or more during the validity period"
            )
            StepConnector()
            InviteStep(
                modifier = Modifier.weight(1f),
                icon = OPayIcon.Gift,
                label = "You'll receive rewards in your wallet"
            )
        }
        Spacer(Modifier.height(14.dp))
        OPayButton(text = "Earn ₦6,300", onClick = onEarn)
    }
}

@Composable
private fun StepConnector() {
    Box(
        modifier = Modifier
            .width(14.dp)
            .padding(top = 20.dp)
            .height(1.dp)
            .background(OPay.colors.divider)
    )
}

@Composable
private fun InviteStep(icon: OPayIcon, label: String, modifier: Modifier = Modifier) {
    val c = OPay.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(c.cardAlt, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 20.dp, tint = c.green)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = OPayType.captionSmall,
            color = c.muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CodeCard(
    code: String,
    boxColor: Color,
    accent: Color,
    onCopy: () -> Unit,
    onHowTo: () -> Unit
) {
    val c = OPay.colors
    OPayCard {
        Text(
            text = "Share Invitation Code",
            style = OPayType.body,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(boxColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = code,
                style = OPayType.headline.copy(fontSize = 20.sp),
                color = c.text,
                modifier = Modifier.weight(1f)
            )
            OPayChip(text = "Copy", selected = true, onClick = onCopy)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "How to use invitation code?",
            style = OPayType.bodySmall,
            color = accent,
            modifier = Modifier.clickable(onClick = onHowTo)
        )
    }
}

@Composable
private fun MilestonesCard(
    remainingMillis: Long,
    accent: Color,
    onInvite: () -> Unit,
    onRecord: () -> Unit
) {
    val c = OPay.colors
    OPayCard {
        Text(
            text = countdownText(remainingMillis),
            style = OPayType.caption,
            fontWeight = FontWeight.Bold,
            color = c.warn,
            modifier = Modifier
                .background(c.warn.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Milestone(
                modifier = Modifier.weight(1f),
                amount = "₦1,800",
                active = true,
                onInvite = onInvite
            )
            MilestoneConnector()
            Milestone(
                modifier = Modifier.weight(1f),
                amount = "₦2,000",
                active = false,
                onInvite = onInvite
            )
            MilestoneConnector()
            Milestone(
                modifier = Modifier.weight(1f),
                amount = "₦2,500",
                active = false,
                onInvite = onInvite
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = highlighted(
                before = "Invite 1 more person, you can get ",
                highlight = "₦1,800",
                after = "",
                color = accent
            ),
            style = OPayType.bodySmall,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OPayRowDivider()
        OPayRow(title = "Total Earned: ₦0", showChevron = false)
        OPayRowDivider()
        OPayRow(title = "Invitation Record", onClick = onRecord)
    }
}

@Composable
private fun MilestoneConnector() {
    Box(
        modifier = Modifier
            .width(14.dp)
            .padding(top = 28.dp)
            .height(1.dp)
            .background(OPay.colors.divider)
    )
}

@Composable
private fun Milestone(
    amount: String,
    active: Boolean,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(2.dp, if (active) c.green else c.cardAlt, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = amount,
                style = OPayType.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (active) c.green else c.muted
            )
        }
        Spacer(Modifier.height(8.dp))
        if (active) {
            Text(
                text = "Invite Now",
                style = OPayType.caption,
                fontWeight = FontWeight.Bold,
                color = c.onGreen,
                modifier = Modifier
                    .background(c.green, RoundedCornerShape(14.dp))
                    .clickable(onClick = onInvite)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        } else {
            Text(text = "To be invited", style = OPayType.caption, color = c.muted)
        }
    }
}

@Composable
private fun CelebrationDialog(onInvite: () -> Unit, onDismiss: () -> Unit) {
    val c = OPay.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        RemoveWindowDim()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OverlayScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .background(c.card, RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "A new opportunity to earn rewards",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "₦6,300",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.green
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Invite new friends to sign up and get up to ₦6,300 cash.",
                    style = OPayType.body,
                    color = c.muted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                OPayButton(text = "Invite Now!", onClick = onInvite)
            }
        }
    }
}
