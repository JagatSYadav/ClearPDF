package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.LocalDocumentMirror
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.domain.usecase.CompressPdfUseCase
import com.chethan616.clearpdf.ui.utils.AppDispatchers
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

enum class CompressionPreset(val label: String, val subtitle: String, val targetBytes: Long?) {
    TARGET_100KB("Target 100 KB", "High compression for government portals", 100 * 1024L),
    TARGET_200KB("Target 200 KB", "Standard web form size", 200 * 1024L),
    TARGET_500KB("Target 500 KB", "Balanced document quality", 500 * 1024L),
    CUSTOM_QUALITY("Custom Quality", "Select 10% to 90% JPEG quality", null)
}

data class CompressPdfUiState(
    val sourceFileName: String = "",
    val sourceUri: Uri? = null,
    val originalSizeBytes: Long = 0,
    val pageCount: Int = 0,
    val selectedPreset: CompressionPreset = CompressionPreset.TARGET_200KB,
    val customSliderQuality: Float = 0.60f,
    val estimatedSizeBytes: Long = -1,
    val isCompressing: Boolean = false,
    val compressionProgressText: String = "",
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val compressedSizeBytes: Long = -1,
    val lastOutputUri: Uri? = null,
    val lastOutputFile: File? = null,
    val recentPdfs: List<RecentFile> = emptyList()
)

