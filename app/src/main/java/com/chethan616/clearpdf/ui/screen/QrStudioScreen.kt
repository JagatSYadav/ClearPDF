package com.chethan616.clearpdf.ui.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Vibrator
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassChip
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private enum class QrStudioTab { SCAN, CREATE }
private enum class CreateQrType { TEXT, URL, UPI, WIFI }

data class UpiDetails(
    val payeeVpa: String = "",
    val payeeName: String = "",
    val amount: String = "",
    val note: String = "",
    val currency: String = "INR",
    val rawUri: String = ""
)

data class WifiDetails(
    val ssid: String = "",
    val password: String = "",
    val type: String = "WPA"
)

@Composable
fun QrStudioScreen(
    backdrop: LayerBackdrop,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val accent = LiquidGlassColors.Orange
    val uiSensor = rememberUISensor()

    var currentTab by remember { mutableStateOf(QrStudioTab.SCAN) }

    GlassScreenScaffold(
        backdrop = backdrop,
        contentHorizontalPadding = 16.dp,
        headerHorizontalPadding = 16.dp,
        contentBottomPadding = 32.dp,
        header = { headerBackdrop ->
            GlassScreenHeaderRow(
                title = stringResource(R.string.qr_studio_title),
                backdrop = headerBackdrop,
                onBack = onNavigateBack
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Segmented Tab Switcher: [Scan QR] & [Create QR]
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val scanSelected = currentTab == QrStudioTab.SCAN
                val createSelected = currentTab == QrStudioTab.CREATE

                LiquidButton(
                    onClick = { currentTab = QrStudioTab.SCAN },
                    backdrop = backdrop,
                    tint = if (scanSelected) accent else Color.Unspecified,
                    surfaceColor = if (scanSelected) Color.Unspecified else (if (isLight) Color.Black.copy(0.05f) else Color.White.copy(0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.QrCodeScanner,
                            null,
                            Modifier.size(18.dp),
                            if (scanSelected) Color.White else text
                        )
                        BasicText(
                            stringResource(R.string.qr_tab_scan),
                            style = TextStyle(if (scanSelected) Color.White else text, 14.sp, FontWeight.SemiBold)
                        )
                    }
                }

                LiquidButton(
                    onClick = { currentTab = QrStudioTab.CREATE },
                    backdrop = backdrop,
                    tint = if (createSelected) accent else Color.Unspecified,
                    surfaceColor = if (createSelected) Color.Unspecified else (if (isLight) Color.Black.copy(0.05f) else Color.White.copy(0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.QrCode2,
                            null,
                            Modifier.size(18.dp),
                            if (createSelected) Color.White else text
                        )
                        BasicText(
                            stringResource(R.string.qr_tab_create),
                            style = TextStyle(if (createSelected) Color.White else text, 14.sp, FontWeight.SemiBold)
                        )
                    }
                }
            }

            // Tab Content
            when (currentTab) {
                QrStudioTab.SCAN -> {
                    QrScanView(backdrop = backdrop)
                }
                QrStudioTab.CREATE -> {
                    QrCreateView(backdrop = backdrop)
                }
            }
        }
    }
}

