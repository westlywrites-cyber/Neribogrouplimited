package com.westly.neribogroup.feature.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.PlanEvent
import com.westly.neribogroup.data.PlanHistory
import com.westly.neribogroup.ui.formatFullDate

/** Read-only list of what happened to one user's paid plan, newest first. */
@Composable
fun PlanHistoryDialog(user: AppUser, history: List<PlanEvent>, onClose: () -> Unit) {
    val events = PlanHistory.forUser(history, user.id)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Plan history") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = user.name, style = MaterialTheme.typography.titleSmall)
                if (events.isEmpty()) {
                    Text(
                        text = "Nothing recorded for this user yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                for (e in events) {
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(text = PlanHistory.label(e), style = MaterialTheme.typography.bodyMedium)
                        val end = if (e.endMillis > 0L) " • ends " + formatFullDate(e.endMillis) else ""
                        Text(
                            text = formatFullDate(e.atMillis) + end,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
