@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.untis

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.LGerman
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.core.MeldoCalendar
import app.meldeplanner.android.data.UntisAbsenceRecord
import app.meldeplanner.android.model.setExcuseDetails
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.TimeText
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.rows.meldoAnimateItem
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.ActionRow
import app.meldeplanner.android.ui.rows.SwitchRow
import app.meldeplanner.android.ui.rows.ValueRow
import app.meldeplanner.android.ui.rows.formatDate
import app.meldeplanner.android.ui.navigation.MeldoScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

/**
 * Entschuldigung for an absence: filled in from WebUntis and the student's details,
 * rendered as an A4 PDF to print or send. Works for any school: the school name,
 * salutation and signer are filled in or editable. Port of ExcuseNoteView.swift.
 */
@Composable
fun ExcuseNoteScreen(absence: UntisAbsenceRecord) {
    val model = LocalAppModel.current
    val context = LocalContext.current
    val p = model.preferences
    var school by rememberSaveable { mutableStateOf(model.data.untis?.schoolName ?: "") }
    var name by rememberSaveable { mutableStateOf(p.studentName) }
    var className by rememberSaveable { mutableStateOf(p.className) }
    var salutation by rememberSaveable { mutableStateOf(p.excuseSalutation) }
    var reason by rememberSaveable {
        mutableStateOf(listOf(absence.reason, absence.text).filter { it.isNotEmpty() }.joinToString(", "))
    }
    var adult by rememberSaveable { mutableStateOf(p.adultStudent) }
    var twoCopies by rememberSaveable { mutableStateOf(true) }
    var pdf by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    // The form speaks the app language; the PDF is always German.
    val period = remember(absence, Localization.language) { ExcuseNote.period(absence, model.calendar, german = false) }
    val pdfPeriod = remember(absence) { ExcuseNote.period(absence, model.calendar) }

    fun create() {
        model.setExcuseDetails(name, className, salutation, adult)
        val note = ExcuseNote(
            school = school, name = name, className = className,
            salutation = salutation.ifEmpty { ExcuseNote.defaultSalutation },
            period = pdfPeriod, reason = reason, adult = adult,
        )
        val file = note.write(context, twoCopies, absence.firstDay, model.calendar)
        if (file != null) {
            pdf = file
            problem = null
        } else {
            problem = L("Das PDF konnte nicht gespeichert werden.")
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(pdf) {
        val file = pdf ?: return@LaunchedEffect
        preview = withContext(Dispatchers.IO) { runCatching { renderFirstPage(file) }.getOrNull() }
        // The preview is below the form: show it, so "PDF erstellen" visibly did something.
        if (preview != null) listState.animateScrollToItem(if (problem != null) 3 else 2)
    }

    MeldoScreen(
        title = L("Entschuldigung"),
        largeTitle = false,
        actions = {
            // The screen's one primary action.
            Button(
                onClick = ::create,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.SmallContentPadding,
                modifier = Modifier.padding(end = DS.spacingS),
            ) { Text(L("PDF erstellen"), fontWeight = FontWeight.SemiBold) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().imePadding(), state = listState, contentPadding = padding) {
            item(key = "details") {
                FormSection(header = L("Angaben"), footer = L("Name und Klasse merkt sich Meldo für das nächste Mal.")) {
                    Field(school, { school = it }, L("Schule"))
                    Field(name, { name = it }, L("Dein Name"), words = true)
                    Field(className, { className = it }, L("Klasse"))
                }
            }
            item(key = "note") {
                FormSection(
                    footer = if (adult) {
                        L("Du unterschreibst die Entschuldigung selbst.")
                    } else {
                        L("Unterschreiben muss eine erziehungsberechtigte Person.")
                    },
                ) {
                    ValueRow(L("Fehlzeit"), period)
                    Field(reason, { reason = it }, L("Grund, z. B. Krankheit"), singleLine = false)
                    Field(salutation, { salutation = it }, L("Anrede"), placeholder = ExcuseNote.defaultSalutation)
                    SwitchRow(L("Ich bin volljährig"), adult) { adult = it }
                    SwitchRow(L("Zwei Exemplare pro Seite"), twoCopies) { twoCopies = it }
                }
            }
            problem?.let { message ->
                item(key = "problem") {
                    Row(
                        meldoAnimateItem().fillMaxWidth().padding(horizontal = DS.spacingXL, vertical = DS.spacingM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(sfSymbol("exclamationmark.triangle.fill"), null, tint = MaterialTheme.colorScheme.error)
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = DS.spacingS))
                    }
                }
            }
            pdf?.let { file ->
                item(key = "preview") {
                    FormSection(header = L("Vorschau"), modifier = meldoAnimateItem()) {
                        preview?.let {
                            Image(
                                it.asImageBitmap(), L("Vorschau"),
                                Modifier
                                    .fillMaxWidth()
                                    .padding(DS.spacingM)
                                    .clip(RoundedCornerShape(DS.spacingM))
                                    .aspectRatio(PAGE_WIDTH.toFloat() / PAGE_HEIGHT),
                            )
                        }
                        ActionRow(L("Drucken"), symbol = "doc.text") { print(context, file) }
                        ActionRow(L("Teilen"), symbol = "square.and.arrow.up") {
                            if (!share(context, file)) problem = L("Das PDF konnte nicht gespeichert werden.")
                        }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    words: Boolean = false,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(
            capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
        ),
        shape = DS.rowShape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.spacingM, vertical = DS.spacingS),
    )
}

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842

/** The text and layout of the note. Always German, whatever the app language: it goes to a German school. */
data class ExcuseNote(
    val school: String,
    val name: String,
    val className: String,
    val salutation: String,
    val period: String,
    val reason: String,
    val adult: Boolean,
) {
    companion object {
        val defaultSalutation: String get() = LGerman("Sehr geehrte Damen und Herren,")

        /** "am 29.09.2026 (09:35–09:50 Uhr)" or "vom 24.09. bis 25.09.2026". */
        fun period(absence: UntisAbsenceRecord, calendar: MeldoCalendar, german: Boolean = true): String {
            val tr: (String, Array<out Any?>) -> String = { key, args -> if (german) LGerman(key, *args) else L(key, *args) }
            val locale = if (german) java.util.Locale.GERMAN else app.meldeplanner.android.core.Localization.locale
            val skeleton = "ddMMyyyy"
            val first = formatDate(absence.firstDay, skeleton, calendar.zone, locale)
            if (calendar.isSameDay(absence.firstDay, absence.lastDay)) {
                return tr("am %@ (%@ Uhr)", arrayOf(first, TimeText.range(absence.startMinute, absence.endMinute)))
            }
            return tr("vom %@ bis %@", arrayOf(first, formatDate(absence.lastDay, skeleton, calendar.zone, locale)))
        }
    }

    private val bodyText: String
        get() {
            if (adult) return LGerman("hiermit bitte ich darum, mein Fehlen %@ zu entschuldigen.", period)
            val child = if (className.isEmpty()) name else "$name ($className)"
            return LGerman("hiermit bitte ich darum, das Fehlen meines Kindes %@ %@ zu entschuldigen.", child, period)
        }

    /** A4 PDF in the app's files, folder Entschuldigungen (iOS: Documents/Entschuldigungen). */
    fun write(context: Context, twoCopies: Boolean, fileDate: java.time.Instant, calendar: MeldoCalendar): File? {
        val document = PdfDocument()
        return try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            val canvas = page.canvas
            draw(canvas, 60f, 50f, PAGE_WIDTH - 120f)
            if (twoCopies) {
                val cut = Paint().apply {
                    color = LIGHT_GRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                }
                canvas.drawLine(40f, PAGE_HEIGHT / 2f, PAGE_WIDTH - 40f, PAGE_HEIGHT / 2f, cut)
                draw(canvas, 60f, PAGE_HEIGHT / 2f + 30f, PAGE_WIDTH - 120f)
            }
            document.finishPage(page)
            val folder = File(context.filesDir, "Entschuldigungen").apply { mkdirs() }
            val day = calendar.localDate(fileDate).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val file = File(folder, "${LGerman("Entschuldigung")} $day.pdf")
            val temp = File(folder, "${file.name}.tmp")
            FileOutputStream(temp).use { document.writeTo(it) }
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) return null
            }
            file
        } catch (e: Exception) {
            null
        } finally {
            document.close()
        }
    }

    private fun draw(canvas: android.graphics.Canvas, x: Float, top: Float, width: Float) {
        val serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        var y = top

        fun line(text: String, typeface: Typeface, size: Float, color: Int = BLACK, after: Float = 12f) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = size
                this.color = color
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(3f, 1f)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + after
        }

        fun signature(label: String) {
            y += 18f
            val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BLACK
                style = Paint.Style.STROKE
                strokeWidth = 0.6f
            }
            canvas.drawLine(x, y, x + 200f, y, pen)
            y += 4f
            line(label, serif, 10f, DARK_GRAY, after = 10f)
        }

        if (school.isNotEmpty()) line(school, serif, 10f, DARK_GRAY, after = 2f)
        if (name.isNotEmpty()) line(if (className.isEmpty()) name else "$name · $className", serif, 10f, DARK_GRAY, after = 14f)
        line(LGerman("Entschuldigung"), serifBold, 12f)
        line(salutation, serif, 12f)
        line(bodyText, serif, 12f)
        val because = reason.ifEmpty { "______________________________" }
        line(LGerman("Grund für das Fehlen: %@", because), serif, 12f)
        line(LGerman("Ich bitte um Verständnis."), serif, 12f)
        line(LGerman("Mit freundlichen Grüßen"), serif, 12f, after = 4f)
        signature(LGerman("Ort, Datum"))
        signature(if (adult) LGerman("Unterschrift") else LGerman("Unterschrift der/des Erziehungsberechtigten"))
    }
}

// UIColor.black / .darkGray / .lightGray.
private const val BLACK = 0xFF000000.toInt()
private const val DARK_GRAY = 0xFF555555.toInt()
private const val LIGHT_GRAY = 0xFFAAAAAA.toInt()

/** The first page at twice the PDF size, for the preview. */
private fun renderFirstPage(file: File): Bitmap =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

/** Share sheet with the PDF, through the app's FileProvider ("<applicationId>.files"). */
private fun share(context: Context, file: File): Boolean {
    // Needs <files-path path="Entschuldigungen/"> in res/xml/meldo_file_paths.xml.
    val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull() ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { context.startActivity(Intent.createChooser(send, file.nameWithoutExtension)) }.isSuccess
}

/** Android's print dialog with the finished PDF. */
private fun print(context: Context, file: File) {
    val manager = context.getSystemService(PrintManager::class.java) ?: return
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback, extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
    manager.print(file.nameWithoutExtension, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}
