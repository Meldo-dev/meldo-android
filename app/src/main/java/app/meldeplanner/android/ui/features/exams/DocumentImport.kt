package app.meldeplanner.android.ui.features.exams

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.newID
import app.meldeplanner.android.model.ExamFiles
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A scanned or imported PDF waiting for its details. */
class PendingExam(val pdf: ByteArray, val pageCount: Int, val id: String = newID())

/** The three ways to add a document: camera scan, photos, PDF file. */
class DocumentImporter(val scan: () -> Unit, val photos: () -> Unit, val files: () -> Unit)

/**
 * Google's document scanner (edge detection, perspective correction, several
 * pages, the counterpart of VisionKit), the photo picker (photos become one PDF
 * in the order picked) and the system file picker for PDFs.
 */
@Composable
fun rememberDocumentImporter(onPending: (PendingExam) -> Unit, onProblem: (String) -> Unit): DocumentImporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by rememberUpdatedState(onPending)
    val problem by rememberUpdatedState(onProblem)

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf
        scope.launch {
            val bytes = pdf?.let { withContext(Dispatchers.IO) { read(context, it.uri) } }
            if (pdf != null && bytes != null && pdf.pageCount > 0) pending(PendingExam(bytes, pdf.pageCount))
            else problem(L("Der Scan konnte nicht als PDF gespeichert werden."))
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { read(context, uri) }
            if (bytes != null && bytes.size > MAX_IMPORT_BYTES) {
                problem(L("Diese Datei ist zu groß (höchstens 100 MB)."))
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) { bytes?.let { b -> ExamFiles.pageCount(context, b)?.let { b to it } } }
            if (loaded != null) pending(PendingExam(loaded.first, loaded.second))
            else problem(L("Diese Datei ist kein lesbares PDF."))
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                val images = uris.mapNotNull { decode(context, it) }
                ExamFiles.pdf(images)?.let { it to images.size }.also { images.forEach(Bitmap::recycle) }
            }
            if (result != null) pending(PendingExam(result.first, result.second))
            else problem(L("Die Fotos konnten nicht als PDF gespeichert werden."))
        }
    }

    return remember(context) {
        DocumentImporter(
            scan = {
                val activity = context.findActivity()
                if (activity == null) {
                    problem(L("Der Scanner ist auf diesem Gerät nicht verfügbar."))
                } else {
                    val options = GmsDocumentScannerOptions.Builder()
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                        .setGalleryImportAllowed(true)
                        .build()
                    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                        .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                        .addOnFailureListener { problem(L("Der Scanner ist auf diesem Gerät nicht verfügbar.")) }
                }
            },
            photos = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            files = { pdfLauncher.launch(arrayOf("application/pdf")) },
        )
    }
}

/** Larger PDFs are refused instead of read into memory whole. */
private const val MAX_IMPORT_BYTES = 100 * 1024 * 1024

/** The file's bytes, reading at most one byte past [MAX_IMPORT_BYTES] so a huge file is never loaded whole. */
private fun read(context: Context, uri: Uri): ByteArray? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            // InputStream.readNBytes needs Android 13; read in chunks instead.
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() <= MAX_IMPORT_BYTES) {
                val n = input.read(buffer, 0, minOf(buffer.size, MAX_IMPORT_BYTES + 1 - out.size()))
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }.getOrNull()

/** Decodes a photo in software memory (drawable into a PDF), at most 2200 px on the longest side. */
private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return@runCatching decodeLegacy(context, uri)
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > MAX_SIDE) {
            val factor = MAX_SIDE.toFloat() / longest
            decoder.setTargetSize((info.size.width * factor).toInt(), (info.size.height * factor).toInt())
        }
    }
}.getOrNull()

private const val MAX_SIDE = 2200

/** Android 8: BitmapFactory, downsampled by powers of two, then scaled to at most [MAX_SIDE]. */
private fun decodeLegacy(context: Context, uri: Uri): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / (sample * 2) >= MAX_SIDE) sample *= 2
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) } ?: return null
    val side = maxOf(bitmap.width, bitmap.height)
    if (side <= MAX_SIDE) return bitmap
    val factor = MAX_SIDE.toFloat() / side
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
        .also { if (it !== bitmap) bitmap.recycle() }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