@Composable
private fun QrScanView(backdrop: LayerBackdrop) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val isDarkMode = LocalIsDarkMode.current
    val text = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val accent = LiquidGlassColors.Orange
    val uiSensor = rememberUISensor()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
        }
    }

    var scannedResult by remember { mutableStateOf<String?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<Camera?>(null) }

    if (!hasCameraPermission) {
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.CameraAlt, null, Modifier.size(32.dp), accent)
            }

            BasicText(
                stringResource(R.string.qr_permission_required),
                style = TextStyle(text, 18.sp, FontWeight.Bold, textAlign = TextAlign.Center)
            )

            BasicText(
                stringResource(R.string.qr_permission_desc),
                style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center)
            )

            LiquidButton(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                backdrop = backdrop,
                tint = accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.CameraAlt, null, Modifier.size(18.dp), Color.White)
                    BasicText(
                        stringResource(R.string.qr_grant_permission),
                        style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                    )
                }
            }
        }
    } else {
        // CameraX Scanner Viewport Container
        Box(
            Modifier
                .fillMaxWidth()
                .height(440.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = Executors.newSingleThreadExecutor()

                    val barcodeScanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_ALL_FORMATS)
                            .build()
                    )

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && scannedResult == null) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            if (barcodes.isNotEmpty() && scannedResult == null) {
                                                val raw = barcodes.firstOrNull()?.rawValue
                                                if (!raw.isNullOrBlank()) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    scannedResult = raw
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            cameraControl = cam
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning Target Box & Reticle Overlay
            ScannerTargetOverlay(modifier = Modifier.fillMaxSize())

            // Torch Toggle Button on top right
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable {
                            cameraControl?.let { cam ->
                                isTorchOn = !isTorchOn
                                cam.cameraControl.enableTorch(isTorchOn)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = "Torch",
                        tint = if (isTorchOn) Color(0xFFFFD600) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Bottom Prompt Badge
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(16.dp), accent)
                    BasicText(
                        stringResource(R.string.qr_scan_prompt),
                        style = TextStyle(Color.White, 12.sp, FontWeight.Medium)
                    )
                }
            }
        }

        // Modal Result Bottom Sheet when QR detected
        scannedResult?.let { rawResult ->
            ScannedResultCard(
                rawResult = rawResult,
                backdrop = backdrop,
                onDismiss = { scannedResult = null }
            )
        }
    }
}

@Composable
private fun ScannerTargetOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "laserAnim")
    val laserOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserPosition"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val boxSize = 250.dp
        Box(
            Modifier
                .size(boxSize)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, LiquidGlassColors.Orange.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
        ) {
            // Animated Scanning Laser Bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .padding(horizontal = 8.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = (244.dp * laserOffsetY).coerceAtLeast(0.dp))
                    .background(LiquidGlassColors.Orange)
            )
        }
    }
}

