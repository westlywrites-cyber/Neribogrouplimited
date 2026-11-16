package com.westly.opay.feature.savings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val JOINED_MESSAGE = "Joined"
private const val MEMBER_COUNTER = "13140485"
private val DigitTile = Color(0xFF0A3D2C)
private val FilterLabels = listOf("Hot", "Fest.", "Accomodation", "Appliance", "Biz.", "Edu.")

internal data class TargetInfo(
    val name: String,
    val joined: String,
    val avgGoal: String,
    val totalSaved: String,
    val hot: Boolean,
    val icon: OPayIcon,
    val start: Color,
    val end: Color
)

internal val PopularTargets = listOf(
    TargetInfo(
        "Christmas 2026", "840,709", "121K", "13892.7M", true,
        OPayIcon.Gift, Color(0xFFE5484D), Color(0xFFFF8A5B)
    ),
    TargetInfo(
        "Small Business Savings", "208,654", "287.1K", "16077M", true,
        OPayIcon.Bag, Color(0xFF3B82F6), Color(0xFF60C3F5)
    ),
    TargetInfo(
        "Saving Challenge 2026", "793,950", "232.6K", "30161.9M", true,
        OPayIcon.Star, Color(0xFF8B5CF6), Color(0xFFC084FC)
    ),
    TargetInfo(
        "House Rent", "512,336", "88.7K", "57742.9M", false,
        OPayIcon.Bank, Color(0xFF14B8A6), Color(0xFF5EEAD4)
    ),
    TargetInfo(
        "Business Booster", "176,420", "287.1K", "16077M", false,
        OPayIcon.Chart, Color(0xFFF59E0B), Color(0xFFFCD34D)
    )
)

@Composable
internal fun PopularTargetsOverlay(
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by rememberSaveable { mutableStateOf(0) }

    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SavingsColors.Page)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Close"; role = Role.Button }
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = SavingsColors.Ink)
                }
                Text(
                    text = "Popular Targets",
                    style = OPayType.title,
                    color = SavingsColors.Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "My Targets",
                    style = OPayType.titleSmall,
                    color = SavingsColors.Muted,
                    modifier = Modifier.clickable { onMessage(NOT_AVAILABLE) }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(OPayShapes.card)
                        .background(
                            Brush.linearGradient(listOf(SavingsColors.HeroStart, SavingsColors.HeroEnd))
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Join Target Group with the members of",
                        style = OPayType.body,
                        color = Color.White
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MEMBER_COUNTER.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .size(width = 30.dp, height = 40.dp)
                                    .background(DigitTile, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = digit.toString(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                PopularTargets.forEachIndexed { index, target ->
                    RankedTargetRow(
                        rank = index + 1,
                        target = target,
                        onJoin = { onMessage(JOINED_MESSAGE) }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SavingsColors.Card)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterLabels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = OPayType.bodySmall,
                        fontWeight = if (index == filter) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == filter) SavingsColors.OnGreen else SavingsColors.Ink,
                        modifier = Modifier
                            .background(
                                if (index == filter) SavingsColors.Green else SavingsColors.Mint,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { filter = index }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RankedTargetRow(rank: Int, target: TargetInfo, onJoin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(SavingsColors.Card, OPayShapes.card)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) SavingsColors.Red else SavingsColors.Muted,
            modifier = Modifier.width(20.dp)
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(OPayShapes.tile)
                .background(Brush.linearGradient(listOf(target.start, target.end))),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = target.icon, size = 26.dp, tint = Color.White)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = target.name,
                    style = OPayType.body,
                    fontWeight = FontWeight.Bold,
                    color = SavingsColors.Ink,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (target.hot) {
                    Spacer(Modifier.width(5.dp))
                    SavingsTag(text = "Hot")
                }
            }
            Text(
                text = "Avg Goal " + target.avgGoal,
                style = OPayType.captionSmall,
                color = SavingsColors.Muted
            )
            Text(
                text = "Total Saved " + target.totalSaved,
                style = OPayType.captionSmall,
                color = SavingsColors.Muted
            )
            Spacer(Modifier.height(4.dp))
            SavingsChip(text = target.joined + " members joined")
        }
        SavingsPill(text = "Join", onClick = onJoin)
    }
}
