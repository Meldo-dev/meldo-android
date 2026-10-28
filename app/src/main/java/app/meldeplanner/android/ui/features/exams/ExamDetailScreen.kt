@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.exams

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.meldeplanner.android.ui.rows.extend
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.ExamDocument
import app.meldeplanner.android.model.exam
import app.meldeplanner.android.model.file
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.tasks.EmptyState
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One document: its PDF, with Teilen and Details. Port of ExamDetailView. */
@Composable
fun ExamDetailScreen(examID: String, onDismiss: (() -> Unit)? = null) {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val exam = model.exam(examID)
    val file = exam?.let { model.file(it) }
    if (exam == null || file == null || !file.exists()) {
        MeldoScreen(L("Dokumente"), largeTitle = false, onBack = onDismiss) { padding ->
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
                EmptyState("doc.questionmark", L("Prüfung nicht gefunden"))
            }
        }
        return
    }
    MeldoScreen(
        exam.title,
        largeTitle = false,
        onBack = onDismiss,
        actions = {
            IconButton(onClick = { share(context, exam, file) }, shapes = IconButtonDefaults.shapes()) {
                Icon(sfSymbol("square.and.arrow.up"), L("Teilen"))
            }
            IconButton(
                onClick = { navigator.push { ExamEditorScreen(ExamEditorMode.Existing(exam), onDismiss = { navigator.pop() }) } },
                shapes = IconButtonDefaults.shapes(),
            ) { Icon(sfSymbol("info.circle"), L("Details")) }
        },
    ) { padding ->
        // Pages scroll under the translucent bars.
        PdfView(file, Modifier.fillMaxSize(), contentPadding = padding)
    }
}

/** `ShareLink(item: url)`: the PDF through the share sheet. */
private fun share(context: Context, exam: ExamDocument, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, exam.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, exam.title))
    } catch (e: ActivityNotFoundException) {
        // No app can receive the PDF; nothing to share to.
    }
}

/** An open PDF with its page sizes, rendered page by page (PdfRenderer isn't thread-safe). */
private class OpenPdf(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Mutex()
    val pageSizes: List<Pair<Int, Int>> = (0 until renderer.pageCount).map { i ->
        renderer.openPage(i).use { it.width to it.height }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, widthPx * 20)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

/**
 * Read-only PDF viewer (iOS: PDFView, single page continuous): pages in a
 * vertical list, pinch to zoom, double tap to zoom in or back out.
 */
@Composable
fun PdfView(file: File, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val pdf = remember(file) { runCatching { OpenPdf(file) }.getOrNull() }
    DisposableEffect(pdf) { onDispose { pdf?.close() } }
    if (pdf == null) {
        Box(modifier.padding(contentPadding)) { EmptyState("doc.questionmark", L("Diese Datei ist kein lesbares PDF.")) }
        return
    }
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offsetX by remember(file) { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .pointerInput(file) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offsetX = 0f
                    } else {
                        scale = 2f
                    }
                })
            }
            .pointerInput(file) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            val limit = (scale - 1f) * size.width / 2f
                            offsetX = (offsetX + event.calculatePan().x).coerceIn(-limit, limit)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val pagePadding = DS.spacingM
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            ((maxWidth - pagePadding * 2).toPx() * 2f).toInt().coerceIn(200, 2400)
        }
        LazyColumn(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
            },
            contentPadding = contentPadding.extend(horizontal = pagePadding, top = pagePadding, bottom = pagePadding),
            verticalArrangement = Arrangement.spacedBy(DS.spacingM),
        ) {
            items(pdf.pageSizes.size) { index ->
                val (w, h) = pdf.pageSizes[index]
                val bitmap by produceState<Bitmap?>(null, pdf, index, widthPx) { value = runCatching { pdf.render(index, widthPx) }.getOrNull() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio((w.coerceAtLeast(1).toFloat() / h.coerceAtLeast(1)).coerceIn(0.05f, 20f))
                        .clip(RoundedCornerShape(DS.spacingS))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .semantics { contentDescription = "${index + 1} / ${pdf.pageSizes.size}" },
                ) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                }
            }
        }
    }
}
