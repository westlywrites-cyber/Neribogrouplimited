package com.westly.neribogroup.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.PaymentModel

/**
 * The three ways an app can earn from its users, one tappable row each, with a short plain sentence under
 * the list about the one that is chosen. Used when an app is added and in the app's settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentModelPicker(
    selected: PaymentModel,
    onSelect: (PaymentModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (model in PaymentModel.values()) {
            FilterChip(
                selected = selected == model,
                onClick = { onSelect(model) },
                label = { Text(model.label) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = selected.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
