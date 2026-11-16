package com.westly.opay.navigation

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayGlyph
import com.westly.opay.core.ui.icons.OPayGlyphView
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.incomingTransferMessage

private data class TabItem(val route: String, val label: String, val icon: OPayIcon)

private val tabItems = listOf(
    TabItem(Routes.Home, "Home", OPayIcon.Home),
    TabItem(Routes.Rewards, "Rewards", OPayIcon.Gift),
    TabItem(Routes.Wealth, "Wealth", OPayIcon.Chart),
    TabItem(Routes.Cards, "Cards", OPayIcon.Card),
    TabItem(Routes.Me, "Me", OPayIcon.Me)
)

/**
 * [exitOnBackTwice] is true in the OPay app itself (Back twice on Home leaves the app). Inside the Dell it is false,
 * so Back on Home goes to the Dell, which closes the OPay window instead of closing the whole app.
 */
@Composable
fun MainScaffold(exitOnBackTwice: Boolean = true) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBar = route in Routes.tabRoutes
    val toast = rememberOPayToast()
    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    val repository = (context.applicationContext as OPayApplication).repository

    LaunchedEffect(repository) {
        repository.incomingTransfers.collect { toast.show(incomingTransferMessage(it)) }
    }
    LaunchedEffect(repository) {
        repository.settingMessages.collect { toast.show(it) }
    }

    BackHandler(enabled = exitOnBackTwice && route == Routes.Home) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPress <= 2000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPress = now
            toast.show("Click again to exit")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OPay.colors.bg)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AppNavHost(navController = navController)
            }
            if (showBar) {
                BottomBar(navController = navController, currentRoute = route)
            }
        }
        OPayToastHost(state = toast, bottomPadding = 110.dp)
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    val c = OPay.colors
    val barColor = if (c.bg.luminance() < 0.5f) Color(0xFF121214) else c.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .navigationBarsPadding()
    ) {
        tabItems.forEach { item ->
            val selected = item.route == currentRoute
            val tint = if (selected) c.green else c.muted
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(Routes.Home) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TabIcon(route = item.route, fallback = item.icon, selected = selected, tint = tint)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.label,
                    style = OPayType.caption,
                    fontSize = 9.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TabIcon(route: String, fallback: OPayIcon, selected: Boolean, tint: Color) {
    when {
        route == Routes.Home && selected ->
            OPayGlyphView(glyph = OPayGlyph.HomeFilled, size = 24.dp, tint = tint)
        route == Routes.Wealth ->
            OPayGlyphView(glyph = OPayGlyph.TabWealth, size = 24.dp, tint = tint)
        route == Routes.Me ->
            OPayGlyphView(glyph = OPayGlyph.TabMe, size = 24.dp, tint = tint)
        else ->
            OPayIconView(icon = fallback, size = 24.dp, tint = tint)
    }
}
