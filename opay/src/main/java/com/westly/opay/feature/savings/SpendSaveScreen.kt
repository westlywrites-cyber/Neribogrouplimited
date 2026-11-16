package com.westly.opay.feature.savings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.SavingsBook
import com.westly.opay.data.SavingsKind
import com.westly.opay.feature.wealth.WealthViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.rememberOPayToast
import kotlinx.coroutines.delay

private const val LOADING_MILLIS = 700L

private val PigPink = Color(0xFFFFA8C5)
private val PigDeep = Color(0xFFF07FA6)
private val CoinGold = Color(0xFFF5C542)

@Composable
fun SpendSaveScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WealthViewModel = viewModel()
) {
    val toast = rememberOPayToast()
    val action = rememberSavingsAction(toast)
    var loading by rememberSaveable { mutableStateOf(true) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val saving = SavingsBook.ofKind(positions, SavingsKind.SpendSave).firstOrNull()
    val enabled = saving != null

    LaunchedEffect(Unit) {
        if (loading) {
            delay(LOADING_MILLIS)
            loading = false
        }
    }

    SavingsPage(
        title = "Spend & Save",
        onBack = onBack,
        toast = toast,
        modifier = modifier,
        showFooter = false,
        overlay = {
            SpendSaveSheet(
                visible = setupOpen,
                action = action,
                onDismiss = { setupOpen = false },
                onCreate = { percent, firstKobo ->
                    action.start(
                        call = { done ->
                            viewModel.create(
                                SavingsKind.SpendSave,
                                "Spend and Save",
                                firstKobo,
                                meta = JsonObject(mapOf("percent" to JsonPrimitive(percent))),
                                onDone = done
                            )
                        },
                        onSuccess = {
                            setupOpen = false
                            toast.show("Spend & Save is on")
                        }
                    )
                }
            )
        }
    ) {
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                OPayLoader()
            }
        } else {
            SavingsHero(
                art = {
                    PiggyIllustration(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(96.dp)
                    )
                },
                minHeight = 120.dp
            ) {
                Text(
                    text = "Save automatically every time you spend",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
            }
            SavingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Spend & Save",
                            style = OPayType.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SavingsColors.Ink
                        )
                        Text(
                            text = if (saving == null) "Off" else {
                                "On  " + (saving.spendSavePercent?.let { "$it%  " } ?: "") +
                                    formatNaira(saving.totalKobo)
                            },
                            style = OPayType.caption,
                            color = SavingsColors.Muted
                        )
                    }
                    SavingsSwitch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            if (on) {
                                action.clear()
                                setupOpen = true
                            } else if (saving != null) {
                                action.start(
                                    call = { done -> viewModel.withdraw(saving.id, saving.principalKobo, done) },
                                    onSuccess = { toast.show("Spend & Save is off") }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) SavingsColors.Green else Color(0xFFBDBDC2))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 21.dp else 3.dp, y = 3.dp)
                .size(18.dp)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
private fun PiggyIllustration(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 100f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        drawCircle(color = Color(0xFFD9A21B), radius = 11f * s, center = o(50f, 14f))
        drawCircle(color = CoinGold, radius = 8f * s, center = o(50f, 14f))
        drawRoundRect(
            color = PigDeep,
            topLeft = o(12f, 62f),
            size = Size(14f * s, 22f * s),
            cornerRadius = CornerRadius(4f * s)
        )
        drawRoundRect(
            color = PigDeep,
            topLeft = o(60f, 62f),
            size = Size(14f * s, 22f * s),
            cornerRadius = CornerRadius(4f * s)
        )
        drawOval(color = PigPink, topLeft = o(6f, 28f), size = Size(76f * s, 52f * s))
        drawOval(color = PigDeep, topLeft = o(72f, 46f), size = Size(24f * s, 20f * s))
        drawCircle(color = Color(0xFF7A2E4B), radius = 2f * s, center = o(80f, 56f))
        drawCircle(color = Color(0xFF7A2E4B), radius = 2f * s, center = o(88f, 56f))
        drawCircle(color = Color(0xFF3A1626), radius = 3f * s, center = o(62f, 44f))
        drawArc(
            color = PigDeep,
            startAngle = 200f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = o(-4f, 40f),
            size = Size(16f * s, 16f * s),
            style = Stroke(width = 3f * s)
        )
        drawRoundRect(
            color = Color(0xFF7A2E4B),
            topLeft = o(36f, 32f),
            size = Size(22f * s, 4f * s),
            cornerRadius = CornerRadius(2f * s)
        )
        drawOval(color = PigDeep, topLeft = o(52f, 22f), size = Size(14f * s, 16f * s))
    }
}
