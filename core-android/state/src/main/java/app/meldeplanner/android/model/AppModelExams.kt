package app.meldeplanner.android.model

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.DocumentCategory
import app.meldeplanner.android.data.ExamDocument
import app.meldeplanner.android.data.newID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

// Dokumente: PDFs in filesDir/Scans/<Fach>/<Exams|Worksheets|Documents>, mirrored
// into the public Documents/Meldo/Scans folder so they show in the Files app (iOS:
// Documents/Scans, visible under "Auf meinem iPhone › Meldo"); metadata in AppData.
// Port of AppModel+Exams.swift.

class ExamException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { unreadablePDF, storage }
}

/** Sorted newest first. */
val AppModel.exams: List<ExamDocument> get() = data.exams.sortedByDescending { it.date }

fun AppModel.exam(id: String): ExamDocument? = data.exams.firstOrNull { it.id == id }

/** The master copy of the PDF, `null` without a context (unit tests). */
fun AppModel.file(exam: ExamDocument): File? = context?.let { ExamFiles.locate(it, exam.fileName) }

/** Stores the PDF (and its Files-app copy) and records the exam. */
suspend fun AppModel.addExam(
    pdf: ByteArray,
    title: String,
    subjectID: String?,
    date: Instant,
    grade: String,
    notes: String,
    category: DocumentCategory = DocumentCategory.exam,
): ExamDocument {
    val context = context ?: throw ExamException(ExamException.Reason.storage)
    val id = newID()
    val subjectName = subjectID?.let { subject(it)?.name }
    val folder = ExamFiles.relativeFolder(subjectName, category)
    val fileName = withContext(Dispatchers.IO) {
        val name = folder + "/" + ExamFiles.readableName(context, title, null, date, id, folder, calendar)
        val file = File(ExamFiles.folder(context), name)
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, ".${file.name}.tmp")
            temp.writeBytes(pdf)
            if (!temp.renameTo(file)) {
                temp.delete()
                throw ExamException(ExamException.Reason.storage)
            }
        } catch (e: ExamException) {
            throw e
        } catch (e: Exception) {
            throw ExamException(ExamException.Reason.storage)
        }
        name
    }
    val file = File(ExamFiles.folder(context), fileName)
    val pages = withContext(Dispatchers.IO) { ExamFiles.pageCount(file) }
    if (pages == null) {
        file.delete()
        throw ExamException(ExamException.Reason.unreadablePDF)
    }
    val exam = ExamDocument(
        id = id,
        title = title.ifBlank { L("Prüfung") },
        subjectID = subjectID, date = date, fileName = fileName,
        pageCount = pages, grade = grade, notes = notes, category = category,
    )
    mutate { it.exams.add(exam) }
    withContext(Dispatchers.IO) { ScanMirror.write(context, exam.id, fileName, file) }
    return exam
}

fun AppModel.update(exam: ExamDocument) {
    val e = exam.copy(updatedAt = currentDate)
    mutate { d ->
        val i = d.exams.indexOfFirst { it.id == e.id }
        if (i >= 0) d.exams[i] = e
    }
    organizeDocuments()
}

/**
 * Moves every PDF into Scans/<Fach>/<Kategorie> (after a subject or category
 * change or a renamed subject) and keeps the Files-app copies in step.
 */
fun AppModel.organizeDocuments() {
    val context = context ?: return
    val root = ExamFiles.folder(context)
    val moved = mutableMapOf<String, String>()
    for (exam in data.exams) {
        val folder = ExamFiles.relativeFolder(exam.subjectID?.let { subject(it)?.name }, exam.kind)
        val current = exam.fileName.substringBeforeLast('/', "")
        if (current == folder) continue
        val source = ExamFiles.locate(context, exam.fileName) ?: continue
        if (!source.exists()) continue
        val name = ExamFiles.readableName(context, exam.title, null, exam.date, exam.id, folder, calendar)
        val target = File(root, "$folder/$name")
        target.parentFile?.mkdirs()
        // rename replaces an existing file: never move onto another exam's PDF.
        if (!target.exists() && source.renameTo(target)) moved[exam.id] = "$folder/$name"
    }
    if (moved.isNotEmpty()) {
        mutate { d ->
            for (e in d.exams) moved[e.id]?.let { e.fileName = it }
        }
    }
    val snapshot = data.exams.map { it.id to it.fileName }
    scope.launch(Dispatchers.IO) {
        for ((id, fileName) in snapshot) {
            val file = ExamFiles.locate(context, fileName) ?: continue
            if (!file.exists()) continue
            if (id in moved) ScanMirror.move(context, id, fileName, file) else ScanMirror.ensure(context, id, fileName, file)
        }
    }
}

fun AppModel.deleteExam(id: String) {
    val context = context
    val exam = exam(id)
    // Another exam can use the same file name (merged from another device): keep the file then, as iOS does.
    val shared = exam != null && data.exams.any { it.id != id && it.fileName == exam.fileName }
    if (context != null && exam != null && !shared) {
        ExamFiles.locate(context, exam.fileName)?.delete()
        scope.launch(Dispatchers.IO) { ScanMirror.remove(context, id) }
    }
    mutate { d -> d.exams.removeAll { it.id == id } }
}

object ExamFiles {
    private val unsafe = Regex("[/\\\\:?%*|\"<>\\r\\n]")

    /** filesDir/Scans, the master copies. */
    fun folder(context: Context): File = File(context.filesDir, "Scans").apply { mkdirs() }

