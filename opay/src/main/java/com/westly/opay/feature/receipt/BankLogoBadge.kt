package com.westly.opay.feature.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay

private val BadgeBorder = Color(0xFFE6E8EC)
private val OpayRingGreen = Color(0xFF1ED6A0)
private val OpayRingAccent = Color(0xFF1B1464)

@Composable
fun BankLogoBadge(bankName: String?, opay: Boolean, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    val label = if (opay) "OPay" else bankName.orEmpty()
    val scale = size / 60.dp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .semantics { contentDescription = "$label logo" },
        contentAlignment = Alignment.Center
    ) {
        if (opay) {
            OpayRingLogo(scale = scale)
        } else {
            BankLogoImage(bankName = bankName, logoSize = 36.dp * scale)
        }
        Box(modifier = Modifier.fillMaxSize().border(1.dp, BadgeBorder, CircleShape))
    }
}

@Composable
private fun OpayRingLogo(scale: Float = 1f) {
    Canvas(modifier = Modifier.size(36.dp * scale)) {
        val stroke = 6.dp.toPx() * scale
        val outerDiameter = 30.dp.toPx() * scale
        val ringDiameter = outerDiameter - stroke
        drawCircle(
            color = OpayRingGreen,
            radius = ringDiameter / 2f,
            center = center,
            style = Stroke(width = stroke)
        )
        val innerLeftEdge = center.x - (outerDiameter / 2f - stroke)
        val accentWidth = 6.dp.toPx() * scale
        val accentHeight = 3.dp.toPx() * scale
        drawRect(
            color = OpayRingAccent,
            topLeft = Offset(innerLeftEdge - accentWidth / 2f, center.y - accentHeight / 2f),
            size = Size(accentWidth, accentHeight)
        )
    }
}

@Composable
internal fun BankLogoImage(
    bankName: String?,
    logoSize: Dp = 36.dp,
    fallback: @Composable () -> Unit = { LetterFallback(bankName = bankName) }
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as OPayApplication).repository }
    val appBanks by repository.banksState.collectAsStateWithLifecycle()
    val catalog by produceState(initialValue = emptyList<LogoBank>(), context) {
        value = BankLogoCatalog.get(context)
    }
    val url = remember(bankName, appBanks, catalog) {
        BankLogoMatcher.find(bankName, appBanks, catalog)?.let(BankLogoMatcher::logoUrl)
    }
    if (url == null) {
        fallback()
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            loading = { fallback() },
            error = { fallback() },
            success = { SubcomposeAsyncImageContent(modifier = Modifier.size(logoSize)) }
        )
    }
}

@Composable
private fun LetterFallback(bankName: String?) {
    val c = OPay.colors
    val letter = bankName.orEmpty().trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier.fillMaxSize().background(c.cardAlt, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = letter, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.green)
    }
}
