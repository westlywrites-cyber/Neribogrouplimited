package com.westly.opay.feature.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView

private val FLAG_GREEN = Color(0xFF0A8D4B)

/** Registration step 2 of 2, second page: nationality, fixed to Nigeria (reference B7). */
@Composable
fun RegisterNationalityScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberRegistrationViewModel()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        OPayTopBar(
            title = "",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp),
            actionText = "Help",
            onAction = onHelp
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "What is your Nationality?",
                style = OPayType.headline,
                color = c.text
            )
            Text(
                text = "Step 2 of 2",
                style = OPayType.caption,
                color = c.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.greenSoft)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Please select the country you are from",
                    style = OPayType.bodySmall,
                    color = c.green
                )
            }
            OPayCard {
                Text(
                    text = "Country / Region",
                    style = OPayType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NigeriaFlag()
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = viewModel.nationality,
                        style = OPayType.titleSmall,
                        color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                    OPayIconView(icon = OPayIcon.Check, size = 20.dp, tint = c.green)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OPayButton(text = "Next", onClick = onNext)
        }
    }
}

@Composable
private fun NigeriaFlag() {
    Row(
        modifier = Modifier
            .size(width = 24.dp, height = 16.dp)
            .clip(RoundedCornerShape(2.dp))
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(FLAG_GREEN)
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.White)
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(FLAG_GREEN)
        )
    }
}
