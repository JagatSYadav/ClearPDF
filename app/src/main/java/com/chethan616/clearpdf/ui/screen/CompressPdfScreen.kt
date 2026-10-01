package com.chethan616.clearpdf.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.ui.components.GlassChip
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.ToolScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.CompressPdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.CompressionPreset
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

@Composable
fun CompressPdfScreen(
    backdrop: LayerBackdrop,
    viewModel: CompressPdfViewModel,
    initialUri: Uri? = null,
    onBack: () -> Unit,
    onViewOutput: (Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val accent = LiquidGlassColors.Green
    val blueAccent = LiquidGlassColors.Blue
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadRecentPdfs(context)
        if (initialUri != null && state.sourceUri == null) {
            viewModel.onSelectFile(context, initialUri)
        }
    }

    LaunchedEffect(state.resultMessage, state.errorMessage) {
        if (!state.errorMessage.isNullOrBlank()) {
            delay(4000)
            viewModel.clearFeedback()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onSelectFile(context, uri)
        }
    }

    fun shareCompressedPdf(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share compressed PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024
        return if (kb >= 1024) {
            val mb = kb.toFloat() / 1024
            "%.2f MB (%d KB)".format(mb, kb)
        } else {
            "$kb KB"
        }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_compress),
        backdrop = backdrop,
        onBack = onBack
    ) {
        // Top Hero Card if no file selected
        if (state.sourceFileName.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Compress, null, Modifier.size(36.dp), accent)
                }
                BasicText(
                    "PDF Compressor",
                    style = TextStyle(text, 20.sp, FontWeight.Bold)
                )
                BasicText(
                    "Reduce document file size with target presets (100KB, 200KB, 500KB) or custom quality for online portals and fast sharing.",
                    style = TextStyle(sub, 13.sp, textAlign = TextAlign.Center)
                )

                LiquidButton(
                    onClick = { filePicker.launch(arrayOf("application/pdf")) },
                    backdrop = backdrop,
                    tint = accent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.UploadFile, null, Modifier.size(20.dp), Color.White)
                        BasicText(
                            "Choose PDF from Device",
                            style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                        )
                    }
                }
            }

            // Recent PDFs list for quick selection
            if (state.recentPdfs.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.History, null, Modifier.size(18.dp), accent)
                        BasicText(
                            "Or Choose from Recent PDFs",
                            style = TextStyle(text, 15.sp, FontWeight.SemiBold)
                        )
                    }

                    state.recentPdfs.take(5).forEach { recent ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .liquidGlassPanel(backdrop, uiSensor)
                                .clickable {
                                    viewModel.onSelectFile(context, recent.uri)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Description, null, Modifier.size(20.dp), accent)
                                }
                                Column {
                                    BasicText(
                                        recent.name,
                                        style = TextStyle(text, 14.sp, FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeLabel = if (recent.sizeBytes > 0) formatFileSize(recent.sizeBytes) else "PDF Document"
                                    val pageLabel = if (recent.pageCount > 0) " • ${recent.pageCount} pages" else ""
                                    BasicText(
                                        "$sizeLabel$pageLabel",
                                        style = TextStyle(sub, 12.sp)
                                    )
                                }
                            }
                            Icon(Icons.Rounded.Compress, null, Modifier.size(18.dp), accent)
                        }
                    }
                }
            }
        } else {
            // Selected File Details Card
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Description, null, Modifier.size(24.dp), accent)
                        }
                        Column {
                            BasicText(
                                state.sourceFileName,
                                style = TextStyle(text, 15.sp, FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            BasicText(
                                "${state.pageCount} ${if (state.pageCount == 1) "page" else "pages"} • ${formatFileSize(state.originalSizeBytes)}",
                                style = TextStyle(sub, 13.sp)
                            )
                        }
                    }

                    LiquidButton(
                        onClick = { filePicker.launch(arrayOf("application/pdf")) },
                        backdrop = backdrop,
                        tint = accent
                    ) {
                        BasicText(
                            "Change",
                            style = TextStyle(Color.White, 12.sp, FontWeight.Medium)
                        )
                    }
                }
            }

            // Compression Presets Selection
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp), accent)
                    BasicText(
                        "Target File Size & Quality Presets",
                        style = TextStyle(text, 16.sp, FontWeight.SemiBold)
                    )
                }

                CompressionPreset.values().forEach { preset ->
                    val isSelected = state.selectedPreset == preset
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) accent.copy(alpha = if (isLight) 0.16f else 0.25f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) accent else (if (isLight) Color.Black.copy(0.08f) else Color.White.copy(0.10f)),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.onPresetChanged(preset) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                preset.label,
                                style = TextStyle(
                                    if (isSelected) accent else text,
                                    14.sp,
                                    FontWeight.SemiBold
                                )
                            )
                            BasicText(
                                preset.subtitle,
                                style = TextStyle(sub, 12.sp)
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), accent)
                        }
                    }
                }

                // If Custom Quality selected, show slider (10% to 90%)
                if (state.selectedPreset == CompressionPreset.CUSTOM_QUALITY) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                "JPEG Quality",
                                style = TextStyle(text, 14.sp, FontWeight.Medium)
                            )
                            GlassChip(
                                "${(state.customSliderQuality * 100).toInt()}%",
                                accent
                            )
                        }

                        LiquidSlider(
                            value = { state.customSliderQuality },
                            onValueChange = { v -> viewModel.onQualitySliderChanged(v) },
                            valueRange = 0.10f..0.90f,
                            visibilityThreshold = 0.01f,
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicText("10% (Smallest size)", style = TextStyle(sub, 11.sp))
                            BasicText("90% (Highest quality)", style = TextStyle(sub, 11.sp))
                        }
                    }
                }

                // Estimated Output Size
                if (state.estimatedSizeBytes > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                "Estimated Output Size:",
                                style = TextStyle(sub, 12.sp, FontWeight.Medium)
                            )
                            BasicText(
                                "~ ${formatFileSize(state.estimatedSizeBytes)}",
                                style = TextStyle(accent, 13.sp, FontWeight.Bold)
                            )
                        }
                    }
                }

                // Compress Action Button
                LiquidButton(
                    onClick = {
                        if (!state.isCompressing) viewModel.onCompress(context)
                    },
                    backdrop = backdrop,
                    tint = accent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        if (state.isCompressing) {
                            CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                            BasicText(
                                state.compressionProgressText.ifEmpty { "Compressing..." },
                                style = TextStyle(Color.White, 15.sp, FontWeight.Medium)
                            )
                        } else {
                            Icon(Icons.Rounded.Compress, null, Modifier.size(18.dp), Color.White)
                            BasicText(
                                "Compress Now",
                                style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // Output Comparison Card
            if (state.compressedSizeBytes > 0 && state.lastOutputUri != null) {
                val origKb = state.originalSizeBytes / 1024
                val compKb = state.compressedSizeBytes / 1024
                val percentSaved = if (state.originalSizeBytes > 0) {
                    ((state.originalSizeBytes - state.compressedSizeBytes).toFloat() / state.originalSizeBytes * 100).toInt().coerceIn(0, 99)
                } else 0
                val savedBytes = (state.originalSizeBytes - state.compressedSizeBytes).coerceAtLeast(0L)

                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(26.dp), accent)
                        BasicText(
                            "Compression Complete!",
                            style = TextStyle(text, 18.sp, FontWeight.Bold)
                        )
                    }

                    // Comparison Card: Original Size -> New Size
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText("Original Size", style = TextStyle(sub, 12.sp))
                            Spacer(Modifier.height(4.dp))
                            BasicText(formatFileSize(state.originalSizeBytes), style = TextStyle(text, 15.sp, FontWeight.Bold))
                        }

                        Icon(Icons.Rounded.ArrowBackIosNew, null, Modifier.size(16.dp).clip(CircleShape), sub)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText("Compressed Size", style = TextStyle(sub, 12.sp))
                            Spacer(Modifier.height(4.dp))
                            BasicText(formatFileSize(state.compressedSizeBytes), style = TextStyle(accent, 15.sp, FontWeight.Bold))
                        }
                    }

                    // Savings badge
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(accent.copy(alpha = 0.20f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            "Reduced by $percentSaved% (${formatFileSize(savedBytes)} saved)",
                            style = TextStyle(accent, 13.sp, FontWeight.Bold)
                        )
                    }

                    // Direct Action Options: Open in Viewer, Share, Done
                    LiquidButton(
                        onClick = { onViewOutput(state.lastOutputUri!!) },
                        backdrop = backdrop,
                        tint = blueAccent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), Color.White)
                            BasicText(
                                "Open in Viewer",
                                style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                            )
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidButton(
                            onClick = { shareCompressedPdf(state.lastOutputUri!!) },
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
                                BasicText(
                                    "Share PDF",
                                    style = TextStyle(Color.White, 14.sp, FontWeight.Medium)
                                )
                            }
                        }

                        LiquidButton(
                            onClick = onBack,
                            backdrop = backdrop,
                            tint = accent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.Done, null, Modifier.size(16.dp), Color.White)
                                BasicText(
                                    "Done",
                                    style = TextStyle(Color.White, 14.sp, FontWeight.Medium)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.errorMessage != null) {
            com.chethan616.clearpdf.ui.components.LiquidGlassErrorCard(
                message = state.errorMessage!!,
                backdrop = backdrop,
                uiSensor = uiSensor,
                onDismiss = { viewModel.clearFeedback() }
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
