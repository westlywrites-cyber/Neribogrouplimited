package com.westly.opay.feature.receipt

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.R
import com.westly.opay.core.theme.LocalOPayColors
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayLightColors
import com.westly.opay.core.theme.rememberUnscaledTextDensity
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.UserProfile
import com.westly.opay.feature.transfer.SelectedTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOADER_MILLIS = 500L
private const val SHARE_ERROR_MESSAGE = "Could not share receipt"
private val TopBarHeight = 68.dp
private val DefaultCardInset = 16.dp
private val SkinCardInset = 6.dp

@Composable
fun ReceiptScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val selected by SelectedTransaction.current.collectAsStateWithLifecycle()
    val transaction = selected
    if (transaction == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(receiptColors().page)
        )
        LaunchedEffect(Unit) { onBack() }
    } else {
        ReceiptContent(transaction = transaction, onBack = onBack, modifier = modifier)
    }
}

@Composable
private fun ReceiptContent(
    transaction: Transaction,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val r = receiptColors()
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val profile by repository.profile.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()
    var loading by rememberSaveable { mutableStateOf(true) }
    var skinIndex by rememberSaveable { mutableIntStateOf(0) }
    val skin = ReceiptSkin.entries[skinIndex]
    var message by rememberSaveable(skinIndex) { mutableStateOf(skin.message) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val exportLayer = rememberGraphicsLayer()

    // The shared file is always the white receipt, whatever the app theme. The default receipt is shared
    // edge to edge on white; the coloured designs keep their page margin on a light page.
    fun share(export: suspend (Context, Bitmap, Int, String, Boolean) -> Unit) {
        focusManager.clearFocus()
        val plain = skin == ReceiptSkin.Default
        scope.launch {
            try {
                val card = exportLayer.toImageBitmap().asAndroidBitmap()
                val page = if (plain) Color.White else ReceiptColors.Light.page
                export(context, card, page.toArgb(), transaction.txNo, !plain)
            } catch (e: Exception) {
                toast.show(SHARE_ERROR_MESSAGE)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (loading) {
            delay(LOADER_MILLIS)
            loading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(r.page)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(r.card)
                    .statusBarsPadding()
            ) {
                OPayTopBar(
                    title = "Share Receipt",
                    onBack = onBack,
                    modifier = Modifier
                        .height(TopBarHeight)
                        .padding(horizontal = 12.dp)
                )
            }
            if (loading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .receiptWatermark(),
                    contentAlignment = Alignment.Center
                ) {
                    OPayLoader()
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val minCardHeight = maxOf(0.dp, maxHeight - DefaultCardInset * 2)
                    ExportCopy(
                        skin = skin,
                        transaction = transaction,
                        profile = profile,
                        message = message,
                        layer = exportLayer
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ReceiptBody(
                            skin = skin,
                            transaction = transaction,
                            profile = profile,
                            message = message,
                            onMessageChange = { message = it },
                            modifier = if (skin == ReceiptSkin.Default) {
                                Modifier
                                    .padding(
                                        start = DefaultCardInset,
                                        end = DefaultCardInset,
                                        top = DefaultCardInset
                                    )
                                    .heightIn(min = minCardHeight)
                            } else {
                                Modifier.padding(
                                    start = SkinCardInset,
                                    end = SkinCardInset,
                                    top = SkinCardInset
                                )
                            }
                        )
                        SkinPickerRow(
                            selectedIndex = skinIndex,
                            onSelect = { skinIndex = it }
                        )
                    }
                }
                ShareBar(
                    onShareImage = { share(ReceiptExporter::shareImage) },
                    onSharePdf = { share(ReceiptExporter::sharePdf) }
                )
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun ReceiptBody(
    skin: ReceiptSkin,
    transaction: Transaction,
    profile: UserProfile,
    message: String,
    onMessageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // The receipt on screen keeps the sizes it has always had; only the rest of the app follows the text scale.
    CompositionLocalProvider(LocalDensity provides rememberUnscaledTextDensity()) {
        ReceiptSkinCard(
            skin = skin,
            transaction = transaction,
            profile = profile,
            message = message,
            onMessageChange = onMessageChange,
            modifier = modifier
        )
    }
}

/** The shared receipt is always 360dp wide at 3x, so 1080 pixels wide on every phone, like the real OPay receipt. */
private const val EXPORT_WIDTH_PX = 1080
private const val EXPORT_DENSITY = 3f

/**
 * A second copy of the receipt in the white look, at a fixed width and density and with its natural height, so
 * the shared file looks the same on every phone and ignores the phone's font size setting. It takes no space and
 * is never drawn to the screen; it is only recorded into [layer], which is what Share as image and Share as PDF
 * save.
 */
@Composable
private fun ExportCopy(
    skin: ReceiptSkin,
    transaction: Transaction,
    profile: UserProfile,
    message: String,
    layer: GraphicsLayer
) {
    val widthPx = EXPORT_WIDTH_PX
    Box(
        modifier = Modifier.layout { measurable, _ ->
            val placeable = measurable.measure(
                Constraints(minWidth = widthPx, maxWidth = widthPx, minHeight = 0, maxHeight = Constraints.Infinity)
            )
            layout(0, 0) { placeable.place(0, 0) }
        }
    ) {
        CompositionLocalProvider(
            LocalOPayColors provides OPayLightColors,
            LocalReceiptExport provides true,
            LocalDensity provides Density(EXPORT_DENSITY, 1f)
        ) {
            ReceiptSkinCard(
                skin = skin,
                transaction = transaction,
                profile = profile,
                message = message,
                modifier = Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                }
            )
        }
    }
}

@Composable
private fun SkinPickerRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReceiptSkin.entries.forEachIndexed { index, skin ->
            val active = index == selectedIndex
            Column(
                modifier = Modifier
                    .width(62.dp)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(skin.gradient))
                        .border(
                            width = 2.dp,
                            color = if (active) c.green else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = skin.label,
                    fontSize = 11.sp,
                    color = if (active) c.green else c.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ShareBar(onShareImage: () -> Unit, onSharePdf: () -> Unit) {
    val r = receiptColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(r.card)
            .navigationBarsPadding()
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(r.divider)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShareAction(
                iconRes = R.drawable.ic_receipt_image,
                text = "Share as image",
                onClick = onShareImage,
                modifier = Modifier.weight(1f)
            )
            Spacer(
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(r.divider)
            )
            ShareAction(
                iconRes = R.drawable.ic_receipt_pdf,
                text = "Share as PDF",
                onClick = onSharePdf,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ShareAction(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val r = receiptColors()
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = r.green,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, fontSize = 16.sp, color = r.green)
    }
}
