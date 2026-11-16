package com.westly.opay.feature.me

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val NOT_AVAILABLE_MESSAGE = ""

private val PageTop = Color(0xFFD3E6FF)
private val PageBottom = Color(0xFFF3F8FF)
private val Navy = Color(0xFF0E2A5C)
private val InkMuted = Color(0xFF5F7194)
private val CardBorder = Color(0xFFC9DCF7)
private val GreenTile = Color(0xFFDDF5EA)
private val GreenAccent = Color(0xFF14A06F)
private val BlueTile = Color(0xFFDCE9FF)
private val BlueAccent = Color(0xFF2F6BD6)

@Composable
internal fun SystemBarIcons(darkIcons: Boolean) {
    val activity = LocalContext.current as ComponentActivity
    val view = LocalView.current
    DisposableEffect(darkIcons) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

@Composable
fun OJuniorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toast = rememberOPayToast()
    SystemBarIcons(darkIcons = true)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PageTop, PageBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 14.dp)
                    .size(32.dp)
                    .semantics { contentDescription = "Back"; role = Role.Button }
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Back, size = 24.dp, tint = Navy)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Create an OPay account for your child",
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy,
                    modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
                )
                JuniorCard(title = "With OPay, they can enjoy:") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GreenTile)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(GreenAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            OPayIconView(icon = OPayIcon.Check, size = 14.dp, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Up to 18% interest on savings",
                            style = OPayType.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BenefitTile(
                            icon = OPayIcon.Zap,
                            label = "Free transfers",
                            modifier = Modifier.weight(1f)
                        )
                        BenefitTile(
                            icon = OPayIcon.Gift,
                            label = "Cashback rewards",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                JuniorCard(title = "Before you start, make sure:") {
                    Bullet {
                        Text(
                            text = "They have an active phone no.",
                            style = OPayType.body,
                            color = Navy
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Bullet {
                        Column {
                            Text(
                                text = "They have a NIN or BVN.",
                                style = OPayType.body,
                                color = Navy
                            )
                            Text(
                                text = "They don't have a NIN? >",
                                style = OPayType.body,
                                color = GreenAccent,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable { toast.show(NOT_AVAILABLE_MESSAGE) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Bullet {
                        Text(
                            text = "They are with you to perform a facial verification",
                            style = OPayType.body,
                            color = Navy
                        )
                    }
                    Text(
                        text = "*They'll have full control of the account after creation",
                        style = OPayType.caption,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(OPayShapes.pill)
                        .background(Navy)
                        .clickable { toast.show(NOT_AVAILABLE_MESSAGE) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create Account for Them",
                        style = OPayType.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun JuniorCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = OPayType.title,
            fontWeight = FontWeight.Bold,
            color = Navy,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun BenefitTile(
    icon: OPayIcon,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BlueTile)
            .padding(14.dp)
    ) {
        OPayIconView(icon = icon, size = 24.dp, tint = BlueAccent)
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = OPayType.body,
            fontWeight = FontWeight.Bold,
            color = Navy
        )
    }
}

@Composable
private fun Bullet(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(Navy, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        content()
    }
}
