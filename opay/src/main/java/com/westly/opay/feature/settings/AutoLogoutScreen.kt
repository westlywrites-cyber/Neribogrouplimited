package com.westly.opay.feature.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast

/**
 * Auto-logout (reference D4): three rows with a radio mark. Tapping a row saves it at once, so there is no
 * Confirm button. The text on the Auto-logout row of Login Settings follows when this screen is left.
 */
@Composable
fun AutoLogoutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val viewModel = rememberAutoLogoutViewModel()
    val state by viewModel.state.collectAsState()
    val toast = rememberOPayToast()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AutoLogoutEvent.ShowToast -> toast.show(event.message)
            }
        }
    }
    LaunchedEffect(viewModel) { viewModel.onEntered() }

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
                title = "Auto-logout Setting",
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
                Spacer(Modifier.height(8.dp))
                AutoLogoutOptions.all.forEach { option ->
                    AutoLogoutRow(
                        option = option,
                        selected = state.selected == option.mode,
                        onClick = { viewModel.select(option.mode) }
                    )
                }
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun rememberAutoLogoutViewModel(): AutoLogoutViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { AutoLogoutViewModel.factory(application) }
    return viewModel<AutoLogoutViewModel>(factory = factory)
}

@Composable
private fun AutoLogoutRow(
    option: AutoLogoutOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(c.card)
            .border(1.5.dp, if (selected) c.green else Color.Transparent, OPayShapes.card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = OPayType.body,
                fontWeight = FontWeight.SemiBold,
                color = c.text
            )
            Text(
                text = option.description,
                style = OPayType.caption,
                color = c.muted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) c.green else Color.Transparent)
                .border(1.5.dp, if (selected) c.green else c.muted, CircleShape)
        )
    }
}
