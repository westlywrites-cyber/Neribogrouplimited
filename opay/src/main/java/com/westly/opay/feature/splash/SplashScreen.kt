package com.westly.opay.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.westly.opay.R
import com.westly.opay.core.theme.LightStatusBarIcons
import com.westly.opay.core.theme.OPay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

private const val SPLASH_MILLIS = 1200L

/**
 * Green splash with the OPay mark. Shows for 1.2 seconds, then reports whether the welcome slides
 * were already seen. The flag is read in the background while the splash is showing.
 */
@Composable
fun SplashScreen(
    readWelcomeSeen: () -> Boolean,
    onFinished: (welcomeSeen: Boolean) -> Unit
) {
    val finish by rememberUpdatedState(onFinished)
    val read by rememberUpdatedState(readWelcomeSeen)
    LightStatusBarIcons()
    LaunchedEffect(Unit) {
        val seen = async(Dispatchers.IO) { runCatching { read() }.getOrDefault(true) }
        delay(SPLASH_MILLIS)
        finish(seen.await())
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OPay.colors.green),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.opay_ic_launcher_foreground),
                contentDescription = "OPay",
                contentScale = ContentScale.Fit,
                modifier = Modifier.requiredSize(116.dp)
            )
        }
    }
}
