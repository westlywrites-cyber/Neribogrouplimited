package com.westly.neribogroup.feature.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.Staff

/**
 * The Today page inside Staff: who is On-site, Remote and Off today. Each person is on a line of their own with
 * their full name. It shows the same people the short Today card on the Staff page counts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffTodayScreen(
    onSite: List<Staff>,
    remote: List<Staff>,
    off: List<Staff>,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Today", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { TodayGroup("On-site", onSite, showWhenEmpty = true) }
            item { TodayGroup("Remote", remote, showWhenEmpty = true) }
            // Off is only listed when somebody is off, as on the Staff page before.
            if (off.isNotEmpty()) item { TodayGroup("Off", off, showWhenEmpty = false) }
        }
    }
}

@Composable
private fun TodayGroup(title: String, people: List<Staff>, showWhenEmpty: Boolean) {
    if (people.isEmpty() && !showWhenEmpty) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (people.isEmpty()) {
                Text(
                    text = "Nobody",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                for (person in people) {
                    Text(text = person.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
