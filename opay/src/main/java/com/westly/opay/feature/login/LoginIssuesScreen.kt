package com.westly.opay.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon

/** C4: the ways out of a login problem. Reached from the login password page and from the phone page. */
@Composable
fun LoginIssuesScreen(
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onChangePhone: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        OPayTopBar(
            title = "Login Issues",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OPayCard {
                OPayRow(
                    title = "Forgot login password",
                    icon = OPayIcon.Key,
                    onClick = onForgotPassword
                )
                OPayRowDivider()
                OPayRow(
                    title = "Change login phone number",
                    icon = OPayIcon.Phone,
                    onClick = onChangePhone
                )
                OPayRowDivider()
                OPayRow(
                    title = "Contact support",
                    icon = OPayIcon.Support,
                    onClick = onContactSupport
                )
            }
        }
    }
}