    /** "Mathematik/Exams"; documents without a subject go to "Other". */
    fun relativeFolder(subject: String?, category: DocumentCategory): String {
        val name = safe(subject ?: "").ifEmpty { "Other" }
        return name + "/" + category.folderName
    }

    /** No characters file managers can't take, no leading dot. */
    private fun safe(text: String): String = unsafe.replace(text, "-").trim().trimStart('.')

    /** The file for a stored name, or null when the name could leave the Scans folder. */
    fun locate(context: Context, fileName: String): File? = if (isSafe(fileName)) File(folder(context), fileName) else null

    /** A relative path without empty, "." or ".." parts (same rule as iOS). */
    fun isSafe(fileName: String): Boolean {
        val parts = fileName.split('/')
        return !fileName.startsWith("/") && !fileName.contains('\\') && parts.none { it.isEmpty() || it == "." || it == ".." }
    }

    /** "2. Klassenarbeit (12.10.2026).pdf", unique in its folder. */
    fun readableName(
        context: Context,
        title: String,
        subject: String?,
        date: Instant,
        id: String,
        relativeFolder: String,
        calendar: MeldoCalendar,
    ): String {
        val pattern = if (Localization.language.code == "en") "MM/dd/yyyy" else "dd.MM.yyyy"
        val day = calendar.localDate(date).format(DateTimeFormatter.ofPattern(pattern, Localization.locale))
        val parts = listOfNotNull(subject, title.trim()).filter { it.isNotEmpty() }
        val base = (if (parts.isEmpty()) L("Prüfung") else parts.joinToString(" – ")) + " ($day)"
        val safe = unsafe.replace(base, "-").trimStart('.')
        var name = "$safe.pdf"
        val dir = File(folder(context), relativeFolder)
        if (File(dir, name).exists()) name = "$safe ${id.take(4)}.pdf"
        // The short ID can collide too: then the full ID, which is unique.
        if (File(dir, name).exists()) name = "$safe $id.pdf"
        return name
    }

    /** Number of pages, `null` if the file isn't a readable PDF. */
    fun pageCount(file: File): Int? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val renderer = PdfRenderer(fd)
            try {
                renderer.pageCount.takeIf { it > 0 }
            } finally {
                renderer.close()
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Page count of PDF bytes (imported files), via a temporary file. */
    fun pageCount(context: Context, pdf: ByteArray): Int? {
        val temp = File.createTempFile("import", ".pdf", context.cacheDir)
        return try {
            temp.writeBytes(pdf)
            pageCount(temp)
        } catch (e: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    /** A4 width in PostScript points; pages keep the photo's aspect ratio. */
    private const val PAGE_WIDTH = 595

    /**
     * Builds a PDF from photos (already downscaled to at most 2200 px on the
     * longest side), one page per image in the order picked.
     */
    fun pdf(images: List<Bitmap>): ByteArray? {
        if (images.isEmpty()) return null
        val document = PdfDocument()
        return try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            images.forEachIndexed { index, image ->
                val height = (PAGE_WIDTH * image.height.toFloat() / image.width).toInt().coerceAtLeast(1)
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, height, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(image, null, RectF(0f, 0f, PAGE_WIDTH.toFloat(), height.toFloat()), paint)
                document.finishPage(page)
            }
            ByteArrayOutputStream().use { out ->
                document.writeTo(out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        } finally {
            document.close()
        }
    }
}

/**
 * The Files-app copies in Documents/Meldo/Scans/<Fach>/<Category>/, written
 * through MediaStore (no storage permission). The content URI of each copy is
 * remembered per document so renames, moves and deletes reach the right file.
 */
object ScanMirror {
    private const val ROOT = "Documents/Meldo/Scans/"
    private const val PREFS = "meldo_scan_mirrors"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @get:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private val collection: Uri get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun location(fileName: String): Pair<String, String> {
        val folder = fileName.substringBeforeLast('/', "")
        val name = fileName.substringAfterLast('/')
        return (ROOT + if (folder.isEmpty()) "" else "$folder/") to name
    }

    private fun exists(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } == true
    } catch (e: Exception) {
        false
    }

    // MediaStore's relative paths exist from Android 10; before that writing to Documents
    // needs a storage permission Meldo doesn't ask for, so there is no Files copy.

    /** Creates (or replaces) the copy of [file]. */
    fun write(context: Context, examID: String, fileName: String, file: File) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        remove(context, examID)
        val (path, name) = location(fileName)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            null
        } ?: return
        try {
            resolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            prefs(context).edit().putString(examID, uri.toString()).apply()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    /** Moves or renames the copy after the master moved; recreates it if it is gone. */
    fun move(context: Context, examID: String, fileName: String, file: File) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val uri = prefs(context).getString(examID, null)?.let(Uri::parse)
        if (uri == null || !exists(context, uri)) return write(context, examID, fileName, file)
        val (path, name) = location(fileName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
        }
        val updated = try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: Exception) {
            false
        }
        if (!updated) write(context, examID, fileName, file)
    }

    /**
     * Creates the copy for documents that never had one. A copy the student
     * deleted in the Files app is not brought back.
     */
    fun ensure(context: Context, examID: String, fileName: String, file: File) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        if (prefs(context).contains(examID)) return
        write(context, examID, fileName, file)
    }

    fun remove(context: Context, examID: String) {
        val uri = prefs(context).getString(examID, null)?.let(Uri::parse) ?: return
        runCatching { context.contentResolver.delete(uri, null, null) }
        prefs(context).edit().remove(examID).apply()
    }
}
