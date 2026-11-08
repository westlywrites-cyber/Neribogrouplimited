package com.westly.neribogroup.feature.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatNaira
import com.westly.neribogroup.ui.theme.FontStepNames

/** Appearance: the app's own theme (Light or Dark) and its own font size. The phone's settings are ignored. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    fontStep: Int,
    onBack: () -> Unit,
    onDarkTheme: (Boolean) -> Unit,
    onFontStep: (Int) -> Unit,
    /** The Cloud card (Phase 3A). Shown first in the list; nothing when not given. */
    cloud: @Composable () -> Unit = {},
    /** The Claude (AI) card (Phase 4B). Shown under the Cloud card; nothing when not given. */
    ai: @Composable () -> Unit = {}
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item(key = "cloud") {
                cloud()
            }

            item(key = "ai") {
                ai()
            }

            item(key = "theme") {
                FlatCard {
                    SectionTitle("Theme")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !darkTheme,
                            onClick = { onDarkTheme(false) },
                            label = { Text("Light") }
                        )
                        FilterChip(
                            selected = darkTheme,
                            onClick = { onDarkTheme(true) },
                            label = { Text("Dark") }
                        )
                    }
                }
            }

            item(key = "font") {
                FlatCard {
                    SectionTitle("Font size")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FontStepNames[fontStep.coerceIn(0, 5)],
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = fontStep.toFloat(),
                        onValueChange = { onFontStep(Math.round(it)) },
                        valueRange = 0f..5f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "The app uses its own font size and ignores the font size of the phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "preview") {
                FlatCard {
                    SectionTitle("Preview")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sample title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This is a sample paragraph. It shows how normal text looks with the " +
                            "theme and font size you picked.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatNaira(1_250_000L),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item(key = "reset") {
                TintedButton(
                    text = "Reset (Dark, Normal)",
                    onClick = {
                        onDarkTheme(true)
                        onFontStep(2)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
