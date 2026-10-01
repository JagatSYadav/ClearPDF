package com.chethan616.clearpdf.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil3.compose.rememberAsyncImagePainter
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.LocalDocumentMirror
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ISO/IEC 7810 ID-1 standard dimensions: 85.60 mm x 53.98 mm (approx 1.586 : 1)
private const val CARD_ASPECT_RATIO = 85.6f / 54.0f

private enum class CardTargetSide { FRONT, BACK }

@Composable
fun IdCardComposerScreen(
    backdrop: LayerBackdrop,
    onNavigateBack: () -> Unit,
    onPdfGenerated: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode = LocalIsDarkMode.current
    val text = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val accent = LiquidGlassColors.Blue
    val green = LiquidGlassColors.Green
    val uiSensor = rememberUISensor()

    var frontUri by remember { mutableStateOf<Uri?>(null) }
    var backUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPreset by remember { mutableStateOf("Aadhaar") }
    var isGenerating by remember { mutableStateOf(false) }

    var targetSideForCamera by remember { mutableStateOf<CardTargetSide?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            when (targetSideForCamera) {
                CardTargetSide.FRONT -> frontUri = tempCameraUri
                CardTargetSide.BACK -> backUri = tempCameraUri
                null -> Unit
            }
        }
        targetSideForCamera = null
        tempCameraUri = null
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            targetSideForCamera?.let { side ->
                val uri = createTempCaptureUri(context, side.name.lowercase())
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            }
        } else {
            Toast.makeText(context, "Camera permission is required to take photo", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraFor(side: CardTargetSide) {
        targetSideForCamera = side
        val uri = createTempCaptureUri(context, side.name.lowercase())
        tempCameraUri = uri
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    // Photo picker launchers
    val frontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            frontUri = uri
        }
    }

    val backPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            backUri = uri
        }
    }

    val presets = listOf(
        stringResource(R.string.id_card_preset_aadhaar),
        stringResource(R.string.id_card_preset_pan),
        stringResource(R.string.id_card_preset_voter),
        stringResource(R.string.id_card_preset_license),
        stringResource(R.string.id_card_preset_custom)
    )

    GlassScreenScaffold(
        backdrop = backdrop,
        contentHorizontalPadding = 16.dp,
        headerHorizontalPadding = 16.dp,
        contentBottomPadding = 24.dp,
        header = { headerBackdrop ->
            GlassScreenHeaderRow(
                title = stringResource(R.string.id_card_composer_title),
                backdrop = headerBackdrop,
                onBack = onNavigateBack
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preset category selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = preset == selectedPreset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) accent else if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f)
                            )
                            .clickable { selectedPreset = preset }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        BasicText(
                            preset,
                            style = TextStyle(
                                if (isSelected) Color.White else text,
                                13.sp,
                                if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Overview explanation banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Print,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    BasicText(
                        stringResource(R.string.id_card_composer_sub),
                        style = TextStyle(text, 14.sp, FontWeight.SemiBold)
                    )
                }
                BasicText(
                    stringResource(R.string.id_card_layout_note),
                    style = TextStyle(sub, 12.sp, lineHeight = 16.sp)
                )
            }

            // ── Front Side Card Slot ──
            IdCardSlot(
                title = stringResource(R.string.id_card_front_title),
                subtitle = "$selectedPreset — " + stringResource(R.string.id_card_front_desc),
                imageUri = frontUri,
                backdrop = backdrop,
                uiSensor = uiSensor,
                isDarkMode = isDarkMode,
                accentColor = accent,
                testTagPrefix = "front",
                onCameraClick = { launchCameraFor(CardTargetSide.FRONT) },
                onGalleryClick = {
                    frontPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemove = { frontUri = null }
            )

            // ── Back Side Card Slot ──
            IdCardSlot(
                title = stringResource(R.string.id_card_back_title),
                subtitle = "$selectedPreset — " + stringResource(R.string.id_card_back_desc),
                imageUri = backUri,
                backdrop = backdrop,
                uiSensor = uiSensor,
                isDarkMode = isDarkMode,
                accentColor = LiquidGlassColors.Indigo,
                testTagPrefix = "back",
                onCameraClick = { launchCameraFor(CardTargetSide.BACK) },
                onGalleryClick = {
                    backPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemove = { backUri = null }
            )

            // ── Print Layout Visualization / Spec ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Badge, null, Modifier.size(16.dp), accent)
                        BasicText("A4 Print Sheet Preview", style = TextStyle(text, 13.sp, FontWeight.SemiBold))
                    }
                    BasicText("1-Click Lamination Ready", style = TextStyle(green, 11.sp, FontWeight.Medium))
                }

                // Mini diagram of Portrait A4 with Side-by-Side Cards
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDarkMode) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f))
                        .border(1.dp, if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Front Card Mockup
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (frontUri != null) accent.copy(alpha = 0.2f) else sub.copy(alpha = 0.12f))
                                .border(1.dp, if (frontUri != null) accent else sub.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                "FRONT (85.6mm)",
                                style = TextStyle(if (frontUri != null) accent else sub, 10.sp, FontWeight.Bold)
                            )
                        }

                        // Gap separator line
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(sub.copy(alpha = 0.4f))
                            )
                        }

                        // Back Card Mockup
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (backUri != null) LiquidGlassColors.Indigo.copy(alpha = 0.2f) else sub.copy(alpha = 0.12f))
                                .border(1.dp, if (backUri != null) LiquidGlassColors.Indigo else sub.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                "BACK (85.6mm)",
                                style = TextStyle(if (backUri != null) LiquidGlassColors.Indigo else sub, 10.sp, FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // ── Primary Generate Button ──
            val canGenerate = frontUri != null && backUri != null && !isGenerating
            LiquidButton(
                onClick = {
                    if (frontUri == null || backUri == null) {
                        Toast.makeText(context, context.getString(R.string.id_card_error_both_required), Toast.LENGTH_SHORT).show()
                        return@LiquidButton
                    }
                    isGenerating = true
                    scope.launch {
                        val result = generateIdCardA4Pdf(
                            context = context,
                            frontUri = frontUri!!,
                            backUri = backUri!!,
                            presetName = selectedPreset
                        )
                        isGenerating = false
                        if (result != null) {
                            val (pdfUri, fileName) = result
                            Toast.makeText(context, context.getString(R.string.id_card_success), Toast.LENGTH_SHORT).show()
                            onPdfGenerated(pdfUri, fileName)
                        } else {
                            Toast.makeText(context, "Failed to compose ID card PDF. Please retry.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                backdrop = backdrop,
                tint = if (canGenerate) accent else sub.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("generate_id_card_pdf_button")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        BasicText(
                            stringResource(R.string.id_card_generating),
                            style = TextStyle(Color.White, 16.sp, FontWeight.Bold)
                        )
                    } else {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        BasicText(
                            stringResource(R.string.id_card_generate_pdf),
                            style = TextStyle(Color.White, 16.sp, FontWeight.Bold)
                        )
                    }
                }
            }

            if (frontUri == null || backUri == null) {
                BasicText(
                    stringResource(R.string.id_card_error_both_required),
                    style = TextStyle(sub, 12.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IdCardSlot(
    title: String,
    subtitle: String,
    imageUri: Uri?,
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    isDarkMode: Boolean,
    accentColor: Color,
    testTagPrefix: String,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRemove: () -> Unit
) {
    val text = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Slot Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        title,
                        style = TextStyle(text, 15.sp, FontWeight.Bold)
                    )
                    if (imageUri != null) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Captured",
                            tint = LiquidGlassColors.Green,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                BasicText(
                    subtitle,
                    style = TextStyle(sub, 12.sp)
                )
            }

            if (imageUri != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Quick retake options
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .clickable(onClick = onCameraClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.FlipCameraAndroid, null, Modifier.size(14.dp), accentColor)
                            BasicText(stringResource(R.string.id_card_retake), style = TextStyle(accentColor, 12.sp, FontWeight.SemiBold))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                            .clickable(onClick = onRemove)
                            .testTag("${testTagPrefix}_remove_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.id_card_remove), Modifier.size(16.dp), Color(0xFFFF5252))
                    }
                }
            }
        }

        // Card Container Box (Exact ID Card Aspect Ratio 85.6 : 54)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT_RATIO)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isDarkMode) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.5f)
                )
                .border(
                    width = if (imageUri != null) 1.5.dp else 1.dp,
                    color = if (imageUri != null) accentColor else if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                )
                .testTag("${testTagPrefix}_slot_card"),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Corner tag indicating front/back
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    BasicText(
                        title.uppercase(),
                        style = TextStyle(Color.White, 10.sp, FontWeight.Bold)
                    )
                }
            } else {
                // Empty state with actions
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Badge,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = accentColor.copy(alpha = 0.8f)
                    )
                    BasicText(
                        "Position card inside frame",
                        style = TextStyle(sub, 12.sp, FontWeight.Medium)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Camera Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(accentColor)
                                .clickable(onClick = onCameraClick)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("${testTagPrefix}_camera_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CameraAlt, null, Modifier.size(16.dp), Color.White)
                                BasicText(stringResource(R.string.id_card_camera), style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold))
                            }
                        }

                        // Gallery Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f))
                                .clickable(onClick = onGalleryClick)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("${testTagPrefix}_gallery_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(16.dp), text)
                                BasicText(stringResource(R.string.id_card_gallery), style = TextStyle(text, 12.sp, FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Creates temporary URI in cache for camera intent capture.
 */
private fun createTempCaptureUri(context: Context, tag: String): Uri {
    val dir = File(context.cacheDir, "id_card_captures").apply { mkdirs() }
    val file = File(dir, "id_${tag}_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

/**
 * Generates an A4 portrait PDF with Front and Back ID cards positioned side-by-side
 * at the top printable area, with equal margins and vertical baseline alignment.
 */
private suspend fun generateIdCardA4Pdf(
    context: Context,
    frontUri: Uri,
    backUri: Uri,
    presetName: String
): Pair<Uri, String>? = withContext(Dispatchers.IO) {
    try {
        // Standard Portrait A4 Canvas dimensions: 595 x 842 points (72 DPI)
        val pageWidth = 595
        val pageHeight = 842

        // Standard ID Card dimension: 85.60 mm x 53.98 mm
        // 85.6 mm / 25.4 * 72 = 242.6456 pt
        // 54.0 mm / 25.4 * 72 = 153.0708 pt
        val cardWidth = 242.65f
        val cardHeight = 153.07f

        // Calculate horizontal alignment: Side-by-side with equal left/right margins and neat separator gap
        val gap = 25.7f // ~9.06 mm gap between Front and Back for clean cut & lamination
        val totalCardsSpan = (2 * cardWidth) + gap // 485.3 + 25.7 = 511.0 pt
        val marginLeft = (pageWidth.toFloat() - totalCardsSpan) / 2f // exactly 42.0 pt margins on left and right!
        val marginTop = 72f // 1 inch upper printable area margin

        val frontRect = RectF(
            marginLeft,
            marginTop,
            marginLeft + cardWidth,
            marginTop + cardHeight
        )

        val backRect = RectF(
            marginLeft + cardWidth + gap,
            marginTop,
            marginLeft + (2 * cardWidth) + gap,
            marginTop + cardHeight
        )

        // Decode bitmaps with robust memory sampling to avoid OOM
        // Target 1200 x 800 px (crisp ~350 DPI for print, safe memory footprint ~3-4 MB)
        val frontBitmap = decodeSampledBitmapFromUri(context, frontUri, 1200, 800)
            ?: throw IllegalStateException("Could not decode front ID image")
        val backBitmap = decodeSampledBitmapFromUri(context, backUri, 1200, 800)
            ?: throw IllegalStateException("Could not decode back ID image")

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // 1. Draw crisp white page background
        canvas.drawColor(android.graphics.Color.WHITE)

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 2. Draw Front ID card
        drawCardImageFitted(canvas, frontBitmap, frontRect, bitmapPaint)

        // 3. Draw Back ID card
        drawCardImageFitted(canvas, backBitmap, backRect, bitmapPaint)

        // 4. Draw neat borders & cutting guidelines around the cards for lamination
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
            color = android.graphics.Color.parseColor("#C8C8C8")
        }

        // Standard ISO ID card corner radius: ~3.18 mm ≈ 9 points
        val cornerRadius = 9f
        canvas.drawRoundRect(frontRect, cornerRadius, cornerRadius, borderPaint)
        canvas.drawRoundRect(backRect, cornerRadius, cornerRadius, borderPaint)

        // 5. Draw dashed center cutting separator between cards
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = android.graphics.Color.parseColor("#B0B0B0")
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }
        val centerCutX = marginLeft + cardWidth + (gap / 2f)
        canvas.drawLine(centerCutX, marginTop - 12f, centerCutX, marginTop + cardHeight + 12f, dashPaint)

        // 6. Draw clean header labels above cards (outside card area)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#666666")
            textSize = 8.5f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("FRONT SIDE", frontRect.centerX(), marginTop - 8f, labelPaint)
        canvas.drawText("BACK SIDE", backRect.centerX(), marginTop - 8f, labelPaint)

        // Footer info text
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#999999")
            textSize = 7.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "ClearPDF · Standard ID Card Print Sheet (85.6 × 54.0 mm) · 100% Scale · Side-by-Side Lamination Ready",
            pageWidth / 2f,
            marginTop + cardHeight + 24f,
            infoPaint
        )

        pdfDocument.finishPage(page)

        // Recycle bitmaps immediately to release memory
        frontBitmap.recycle()
        backBitmap.recycle()

        // Save PDF to internal storage: context.filesDir/documents/
        val docDir = File(context.filesDir, "documents").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "ID_CARD_$timeStamp.pdf"
        val outputFile = File(docDir, fileName)

        outputFile.outputStream().use { outStream ->
            pdfDocument.writeTo(outStream)
            outStream.flush()
        }
        pdfDocument.close()

        val outputUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            outputFile
        )

        // Register into RecentFilesManager so it appears on the dashboard
        RecentFilesManager.addRecent(
            context,
            RecentFile(
                name = fileName,
                uriString = outputUri.toString(),
                timestamp = System.currentTimeMillis(),
                pageCount = 1,
                sizeBytes = outputFile.length()
            )
        )

        // Ensure durable access via LocalDocumentMirror
        LocalDocumentMirror.resolve(context, outputUri, "pdf")

        Pair(outputUri, fileName)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Draws a bitmap centered and cropped to fit the target card rectangle with exact aspect ratio.
 */
private fun drawCardImageFitted(
    canvas: Canvas,
    bitmap: Bitmap,
    targetRect: RectF,
    paint: Paint
) {
    val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val dstRatio = targetRect.width() / targetRect.height()

    val srcRect = if (srcRatio > dstRatio) {
        // Source is wider than target -> crop sides
        val targetWidth = (bitmap.height * dstRatio).toInt()
        val xOffset = (bitmap.width - targetWidth) / 2
        Rect(xOffset, 0, xOffset + targetWidth, bitmap.height)
    } else {
        // Source is taller than target -> crop top/bottom
        val targetHeight = (bitmap.width / dstRatio).toInt()
        val yOffset = (bitmap.height - targetHeight) / 2
        Rect(0, yOffset, bitmap.width, yOffset + targetHeight)
    }

    canvas.drawBitmap(bitmap, srcRect, targetRect, paint)
}

/**
 * Decodes a bitmap from Uri with downsampling and EXIF orientation correction.
 */
private fun decodeSampledBitmapFromUri(
    context: Context,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return null

        var inSampleSize = 1
        while ((rawWidth / inSampleSize) > reqWidth * 1.5 || (rawHeight / inSampleSize) > reqHeight * 1.5) {
            inSampleSize *= 2
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        // Check and apply EXIF orientation
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        return if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            val rotated = Bitmap.createBitmap(
                sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true
            )
            if (rotated !== sampledBitmap) {
                sampledBitmap.recycle()
            }
            rotated
        } else {
            sampledBitmap
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
