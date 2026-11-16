package com.westly.opay.feature.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onRegister: () -> Unit,
    onLogin: () -> Unit
) {
    val c = OPay.colors
    val pagerState = rememberPagerState(pageCount = { WELCOME_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val state = WelcomePagerState(page = pagerState.currentPage)

    fun goTo(target: WelcomePagerState) {
        scope.launch { pagerState.animateScrollToPage(target.page) }
    }

    BackHandler(enabled = state.canGoBack) { goTo(state.back()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (state.showSkip) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button) { goTo(state.skip()) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Skip", style = OPayType.title, color = c.muted)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            WelcomeSlideContent(welcomeSlides[page])
        }

        PageDots(current = state.page, count = state.count)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)
        ) {
            if (state.isLast) {
                OPayButton(text = "Register", onClick = onRegister)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(role = Role.Button, onClick = onLogin),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Login",
                        style = OPayType.title.copy(fontWeight = FontWeight.Bold),
                        color = c.green
                    )
                }
            } else {
                OPayButton(text = "Next", onClick = { goTo(state.next()) })
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun WelcomeSlideContent(slide: WelcomeSlide) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WelcomeIllustration(art = slide.art)
        Spacer(Modifier.height(28.dp))
        Text(
            text = slide.headline,
            style = OPayType.display,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = slide.subText,
            style = OPayType.title,
            color = c.muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until count) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (index == current) c.green else c.muted.copy(alpha = 0.4f))
            )
        }
    }
}