@Composable
private fun ScannedResultCard(
    rawResult: String,
    backdrop: LayerBackdrop,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val accent = LiquidGlassColors.Orange
    val green = LiquidGlassColors.Green
    val blue = LiquidGlassColors.Blue
    val uiSensor = rememberUISensor()

    val isUpi = rawResult.startsWith("upi://pay", ignoreCase = true)
    val isUrl = rawResult.startsWith("http://", ignoreCase = true) ||
            rawResult.startsWith("https://", ignoreCase = true) ||
            rawResult.startsWith("www.", ignoreCase = true)
    val isWifi = rawResult.startsWith("WIFI:", ignoreCase = true)

    val upiDetails = remember(rawResult) { if (isUpi) parseUpi(rawResult) else null }
    val wifiDetails = remember(rawResult) { if (isWifi) parseWifi(rawResult) else null }

    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isUpi -> green.copy(alpha = 0.20f)
                                isUrl -> blue.copy(alpha = 0.20f)
                                isWifi -> LiquidGlassColors.Purple.copy(alpha = 0.20f)
                                else -> accent.copy(alpha = 0.20f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            isUpi -> Icons.Rounded.Payment
                            isUrl -> Icons.Rounded.Language
                            isWifi -> Icons.Rounded.Wifi
                            else -> Icons.Rounded.QrCode
                        },
                        null,
                        Modifier.size(18.dp),
                        when {
                            isUpi -> green
                            isUrl -> blue
                            isWifi -> LiquidGlassColors.Purple
                            else -> accent
                        }
                    )
                }

                BasicText(
                    when {
                        isUpi -> "UPI Payment Details"
                        isUrl -> "Website Link"
                        isWifi -> "Wi-Fi Network"
                        else -> "Scanned Result"
                    },
                    style = TextStyle(text, 16.sp, FontWeight.Bold)
                )
            }

            LiquidIconButton(
                onClick = onDismiss,
                backdrop = backdrop,
                tint = LiquidGlassColors.Red,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), Color.White)
            }
        }

        // Details Display
        when {
            isUpi && upiDetails != null -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (upiDetails.payeeName.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText("Payee Name", style = TextStyle(sub, 12.sp))
                            BasicText(upiDetails.payeeName, style = TextStyle(text, 13.sp, FontWeight.SemiBold))
                        }
                    }
                    if (upiDetails.payeeVpa.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText("UPI ID", style = TextStyle(sub, 12.sp))
                            BasicText(upiDetails.payeeVpa, style = TextStyle(text, 13.sp, FontWeight.Medium))
                        }
                    }
                    if (upiDetails.amount.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText("Amount", style = TextStyle(sub, 12.sp))
                            BasicText("₹ ${upiDetails.amount}", style = TextStyle(green, 15.sp, FontWeight.Bold))
                        }
                    }
                    if (upiDetails.note.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText("Note", style = TextStyle(sub, 12.sp))
                            BasicText(upiDetails.note, style = TextStyle(sub, 12.sp))
                        }
                    }
                }

                // Pay via UPI Action
                LiquidButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawResult))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No UPI app installed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    backdrop = backdrop,
                    tint = green,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(18.dp), Color.White)
                        BasicText(stringResource(R.string.qr_pay_upi), style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
                    }
                }
            }

            isUrl -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
                        .padding(12.dp)
                ) {
                    BasicText(
                        rawResult,
                        style = TextStyle(blue, 14.sp, FontWeight.Medium),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                LiquidButton(
                    onClick = {
                        try {
                            val webUri = Uri.parse(if (rawResult.startsWith("www.")) "https://$rawResult" else rawResult)
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    backdrop = backdrop,
                    tint = blue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp), Color.White)
                        BasicText(stringResource(R.string.qr_open_browser), style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
                    }
                }
            }

            isWifi && wifiDetails != null -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText("Network SSID", style = TextStyle(sub, 12.sp))
                        BasicText(wifiDetails.ssid, style = TextStyle(text, 14.sp, FontWeight.Bold))
                    }
                    if (wifiDetails.password.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText("Password", style = TextStyle(sub, 12.sp))
                            BasicText(wifiDetails.password, style = TextStyle(text, 14.sp, FontWeight.Medium))
                        }
                    }
                }
            }

            else -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
                        .padding(14.dp)
                ) {
                    BasicText(
                        rawResult,
                        style = TextStyle(text, 14.sp),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Secondary Action Row: Copy, Share, Scan Another
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("QR Result", rawResult))
                    Toast.makeText(context, context.getString(R.string.qr_copied_toast), Toast.LENGTH_SHORT).show()
                },
                backdrop = backdrop,
                tint = LiquidGlassColors.Indigo,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), Color.White)
                    BasicText(stringResource(R.string.qr_copy_clipboard), style = TextStyle(Color.White, 13.sp, FontWeight.Medium))
                }
            }

            LiquidButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, rawResult)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share QR code text"))
                },
                backdrop = backdrop,
                tint = LiquidGlassColors.Purple,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(16.dp), Color.White)
                    BasicText(stringResource(R.string.qr_share), style = TextStyle(Color.White, 13.sp, FontWeight.Medium))
                }
            }
        }

        LiquidButton(
            onClick = onDismiss,
            backdrop = backdrop,
            surfaceColor = if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                stringResource(R.string.qr_scan_again),
                style = TextStyle(text, 14.sp, FontWeight.Medium),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun QrCreateView(backdrop: LayerBackdrop) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val accent = LiquidGlassColors.Orange
    val uiSensor = rememberUISensor()

    var selectedType by remember { mutableStateOf(CreateQrType.TEXT) }
    var rawInputText by remember { mutableStateOf("") }

    // UPI specific fields
    var upiVpa by remember { mutableStateOf("") }
    var upiName by remember { mutableStateOf("") }
    var upiAmount by remember { mutableStateOf("") }
    var upiNote by remember { mutableStateOf("") }

    // Wi-Fi specific fields
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }

    var generatedQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    fun computeQrContent(): String {
        return when (selectedType) {
            CreateQrType.TEXT -> rawInputText
            CreateQrType.URL -> {
                val trimmed = rawInputText.trim()
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
                else if (trimmed.isNotBlank()) "https://$trimmed" else ""
            }
            CreateQrType.UPI -> {
                if (upiVpa.isBlank()) ""
                else {
                    val encodedName = Uri.encode(upiName)
                    val encodedNote = Uri.encode(upiNote)
                    val amountPart = if (upiAmount.isNotBlank()) "&am=${upiAmount.trim()}" else ""
                    val notePart = if (upiNote.isNotBlank()) "&tn=$encodedNote" else ""
                    "upi://pay?pa=${upiVpa.trim()}&pn=$encodedName$amountPart$notePart&cu=INR"
                }
            }
            CreateQrType.WIFI -> {
                if (wifiSsid.isBlank()) ""
                else "WIFI:S:${wifiSsid.trim()};T:WPA;P:${wifiPassword};;"
            }
        }
    }

    fun generateQr() {
        val content = computeQrContent()
        if (content.isBlank()) {
            Toast.makeText(context, "Please enter content to generate QR", Toast.LENGTH_SHORT).show()
            return
        }

        isGenerating = true
        scope.launch(Dispatchers.Default) {
            try {
                val bitmap = createQrBitmap(content, 800)
                withContext(Dispatchers.Main) {
                    generatedQrBitmap = bitmap
                    isGenerating = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isGenerating = false
                    Toast.makeText(context, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Format Chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CreateQrType.values().forEach { type ->
                val isSelected = selectedType == type
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) accent else (if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.08f))
                        )
                        .clickable { selectedType = type }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    BasicText(
                        when (type) {
                            CreateQrType.TEXT -> "Plain Text"
                            CreateQrType.URL -> "Website URL"
                            CreateQrType.UPI -> "UPI Payment"
                            CreateQrType.WIFI -> "Wi-Fi Details"
                        },
                        style = TextStyle(
                            if (isSelected) Color.White else text,
                            13.sp,
                            FontWeight.Medium
                        )
                    )
                }
            }
        }

        // Input Form Card
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedType) {
                CreateQrType.TEXT -> {
                    BasicText("Text Content", style = TextStyle(text, 14.sp, FontWeight.SemiBold))
                    QrStyledInputField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        hint = "Enter text message, note, or ID number…",
                        singleLine = false,
                        maxLines = 4
                    )
                }

                CreateQrType.URL -> {
                    BasicText("Website Address (URL)", style = TextStyle(text, 14.sp, FontWeight.SemiBold))
                    QrStyledInputField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        hint = "example.com or https://...",
                        singleLine = true
                    )
                }

                CreateQrType.UPI -> {
                    BasicText("UPI Payment Request", style = TextStyle(text, 14.sp, FontWeight.SemiBold))
                    QrStyledInputField(
                        value = upiVpa,
                        onValueChange = { upiVpa = it },
                        hint = "Payee UPI ID (e.g. john@okhdfcbank)*",
                        singleLine = true
                    )
                    QrStyledInputField(
                        value = upiName,
                        onValueChange = { upiName = it },
                        hint = "Payee / Business Name (e.g. John Doe)",
                        singleLine = true
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            QrStyledInputField(
                                value = upiAmount,
                                onValueChange = { upiAmount = it },
                                hint = "Amount (₹ optional)",
                                singleLine = true
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            QrStyledInputField(
                                value = upiNote,
                                onValueChange = { upiNote = it },
                                hint = "Note (e.g. Coffee)",
                                singleLine = true
                            )
                        }
                    }
                }

                CreateQrType.WIFI -> {
                    BasicText("Wi-Fi Network Configuration", style = TextStyle(text, 14.sp, FontWeight.SemiBold))
                    QrStyledInputField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        hint = "Network Name (SSID)*",
                        singleLine = true
                    )
                    QrStyledInputField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        hint = "Password",
                        singleLine = true
                    )
                }
            }

            // Generate Button
            LiquidButton(
                onClick = { generateQr() },
                backdrop = backdrop,
                tint = accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp), Color.White)
                    }
                    BasicText(
                        stringResource(R.string.qr_generate_btn),
                        style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                    )
                }
            }
        }

        // QR Code Preview & Export Card
        generatedQrBitmap?.let { qrBitmap ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BasicText("Generated QR Code", style = TextStyle(text, 16.sp, FontWeight.Bold))

                // High-contrast clean white background for reliable camera scanning
                Box(
                    Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Generated QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Action Buttons: Save & Share
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidButton(
                        onClick = {
                            val savedFile = saveQrBitmap(context, qrBitmap)
                            if (savedFile != null) {
                                Toast.makeText(context, context.getString(R.string.qr_saved_toast), Toast.LENGTH_SHORT).show()
                            }
                        },
                        backdrop = backdrop,
                        tint = LiquidGlassColors.Green,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Save, null, Modifier.size(16.dp), Color.White)
                            BasicText(stringResource(R.string.qr_save_device), style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold))
                        }
                    }

                    LiquidButton(
                        onClick = {
                            shareQrBitmap(context, qrBitmap)
                        },
                        backdrop = backdrop,
                        tint = LiquidGlassColors.Blue,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(16.dp), Color.White)
                            BasicText(stringResource(R.string.qr_share_image), style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrStyledInputField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    val isDark = LocalIsDarkMode.current
    val isLight = !isDark
    val text = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
            .border(1.dp, if (isLight) Color.Black.copy(0.08f) else Color.White.copy(0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            BasicText(hint, style = TextStyle(sub, 14.sp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = TextStyle(text, 14.sp, FontWeight.Normal),
            cursorBrush = SolidColor(LiquidGlassColors.Orange),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun parseUpi(uriString: String): UpiDetails {
    return try {
        val uri = Uri.parse(uriString)
        UpiDetails(
            payeeVpa = uri.getQueryParameter("pa") ?: "",
            payeeName = uri.getQueryParameter("pn") ?: "",
            amount = uri.getQueryParameter("am") ?: "",
            note = uri.getQueryParameter("tn") ?: "",
            currency = uri.getQueryParameter("cu") ?: "INR",
            rawUri = uriString
        )
    } catch (_: Exception) {
        UpiDetails(rawUri = uriString)
    }
}

private fun parseWifi(wifiString: String): WifiDetails {
    val parts = wifiString.removePrefix("WIFI:").removeSuffix(";;").split(";")
    var ssid = ""
    var password = ""
    var type = "WPA"
    for (part in parts) {
        if (part.startsWith("S:")) ssid = part.removePrefix("S:")
        if (part.startsWith("P:")) password = part.removePrefix("P:")
        if (part.startsWith("T:")) type = part.removePrefix("T:")
    }
    return WifiDetails(ssid = ssid, password = password, type = type)
}

private fun createQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 1
    )
    val bitMatrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        hints
    )
    val width = bitMatrix.width
    val height = bitMatrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun saveQrBitmap(context: Context, bitmap: Bitmap): File? {
    return try {
        val docDir = File(context.filesDir, "documents").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "QR_$timeStamp.png"
        val file = File(docDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        // Also add to MediaStore for user convenience
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClearPDF")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    try {
        val file = saveQrBitmap(context, bitmap) ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR Code"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Cannot share QR image", Toast.LENGTH_SHORT).show()
    }
}
