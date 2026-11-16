package com.westly.opay.feature.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPayTheme
import com.westly.opay.core.theme.ThemeMode
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import com.westly.opay.data.model.UserProfile

private val PreviewTransaction = Transaction(
    id = "preview",
    type = TxType.TransferOut,
    title = "Transfer to ADA OKAFOR",
    counterpartyName = "ADA OKAFOR",
    counterpartyBank = "OPay",
    counterpartyAccount = "8012345678",
    amountKobo = 50_000L,
    timestampMillis = 1_793_473_974_000L,
    status = "Successful",
    txNo = "000000000000000000000000",
    sessionId = "000000000000000000000000000000",
    category = null,
    remark = null
)

private val PreviewProfile = UserProfile(
    id = "preview",
    fullName = "John David Johnson",
    nickname = "John",
    phone = "8051234703",
    accountNumber = "8051234703",
    tier = 1,
    emailMasked = "j***@example.com",
    gender = "Male",
    dobMasked = "**/**/****"
)

private val PreviewAirtime = PreviewTransaction.copy(
    type = TxType.Airtime,
    title = "Glo Airtime",
    counterpartyName = "08000000000",
    counterpartyBank = null,
    counterpartyAccount = null,
    amountKobo = -5_000L
)

@Composable
private fun ReceiptPreviewContent(mode: ThemeMode, transaction: Transaction = PreviewTransaction) {
    OPayTheme(mode = mode) {
        Box(
            modifier = Modifier
                .background(receiptColors().page)
                .padding(16.dp)
        ) {
            DefaultReceiptCard(transaction = transaction, profile = PreviewProfile)
        }
    }
}

@Preview(name = "Receipt dark", widthDp = 360, heightDp = 560)
@Composable
private fun ReceiptDarkPreview() {
    ReceiptPreviewContent(ThemeMode.Dark)
}

@Preview(name = "Receipt light", widthDp = 360, heightDp = 560)
@Composable
private fun ReceiptLightPreview() {
    ReceiptPreviewContent(ThemeMode.Light)
}

@Preview(name = "Airtime receipt dark", widthDp = 360, heightDp = 560)
@Composable
private fun AirtimeDarkPreview() {
    ReceiptPreviewContent(ThemeMode.Dark, PreviewAirtime)
}

@Preview(name = "Airtime receipt light", widthDp = 360, heightDp = 560)
@Composable
private fun AirtimeLightPreview() {
    ReceiptPreviewContent(ThemeMode.Light, PreviewAirtime)
}
