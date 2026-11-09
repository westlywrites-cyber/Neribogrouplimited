package com.westly.neribogroup.feature.appdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.PlanEvent
import com.westly.neribogroup.feature.common.SearchField
import com.westly.neribogroup.feature.common.matchesQuery
import com.westly.neribogroup.ui.formatCount

/**
 * True when [query] matches the user's name, email, phone or plan.
 * A phone search also works with digits only, so "0803 123" finds "+234 803 123 ...".
 */
private fun userMatches(query: String, user: AppUser): Boolean {
    if (matchesQuery(query, user.name, user.email, user.phone, planText(user.plan))) return true
    val digits = query.filter { it.isDigit() }
    if (digits.length < 3) return false
    val phoneDigits = user.phone.filter { it.isDigit() }
    if (phoneDigits.isEmpty()) return false
    return phoneDigits.contains(digits) ||
        (digits.startsWith("0") && phoneDigits.contains(digits.drop(1)))
}

/**
 * The full list of an app's users on its own page: every user (newest first) with a search box.
 * Opened from the "App users" card on the app dashboard. Back returns to the dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsersScreen(
    appName: String,
    users: List<AppUser>,
    onBack: () -> Unit,
    planHistory: List<PlanEvent> = emptyList()
) {
    var historyUserId by rememberSaveable { mutableStateOf<String?>(null) }
    val historyUser = users.firstOrNull { it.id == historyUserId }
    if (historyUser != null) {
        PlanHistoryDialog(user = historyUser, history = planHistory, onClose = { historyUserId = null })
    }
    var query by rememberSaveable { mutableStateOf("") }
    val sorted = remember(users) { users.sortedByDescending { it.addedMillis } }
    val visible = remember(sorted, query) { sorted.filter { userMatches(query, it) } }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "App users",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "users-search") {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search name, email, phone or plan"
                )
            }
            item(key = "users-count") {
                val total = users.size
                val text = when {
                    total == 0 -> "No users yet."
                    query.isBlank() ->
                        if (total == 1) "1 user" else formatCount(total.toLong()) + " users"
                    else -> formatCount(visible.size.toLong()) + " of " +
                        formatCount(total.toLong()) + " users"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (users.isNotEmpty() && visible.isEmpty()) {
                item(key = "users-none") {
                    Text(
                        text = "No users match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(visible, key = { "user-" + it.id }) { user ->
                UserRow(user = user, clickable = true, onClick = { historyUserId = user.id })
            }
        }
    }
}
