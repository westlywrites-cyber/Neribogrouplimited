package com.westly.opay.feature.addmoney

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.transfer.groupAccountNumber

private const val COPIED = "Copied"
private const val COPIED_OPEN_BANK = "Copied. Open your bank app to continue"
private const val NOT_AVAILABLE = ""

private data class BankLogo(val name: String, val letter: String, val color: Color)

private val BankLogos = listOf(
    BankLogo("Moniepoint", "M", Color(0xFF2F6BFF)),
    BankLogo("Kuda", "K", Color(0xFF6B3FD1)),
    BankLogo("GTBank", "G", Color(0xFFE8590C)),
    BankLogo("Access", "A", Color(0xFFE8730C)),
    BankLogo("UBA", "U", Color(0xFFD62828))
)

@Composable
fun AddMoneyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val profile by repository.profile.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()
    val digits = profile.accountNumber

    fun copyNumber(message: String) {
        clipboard.setText(AnnotatedString(digits))
        toast.show(message)
    }

    fun shareNumber() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "My OPay account number is $digits")
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
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
        ) {
            OPayTopBar(
                title = "Add Money",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bank Transfer",
                                style = OPayType.body,
                                fontWeight = FontWeight.Bold,
                                color = c.text
                            )
                            Text(
                                text = "Add money via mobile or internet banking",
                                style = OPayType.caption,
                                color = c.muted
                            )
                        }
                        OPayIconView(icon = OPayIcon.Bank, size = 24.dp, tint = c.green)
                    }
                    Text(
                        text = "OPay Account Number",
                        style = OPayType.body,
                        color = c.muted,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = groupAccountNumber(digits),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OPayButton(
                            text = "Copy",
                            onClick = { copyNumber(COPIED) },
                            modifier = Modifier.weight(1f),
                            style = ButtonStyle.Dark
                        )
                        OPayButton(
                            text = "Share",
                            onClick = { shareNumber() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Tap bank to copy & open APP",
                        style = OPayType.caption,
                        color = c.muted,
                        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BankLogos.forEach { bank ->
                            Column(
                                modifier = Modifier.clickable { copyNumber(COPIED_OPEN_BANK) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(bank.color, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = bank.letter,
                                        style = OPayType.body,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = bank.name,
                                    style = OPayType.captionSmall,
                                    color = c.muted,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
                SectionCard {
                    MethodRow(
                        icon = OPayIcon.Card,
                        title = "Auto Top-Up (Powered by OPay)",
                        subtitle = "Never worry about your balance. We'll top up your\u2026",
                        onClick = { toast.show(NOT_AVAILABLE) }
                    )
                    OPayRowDivider()
                    MethodRow(
                        icon = OPayIcon.Bag,
                        title = "Cash Deposit",
                        subtitle = "Fund your account with nearby merchants",
                        onClick = { toast.show(NOT_AVAILABLE) }
                    )
                    OPayRowDivider()
                    MethodRow(
                        icon = OPayIcon.Card,
                        title = "Top-up with Card/Account",
                        subtitle = "Add money directly from your bank card or account",
                        onClick = { toast.show(NOT_AVAILABLE) }
                    )
                }
            }
        }
        OPayToastHost(state = toast, bottomPadding = 24.dp)
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
private fun MethodRow(icon: OPayIcon, title: String, subtitle: String, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OPayIconView(icon = icon, size = 20.dp, tint = c.green)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = OPayType.body, color = c.text)
            Text(text = subtitle, style = OPayType.caption, color = c.muted)
        }
        Spacer(Modifier.width(8.dp))
        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
    }
}
