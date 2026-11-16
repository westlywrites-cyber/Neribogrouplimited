package com.westly.opay.feature.notifications

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.EmptyState
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.model.AppNotification
import com.westly.opay.navigation.Routes
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private val Months = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)
private val NotificationZone: ZoneId = ZoneId.of("Africa/Lagos")
private const val NOTHING_MORE = "Nothing more to show"

private fun formatNotificationDate(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(NotificationZone)
    return Months[t.monthValue - 1] + " " + t.dayOfMonth + ", " +
        String.format(Locale.US, "%02d:%02d:%02d", t.hour, t.minute, t.second)
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = viewModel<NotificationsViewModel>(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val c = OPay.colors
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val newIds by viewModel.newIds.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    DisposableEffect(viewModel) {
        viewModel.open()
        onDispose { viewModel.close() }
    }

    val visible = notificationsForTab(notifications, NotificationTabs[tab])

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
                title = "Notifications",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotificationTabs.forEachIndexed { index, label ->
                    OPayChip(text = label, selected = tab == index, onClick = { tab = index })
                }
            }
            if (visible.isEmpty()) {
                EmptyState(message = "No notifications")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = 16.dp
                    )
                ) {
                    items(visible, key = { it.id }) { item ->
                        NotificationCard(
                            item = item,
                            unread = item.id in newIds,
                            onView = {
                                when {
                                    item.title.startsWith("OWealth") -> onNavigate(Routes.Owealth)
                                    item.title.startsWith("SafeBox") -> onNavigate(Routes.SafeBox)
                                    else -> toast.show(NOTHING_MORE)
                                }
                            }
                        )
                    }
                }
            }
        }
        OPayToastHost(state = toast, bottomPadding = 24.dp)
    }
}

@Composable
private fun NotificationCard(item: AppNotification, unread: Boolean, onView: () -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(c.card)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (unread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(c.danger, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = item.title,
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
        }
        Text(
            text = item.body,
            style = OPayType.body,
            color = c.text,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatNotificationDate(item.timestampMillis),
                style = OPayType.caption,
                color = c.muted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "View",
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = c.green,
                modifier = Modifier
                    .clickable(onClick = onView)
                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
            )
        }
    }
}
