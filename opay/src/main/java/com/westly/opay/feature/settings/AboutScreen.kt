package com.westly.opay.feature.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.westly.opay.R
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast

private const val LEGAL_NONE = 0
private const val LEGAL_TERMS = 1
private const val LEGAL_PRIVACY = 2
private const val LATEST_VERSION_TOAST = "You are on the latest version"

internal val termsParagraphs = listOf(
    "This is a demo application created for personal learning and content. It is not the " +
        "real OPay app and is not affiliated with OPay or any bank.",
    "This is a demo application that never handles real money. Every transfer, payment and " +
        "balance shown is simulated and stored only on this phone.",
    "This is a demo application provided as is, without warranty of any kind. You use it at " +
        "your own risk and may stop using it at any time."
)

internal val privacyParagraphs = listOf(
    "This is a demo application that does not collect, send or share your personal " +
        "information. There is no server and no account behind it.",
    "This is a demo application that stores its sample data only on your phone. Clearing the " +
        "app data removes everything it saved.",
    "This is a demo application whose names, numbers and balances are fictional. Do not " +
        "enter any real personal or banking details."
)

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val toast = rememberOPayToast()
    val versionName = remember(context) { readVersionName(context) }
    var legal by rememberSaveable { mutableStateOf(LEGAL_NONE) }
    val darkTheme = c.bg.luminance() < 0.5f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            OPayTopBar(
                title = "About",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
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
                        .padding(top = 28.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(
                            if (darkTheme) R.drawable.opay_logo_full_dark
                            else R.drawable.opay_logo_full
                        ),
                        contentDescription = "OPay",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(170.dp)
                            .aspectRatio(3f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Version $versionName",
                        style = OPayType.body,
                        color = c.muted
                    )
                }
                OPayCard {
                    OPayRow(
                        title = "Terms & Conditions",
                        onClick = { legal = LEGAL_TERMS },
                        icon = OPayIcon.Doc
                    )
                    OPayRowDivider()
                    OPayRow(
                        title = "Privacy Policy",
                        onClick = { legal = LEGAL_PRIVACY },
                        icon = OPayIcon.Lock
                    )
                }
                OPayCard {
                    OPayRow(
                        title = "Version Upgrade",
                        onClick = { toast.show(LATEST_VERSION_TOAST) },
                        icon = OPayIcon.Zap
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (legal != LEGAL_NONE) {
            LegalTextOverlay(
                title = if (legal == LEGAL_TERMS) "Terms & Conditions" else "Privacy Policy",
                paragraphs = if (legal == LEGAL_TERMS) termsParagraphs else privacyParagraphs,
                onClose = { legal = LEGAL_NONE }
            )
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
internal fun LegalTextOverlay(
    title: String,
    paragraphs: List<String>,
    onClose: () -> Unit,
    footer: (@Composable () -> Unit)? = null
) {
    val c = OPay.colors
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
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
                .imePadding()
        ) {
            OPayTopBar(
                title = title,
                onBack = onClose,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = OPayType.body,
                        color = c.text,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
                footer?.invoke()
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun readVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
