package com.westly.opay.feature.bills

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayToggle
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast

private const val DIALLER_UNAVAILABLE_MESSAGE = "Dialler not available"

private data class QuickPayment(
    val icon: OPayIcon,
    val title: String,
    val code: String,
    val dial: String
)

private data class NetworkCode(val title: String, val code: String)

private data class NetworkCodes(val name: String, val codes: List<NetworkCode>)

private val QuickPayments = listOf(
    QuickPayment(OPayIcon.Bank, "Transfer to Bank Account", "*955*2*amount*10 digit account no.#", "*955*2#"),
    QuickPayment(OPayIcon.User, "Transfer to OPay Account", "*955*1*amount*10 digit account no.#", "*955*1#"),
    QuickPayment(OPayIcon.Phone, "Airtime for Self", "*955*3*amount#", "*955*3#"),
    QuickPayment(OPayIcon.Phone, "Airtime for Others", "*955*3*amount*mobile no.#", "*955*3#"),
    QuickPayment(OPayIcon.Data, "Data", "*955*4*mobile no.#", "*955*4#"),
    QuickPayment(OPayIcon.Game, "Betting", "*955*5#", "*955*5#"),
    QuickPayment(OPayIcon.Zap, "Electricity", "*955*6#", "*955*6#")
)

private val NetworkLists = listOf(
    NetworkCodes(
        "MTN",
        listOf(
            NetworkCode("Check mobile phone number", "*663#"),
            NetworkCode("Check airtime balance", "*310#"),
            NetworkCode("Check data balance", "*323#")
        )
    ),
    NetworkCodes(
        "GLO",
        listOf(
            NetworkCode("Check mobile phone number", "*777#"),
            NetworkCode("Check airtime balance", "*124#"),
            NetworkCode("Check data balance", "*127*0#")
        )
    ),
    NetworkCodes(
        "Airtel",
        listOf(
            NetworkCode("Check mobile phone number", "*121*1#"),
            NetworkCode("Check airtime balance", "*123#"),
            NetworkCode("Check data balance", "*140#")
        )
    ),
    NetworkCodes(
        "9mobile",
        listOf(
            NetworkCode("Check mobile phone number", "*248#"),
            NetworkCode("Check airtime balance", "*232#"),
            NetworkCode("Check data balance", "*228#")
        )
    )
)

private fun openDialler(context: Context, code: String): Boolean {
    val uri = Uri.parse("tel:" + code.replace("#", "%23"))
    return try {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
        true
    } catch (missing: ActivityNotFoundException) {
        false
    }
}

@Composable
fun UssdScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val toast = rememberOPayToast()
    var allowUssd by rememberSaveable { mutableStateOf(true) }

    fun dial(code: String) {
        if (!openDialler(context, code)) {
            toast.show(DIALLER_UNAVAILABLE_MESSAGE)
        }
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
                title = "USSD Codes",
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
                    Text(
                        text = "Quick Payments with OPay USSD",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    Text(
                        text = "*955#",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                    Text(
                        text = "Each dial (excluding, OTP, airtime and data codes) will incur a \u20A610 fee",
                        style = OPayType.caption,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 8.dp)
                    )
                    OPayRowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow USSD Transactions",
                                style = OPayType.body,
                                color = c.text
                            )
                            Text(
                                text = "You can send money and pay bills via USSD",
                                style = OPayType.caption,
                                color = c.muted
                            )
                        }
                        OPayToggle(checked = allowUssd, onCheckedChange = { allowUssd = it })
                    }
                    QuickPayments.forEach { item ->
                        OPayRowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dial(item.dial) }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OPayIconView(icon = item.icon, size = 18.dp, tint = c.green)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, style = OPayType.body, color = c.text)
                                Text(text = item.code, style = OPayType.caption, color = c.muted)
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c.green),
                                contentAlignment = Alignment.Center
                            ) {
                                OPayIconView(icon = OPayIcon.Phone, size = 16.dp, tint = c.onGreen)
                            }
                        }
                    }
                }
                NetworkLists.forEach { network ->
                    OPayCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NetworkLogo(name = network.name, size = 28.dp)
                            Text(
                                text = network.name,
                                style = OPayType.body,
                                fontWeight = FontWeight.Bold,
                                color = c.text
                            )
                        }
                        network.codes.forEach { entry ->
                            OPayRowDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dial(entry.code) }
                                    .padding(vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = entry.title, style = OPayType.body, color = c.text)
                                    Text(text = entry.code, style = OPayType.caption, color = c.muted)
                                }
                                OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}
