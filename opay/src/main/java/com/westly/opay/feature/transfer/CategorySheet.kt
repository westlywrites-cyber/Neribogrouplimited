package com.westly.opay.feature.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

internal data class CategoryOption(val label: String, val icon: OPayIcon)

internal val categoryGroups: List<Pair<String, List<CategoryOption>>> = listOf(
    "Daily Life" to listOf(
        CategoryOption("Food & Dining", OPayIcon.Tag),
        CategoryOption("Groceries", OPayIcon.Bag),
        CategoryOption("Transport", OPayIcon.Clock),
        CategoryOption("Airtime & Data", OPayIcon.Phone),
        CategoryOption("Bills & Utilities", OPayIcon.Zap)
    ),
    "Personal Spending" to listOf(
        CategoryOption("Shopping", OPayIcon.Card),
        CategoryOption("Rent & Mortgage", OPayIcon.Home),
        CategoryOption("Healthcare", OPayIcon.Plus),
        CategoryOption("Personal Care", OPayIcon.Me),
        CategoryOption("Education", OPayIcon.Doc),
        CategoryOption("Entertainment", OPayIcon.Tv),
        CategoryOption("Travel", OPayIcon.Share),
        CategoryOption("Online Transactions", OPayIcon.Data),
        CategoryOption("Betting", OPayIcon.Game),
        CategoryOption("Offline Consumption", OPayIcon.List)
    ),
    "People & Others" to listOf(
        CategoryOption("Family & Friends", OPayIcon.User),
        CategoryOption("Gifts & Donations", OPayIcon.Gift),
        CategoryOption("Other", OPayIcon.Star)
    )
)

internal val quickCategories: List<CategoryOption> = listOf(
    "Food & Dining",
    "Groceries",
    "Bills & Utilities",
    "Family & Friends"
).map { name -> categoryGroups.flatMap { it.second }.first { it.label == name } }

private const val SHEET_COLUMNS = 4

@Composable
internal fun CategoryTile(
    option: CategoryOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Column(
        modifier = modifier
            .clip(OPayShapes.tile)
            .border(1.5.dp, if (selected) c.green else Color.Transparent, OPayShapes.tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(c.cardAlt, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = option.icon, size = 20.dp, tint = c.green)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = option.label,
            style = OPayType.captionSmall,
            color = c.text,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CategorySheet(
    visible: Boolean,
    selected: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    val c = OPay.colors
    var choice by remember(visible, selected) { mutableStateOf(selected) }
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Category",
                style = OPayType.title,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "Close"; role = Role.Button }
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Close, size = 20.dp, tint = c.muted)
            }
        }
        Column(
            modifier = Modifier
                .padding(top = 4.dp)
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
        ) {
            categoryGroups.forEach { (title, options) ->
                Text(
                    text = title,
                    style = OPayType.caption,
                    fontWeight = FontWeight.Bold,
                    color = c.muted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                options.chunked(SHEET_COLUMNS).forEach { rowOptions ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowOptions.forEach { option ->
                            CategoryTile(
                                option = option,
                                selected = choice == option.label,
                                onClick = {
                                    choice = if (choice == option.label) null else option.label
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(SHEET_COLUMNS - rowOptions.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        OPayButton(
            text = "Confirm",
            onClick = {
                onConfirm(choice)
                onDismiss()
            },
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
