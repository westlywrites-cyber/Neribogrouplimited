package com.westly.opay.feature.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val PERMISSION_MESSAGE = "Camera permission is required to scan QR codes"
private const val NO_QR_IN_IMAGE = "No QR code found in this image"
private const val MAX_RESULT_CHARS = 200

private val ScanSquare = 260.dp
private val TorchBackground = Color(0xFF222222)

@Composable
fun QrScanScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val toast = rememberOPayToast()

    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var scanned by rememberSaveable { mutableStateOf<String?>(null) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val scanningEnabled = remember { AtomicBoolean(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        if (!isGranted) {
            val activity = context.findActivity()
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                )
        }
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasCameraPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scanned) { scanningEnabled.set(scanned == null) }
    LaunchedEffect(camera, torchOn) { camera?.cameraControl?.enableTorch(torchOn) }

    fun onDecoded(text: String) {
        scanningEnabled.set(false)
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        scanned = text
    }

    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            decodeFromImage(context, uri) { text ->
                if (text == null) toast.show(NO_QR_IN_IMAGE) else onDecoded(text)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScanTopBar(
                accent = c.green,
                onBack = onBack,
                onAlbum = { albumLauncher.launch("image/*") }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (granted) {
                    CameraPreview(
                        scanningEnabled = scanningEnabled,
                        onCameraChanged = { camera = it },
                        onCode = { onDecoded(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                    ScanFrame(accent = c.green)
                } else {
                    PermissionPrompt(
                        onGrant = {
                            if (permanentlyDenied) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                }
            }
            if (granted && camera?.cameraInfo?.hasFlashUnit() == true) {
                TorchButton(
                    on = torchOn,
                    accent = c.green,
                    onToggle = { torchOn = !torchOn }
                )
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }

    scanned?.let { text ->
        OPayDialog(
            title = "QR Code Scanned",
            message = if (text.length > MAX_RESULT_CHARS) text.take(MAX_RESULT_CHARS) + "…" else text,
            primaryText = "Close",
            onPrimary = { scanned = null },
            onDismiss = { scanned = null }
        )
    }
}

@Composable
private fun ScanTopBar(
    accent: Color,
    onBack: () -> Unit,
    onAlbum: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Back"; role = Role.Button }
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = Color.White)
        }
        Text(
            text = "Scan QR Code",
            style = OPayType.title,
            color = Color.White,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
        Text(
            text = "Album",
            style = OPayType.titleSmall,
            color = accent,
            modifier = Modifier.clickable(onClick = onAlbum)
        )
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = PERMISSION_MESSAGE,
            style = OPayType.body,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        OPayButton(
            text = "Grant permission",
            onClick = onGrant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun ScanFrame(accent: Color) {
    val transition = rememberInfiniteTransition(label = "scanLine")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanProgress"
    )
    Canvas(modifier = Modifier.size(ScanSquare)) {
        val stroke = 3.dp.toPx()
        val arm = 28.dp.toPx()
        val w = size.width
        val h = size.height
        fun bracket(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = accent,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        bracket(0f, 0f, arm, 0f)
        bracket(0f, 0f, 0f, arm)
        bracket(w, 0f, w - arm, 0f)
        bracket(w, 0f, w, arm)
        bracket(0f, h, arm, h)
        bracket(0f, h, 0f, h - arm)
        bracket(w, h, w - arm, h)
        bracket(w, h, w, h - arm)

        val barHeight = 2.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, accent, Color.Transparent)
            ),
            topLeft = Offset(0f, progress * (h - barHeight)),
            size = Size(w, barHeight)
        )
    }
}

@Composable
private fun TorchButton(
    on: Boolean,
    accent: Color,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(TorchBackground)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(
                icon = OPayIcon.Zap,
                size = 22.dp,
                tint = if (on) accent else Color.White
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (on) "Tap to turn off" else "Tap to turn on",
            style = OPayType.body,
            color = Color.White
        )
    }
}

@Composable
private fun CameraPreview(
    scanningEnabled: AtomicBoolean,
    onCameraChanged: (Camera?) -> Unit,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnCameraChanged by rememberUpdatedState(onCameraChanged)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val scanner = newQrScanner()
        var provider: ProcessCameraProvider? = null
        var released = false

        fun bind() {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (released) return@addListener
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    return@addListener
                }
                val cameraProvider = try {
                    future.get()
                } catch (e: Exception) {
                    return@addListener
                }
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(
                    analysisExecutor,
                    QrAnalyzer(scanner, scanningEnabled) { text ->
                        mainExecutor.execute { currentOnCode(text) }
                    }
                )
                try {
                    cameraProvider.unbindAll()
                    val bound = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    currentOnCameraChanged(bound)
                } catch (e: Exception) {
                    currentOnCameraChanged(null)
                }
            }, mainExecutor)
        }

        fun unbind() {
            provider?.unbindAll()
            currentOnCameraChanged(null)
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> bind()
                Lifecycle.Event.ON_STOP -> unbind()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            released = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            unbind()
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val enabled: AtomicBoolean,
    private val onCode: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !enabled.get()) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val text = barcodes.firstNotNullOfOrNull { it.rawValue }
                if (text != null && enabled.compareAndSet(true, false)) onCode(text)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

private fun newQrScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
)

private fun decodeFromImage(context: Context, uri: Uri, onResult: (String?) -> Unit) {
    val image = try {
        InputImage.fromFilePath(context, uri)
    } catch (e: Exception) {
        onResult(null)
        return
    }
    val scanner = newQrScanner()
    scanner.process(image)
        .addOnSuccessListener { barcodes -> onResult(barcodes.firstNotNullOfOrNull { it.rawValue }) }
        .addOnFailureListener { onResult(null) }
        .addOnCompleteListener { scanner.close() }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
