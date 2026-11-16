package com.westly.opay.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.navigation.Routes

internal const val NOT_AVAILABLE_TOAST = ""

private val NewTagColor = Color(0xFFFF4D8D)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val toast = rememberOPayToast()
    val versionName = remember(context) { appVersionName(context) }
    var signOutOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            OPayTopBar(
                title = "Settings",
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
                    OPayRow(
                        title = "My Profile",
                        onClick = { onNavigate(Routes.Profile) },
                        icon = OPayIcon.User
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Payment Settings",
                        onClick = { onNavigate(Routes.PaymentSettings) },
                        icon = OPayIcon.Lock
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Login Settings",
                        onClick = { onNavigate(Routes.LoginSettings) },
                        icon = OPayIcon.Key
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Savings Settings",
                        onClick = { onNavigate(Routes.SavingsSettings) },
                        icon = OPayIcon.Save
                    )
                }
                OPayCard {
                    OPayRow(
                        title = "Homepage Settings",
                        onClick = { toast.show(NOT_AVAILABLE_TOAST) },
                        icon = OPayIcon.Home
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Security Questions",
                        onClick = { onNavigate(Routes.SecurityQuestions) },
                        icon = OPayIcon.Loan,
                        trailing = {
                            Text(
                                text = "Reset",
                                style = OPayType.caption,
                                color = c.green
                            )
                        }
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "SMS Alert Settings",
                        onClick = { onNavigate(Routes.SmsAlerts) },
                        icon = OPayIcon.Chat
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Security Plus",
                        onClick = { onNavigate(Routes.SecurityPlus) },
                        icon = OPayIcon.Loan
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Access to Clipboard",
                        onClick = { toast.show(NOT_AVAILABLE_TOAST) },
                        icon = OPayIcon.Doc
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Themes",
                        onClick = { onNavigate(Routes.Themes) },
                        icon = OPayIcon.Star
                    )
                }
                OPayCard {
                    OPayRow(
                        title = "Security Center",
                        onClick = { onNavigate(Routes.SecurityCenter) },
                        icon = OPayIcon.Loan
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Feedback and Suggestions",
                        onClick = { toast.show(NOT_AVAILABLE_TOAST) },
                        icon = OPayIcon.Doc
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Close Account",
                        onClick = { onNavigate(Routes.CloseAccount) },
                        icon = OPayIcon.Lock
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "About",
                        onClick = { onNavigate(Routes.About) },
                        icon = OPayIcon.Help,
                        value = versionName,
                        trailing = { PinkTag(text = "New") }
                    )
                }
                OPayCard {
                    Text(
                        text = "Sign Out",
                        style = OPayType.title,
                        color = c.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { signOutOpen = true }
                            .padding(vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (signOutOpen) {
            OPayDialog(
                title = "Are you sure to log out of OPay?",
                message = null,
                primaryText = "Yes",
                onPrimary = {
                    signOutOpen = false
                    onSignedOut()
                },
                onDismiss = { signOutOpen = false },
                secondaryText = "No",
                onSecondary = { signOutOpen = false }
            )
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
internal fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = OPayType.body,
        fontWeight = FontWeight.SemiBold,
        color = OPay.colors.text,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
    )
}

@Composable
internal fun PinkTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(OPayShapes.chip)
            .background(NewTagColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Suppress("DEPRECATION")
private fun appVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
