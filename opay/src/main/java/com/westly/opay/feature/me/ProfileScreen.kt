package com.westly.opay.feature.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.OverlayScrim
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.navigation.Routes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val NICKNAME_MAX_LENGTH = 20
private const val NOT_AVAILABLE_MESSAGE = ""

internal fun groupAccountNumber(account: String): String =
    if (account.length == 10) {
        account.take(3) + " " + account.substring(3, 6) + " " + account.substring(6)
    } else {
        account
    }

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val profile by repository.profile.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val toast = rememberOPayToast()
    var nicknameOpen by remember { mutableStateOf(false) }

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
                title = "My Profile",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                OPayCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clickable { toast.show(NOT_AVAILABLE_MESSAGE) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(c.cardAlt),
                                contentAlignment = Alignment.Center
                            ) {
                                OPayIconView(icon = OPayIcon.User, size = 40.dp, tint = c.muted)
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(c.green),
                                contentAlignment = Alignment.Center
                            ) {
                                CameraIcon(tint = c.onGreen)
                            }
                        }
                        Text(
                            text = profile.nickname,
                            style = OPayType.title,
                            fontWeight = FontWeight.Bold,
                            color = c.text,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
                OPayCard {
                    ProfileRow(
                        label = "OPay Account Number",
                        value = groupAccountNumber(profile.accountNumber),
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .semantics { contentDescription = "Copy account number"; role = Role.Button }
                                    .clickable {
                                        clipboard.setText(AnnotatedString(profile.accountNumber))
                                        toast.show("Copied")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CopyIcon(tint = c.green)
                            }
                        }
                    )
                    OPayRowDivider()
                    ProfileRow(
                        label = "Account Tier",
                        onClick = { onNavigate(Routes.AccountLimits) },
                        valueContent = { TierChip(tier = profile.tier) },
                        showChevron = true
                    )
                    if (profile.fullName.isNotBlank()) {
                        OPayRowDivider()
                        ProfileRow(label = "Full Name", value = profile.fullName)
                    }
                    OPayRowDivider()
                    ProfileRow(label = "Mobile Number", value = profile.phone)
                    OPayRowDivider()
                    ProfileRow(
                        label = "Nickname",
                        value = profile.nickname,
                        onClick = { nicknameOpen = true },
                        showChevron = true
                    )
                    if (profile.gender.isNotBlank()) {
                        OPayRowDivider()
                        ProfileRow(label = "Gender", value = profile.gender)
                    }
                    if (profile.dobMasked.isNotBlank()) {
                        OPayRowDivider()
                        ProfileRow(label = "Date of Birth", value = profile.dobMasked)
                    }
                    if (profile.emailMasked.isNotBlank()) {
                        OPayRowDivider()
                        ProfileRow(label = "Email", value = profile.emailMasked)
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
        if (nicknameOpen) {
            NicknameDialog(
                initial = profile.nickname,
                onSave = { value ->
                    repository.setNickname(value)
                    nicknameOpen = false
                },
                onDismiss = { nicknameOpen = false }
            )
        }
    }
}

@Composable
private fun ProfileRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    valueContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = OPay.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, style = OPayType.body, color = c.muted)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (value != null) {
                Text(
                    text = value,
                    style = OPayType.body,
                    color = c.text,
                    textAlign = TextAlign.End
                )
            }
            valueContent?.invoke()
        }
        trailing?.invoke()
        if (showChevron) {
            OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
        }
    }
}

@Composable
private fun NicknameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = OPay.colors
    var draft by remember { mutableStateOf(initial) }
    val canSave = draft.trim().isNotEmpty()
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "Nickname",
                    style = OPayType.title,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                OPayUnderlineField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "Enter nickname",
                    modifier = Modifier.padding(top = 12.dp),
                    maxLength = NICKNAME_MAX_LENGTH
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OPayButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.Dark
                    )
                    OPayButton(
                        text = "Save",
                        onClick = { onSave(draft) },
                        modifier = Modifier.weight(1f),
                        enabled = canSave
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(8f * unit, 8f * unit),
            size = Size(12f * unit, 12f * unit),
            cornerRadius = CornerRadius(2f * unit, 2f * unit),
            style = stroke
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(4f * unit, 4f * unit),
            size = Size(12f * unit, 12f * unit),
            cornerRadius = CornerRadius(2f * unit, 2f * unit),
            style = stroke
        )
    }
}

@Composable
private fun CameraIcon(tint: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2.2f * unit, cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(2f * unit, 6f * unit),
            size = Size(20f * unit, 14f * unit),
            cornerRadius = CornerRadius(3f * unit, 3f * unit),
            style = stroke
        )
        drawCircle(
            color = tint,
            radius = 3.6f * unit,
            center = Offset(12f * unit, 13f * unit),
            style = stroke
        )
    }
}