class CompressPdfViewModel(
    @Suppress("UNUSED_PARAMETER") private val compressPdfUseCase: CompressPdfUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompressPdfUiState())
    val uiState: StateFlow<CompressPdfUiState> = _uiState.asStateFlow()

    fun loadRecentPdfs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val recents = RecentFilesManager.getRecents(context)
                .filter { it.name.endsWith(".pdf", ignoreCase = true) }
            _uiState.value = _uiState.value.copy(recentPdfs = recents)
        }
    }

    fun onSelectFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(AppDispatchers.pdf) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                }

                val fileName = queryFileName(context, uri) ?: "Selected.pdf"
                var fileSize = queryFileSize(context, uri)
                var pages = 0

                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            if (fileSize <= 0) {
                                fileSize = pfd.statSize
                            }
                            PdfRenderer(pfd).use { renderer ->
                                pages = renderer.pageCount
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _uiState.value = _uiState.value.copy(
                    sourceFileName = fileName,
                    sourceUri = uri,
                    originalSizeBytes = fileSize.coerceAtLeast(0L),
                    pageCount = pages.coerceAtLeast(1),
                    errorMessage = null,
                    resultMessage = null,
                    lastOutputUri = null,
                    compressedSizeBytes = -1
                )

                updateEstimate()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = context.getString(R.string.open_pdf_failed)
                )
            }
        }
    }

    fun onPresetChanged(preset: CompressionPreset) {
        _uiState.value = _uiState.value.copy(selectedPreset = preset)
        updateEstimate()
    }

    fun onQualitySliderChanged(value: Float) {
        val clamped = value.coerceIn(0.10f, 0.90f)
        _uiState.value = _uiState.value.copy(
            customSliderQuality = clamped,
            selectedPreset = CompressionPreset.CUSTOM_QUALITY
        )
        updateEstimate()
    }

    private fun updateEstimate() {
        val current = _uiState.value
        val orig = current.originalSizeBytes
        val pages = current.pageCount.coerceAtLeast(1)
        if (orig <= 0) {
            _uiState.value = current.copy(estimatedSizeBytes = -1)
            return
        }

        val estimated: Long = when (current.selectedPreset) {
            CompressionPreset.TARGET_100KB -> {
                val target = 100 * 1024L
                if (orig < target) min(orig, (orig * 0.85f).toLong()) else (target * 0.95f).toLong()
            }
            CompressionPreset.TARGET_200KB -> {
                val target = 200 * 1024L
                if (orig < target) min(orig, (orig * 0.88f).toLong()) else (target * 0.95f).toLong()
            }
            CompressionPreset.TARGET_500KB -> {
                val target = 500 * 1024L
                if (orig < target) min(orig, (orig * 0.90f).toLong()) else (target * 0.95f).toLong()
            }
            CompressionPreset.CUSTOM_QUALITY -> {
                val q = current.customSliderQuality
                val perPageEstimate = (40_000L + (q * 160_000L).toLong())
                val totalEstimated = perPageEstimate * pages
                min(orig, (totalEstimated * 0.90f).toLong()).coerceAtLeast(20_000L)
            }
        }

        _uiState.value = current.copy(estimatedSizeBytes = estimated)
    }

    fun onCompress(context: Context) {
        val srcUri = _uiState.value.sourceUri ?: return
        val origSize = _uiState.value.originalSizeBytes
        val preset = _uiState.value.selectedPreset
        val customQuality = _uiState.value.customSliderQuality

        _uiState.value = _uiState.value.copy(
            isCompressing = true,
            compressionProgressText = "Preparing document...",
            errorMessage = null,
            resultMessage = null,
            lastOutputUri = null,
            compressedSizeBytes = -1
        )

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    performCompression(context, srcUri, origSize, preset, customQuality)
                }

                val origKb = origSize / 1024
                val compKb = result.fileSize / 1024
                val reduction = if (origSize > 0) {
                    ((origSize - result.fileSize).toFloat() / origSize * 100).toInt().coerceIn(0, 99)
                } else 0

                _uiState.value = _uiState.value.copy(
                    isCompressing = false,
                    compressionProgressText = "",
                    compressedSizeBytes = result.fileSize,
                    lastOutputUri = result.uri,
                    lastOutputFile = result.file,
                    resultMessage = "Compressed: ${origKb}KB -> ${compKb}KB ($reduction% smaller)"
                )

                if (GitHubStarPromptManager.recordPdfInteraction(context)) {
                    StarPromptEventBus.requestPrompt()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isCompressing = false,
                    compressionProgressText = "",
                    errorMessage = e.localizedMessage ?: context.getString(R.string.compression_failed)
                )
            }
        }
    }

    private data class CompressionResult(val uri: Uri, val file: File, val fileSize: Long)

    private fun performCompression(
        context: Context,
        srcUri: Uri,
        origSize: Long,
        preset: CompressionPreset,
        customQuality: Float
    ): CompressionResult {
        val pfd = context.contentResolver.openFileDescriptor(srcUri, "r")
            ?: throw IllegalArgumentException("Cannot open source PDF")

        pfd.use { fd ->
            val renderer = PdfRenderer(fd)
            val pageCount = renderer.pageCount
            val outDoc = PdfDocument()

            // Calculate scale and quality based on settings
            val targetBytes = preset.targetBytes
            val (scaleFactor, jpegQuality) = if (targetBytes != null) {
                val pageBudget = (targetBytes * 0.90f) / pageCount.coerceAtLeast(1)
                when {
                    pageBudget < 25_000 -> Pair(0.45f, 32)
                    pageBudget < 50_000 -> Pair(0.55f, 44)
                    pageBudget < 100_000 -> Pair(0.68f, 58)
                    pageBudget < 200_000 -> Pair(0.80f, 70)
                    else -> Pair(0.92f, 82)
                }
            } else {
                val q = (customQuality * 100).toInt().coerceIn(10, 90)
                val s = (0.40f + (customQuality - 0.10f) * 0.70f).coerceIn(0.40f, 0.95f)
                Pair(s, q)
            }

            val matrix = Matrix()
            val stream = ByteArrayOutputStream(256 * 1024)
            var reusableBitmap: Bitmap? = null

            try {
                for (i in 0 until pageCount) {
                    _uiState.value = _uiState.value.copy(
                        compressionProgressText = "Compressing page ${i + 1} of $pageCount..."
                    )

                    val srcPage = renderer.openPage(i)
                    try {
                        val origW = srcPage.width
                        val origH = srcPage.height

                        // Calculate scaled dimensions and prevent OOM
                        var w = (origW * scaleFactor).toInt().coerceAtLeast(1)
                        var h = (origH * scaleFactor).toInt().coerceAtLeast(1)
                        val maxDim = 1800
                        if (w > maxDim || h > maxDim) {
                            val downscale = maxDim.toFloat() / maxOf(w, h)
                            w = (w * downscale).toInt().coerceAtLeast(1)
                            h = (h * downscale).toInt().coerceAtLeast(1)
                        }

                        val actualScaleX = w.toFloat() / origW
                        val actualScaleY = h.toFloat() / origH

                        val workingBitmap = if (reusableBitmap != null && !reusableBitmap.isRecycled &&
                            reusableBitmap.width == w && reusableBitmap.height == h
                        ) {
                            reusableBitmap
                        } else {
                            reusableBitmap?.recycle()
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                reusableBitmap = it
                            }
                        }

                        workingBitmap.eraseColor(Color.WHITE)
                        matrix.reset()
                        matrix.setScale(actualScaleX, actualScaleY)

                        srcPage.render(
                            workingBitmap,
                            null,
                            matrix,
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                        )

                        // Compress rendered page bitmap into JPEG format
                        stream.reset()
                        workingBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                        val compressedBytes = stream.toByteArray()

                        val compressedBitmap = android.graphics.BitmapFactory.decodeByteArray(
                            compressedBytes,
                            0,
                            compressedBytes.size
                        ) ?: throw IllegalStateException("Failed to decode compressed page ${i + 1}")

                        val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, i).create()
                        val page = outDoc.startPage(pageInfo)
                        val destRect = RectF(0f, 0f, origW.toFloat(), origH.toFloat())
                        page.canvas.drawBitmap(compressedBitmap, null, destRect, null)
                        outDoc.finishPage(page)
                        compressedBitmap.recycle()
                    } finally {
                        srcPage.close()
                    }
                }

                // Save directly into internal storage: context.filesDir/documents/
                val docDir = File(context.filesDir, "documents").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "COMPRESSED_$timeStamp.pdf"
                val outputFile = File(docDir, fileName)

                outputFile.outputStream().use { fos ->
                    outDoc.writeTo(fos)
                    fos.flush()
                }

                val outUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    outputFile
                )

                // Register into RecentFilesManager immediately
                RecentFilesManager.addRecent(
                    context,
                    RecentFile(
                        name = fileName,
                        uriString = outUri.toString(),
                        timestamp = System.currentTimeMillis(),
                        pageCount = pageCount,
                        sizeBytes = outputFile.length()
                    )
                )

                LocalDocumentMirror.resolve(context, outUri, "pdf")

                return CompressionResult(outUri, outputFile, outputFile.length())
            } finally {
                reusableBitmap?.recycle()
                outDoc.close()
                renderer.close()
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(resultMessage = null, errorMessage = null)
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }
}
