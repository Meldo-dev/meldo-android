@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.grades

import app.meldeplanner.android.ui.theme.MeldoSheet
import app.meldeplanner.android.ui.theme.MeldoDatePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import app.meldeplanner.android.ui.components.rememberReduceMotion
import app.meldeplanner.android.ui.rows.meldoAnimateItem
import app.meldeplanner.android.ui.rows.ActionRow
import app.meldeplanner.android.ui.rows.ChoiceRow
import app.meldeplanner.android.ui.rows.FormItemSurface
import app.meldeplanner.android.ui.rows.NavRow
import app.meldeplanner.android.ui.rows.formItems
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.data.GradeEntry
import app.meldeplanner.android.data.GradeScaleChoice
import app.meldeplanner.android.model.deleteGrade
import app.meldeplanner.android.model.gradeNeeded
import app.meldeplanner.android.model.gradeSummary
import app.meldeplanner.android.model.gradeText
import app.meldeplanner.android.model.grades
import app.meldeplanner.android.model.overallGradeAverage
import app.meldeplanner.android.model.saveGrade
import app.meldeplanner.android.model.setGradeScale
import app.meldeplanner.android.model.setTendencyStep
import app.meldeplanner.android.model.setWrittenWeight
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.SubjectIcon
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.NoteRow
import app.meldeplanner.android.ui.rows.Segmented
import app.meldeplanner.android.ui.rows.SheetBar
import app.meldeplanner.android.ui.rows.StepperRow
import app.meldeplanner.android.ui.rows.ValueRow
import app.meldeplanner.android.ui.rows.formatDate
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import uniffi.melde_ffi.FfiForecast
import uniffi.melde_ffi.FfiForecastOutcome
import uniffi.melde_ffi.FfiTendency
import uniffi.melde_ffi.gradeTraditionalValue
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt


/**
 * Notenrechner: averages per subject and overall from the student's own grades.
 * An estimate; official grades come only from the school. Port of GradesView.swift.
 */
@Composable
fun GradesScreen() {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    var settings by remember { mutableStateOf(false) }
    val overall = model.overallGradeAverage
    val reduceMotion = rememberReduceMotion()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    MeldoScreen(
        title = L("Noten"),
        actions = {
            IconButton(onClick = { settings = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(sfSymbol("slider.horizontal.3"), L("Notensystem"))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "overall") {
                FormSection(footer = L("Deine eigene Rechnung aus den Noten, die du einträgst. Die Zeugnisnote legt deine Schule fest.")) {
                    Column(
                        Modifier.fillMaxWidth().padding(DS.spacingL).semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(DS.spacingXS),
                    ) {
                        Text(
                            L("Geschätzter Schnitt"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AnimatedContent(
                            overall?.let { model.gradeText(it) },
                            transitionSpec = {
                                if (reduceMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(fade) togetherWith fadeOut(fade)
                                }
                            },
                            label = "overall",
                        ) { text ->
                            if (text != null) {
                                Text(
                                    text,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(L("Noch keine Noten"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            if (model.subjects.isEmpty()) {
                item(key = "no-subjects") {
                    FormSection(header = L("Fächer"), modifier = meldoAnimateItem()) {
                        NoteRow(L("Lege zuerst unter „Fächer“ deine Fächer an."))
                    }
                }
            } else {
                formItems(sectionKey = "subjects", items = model.subjects, key = { it.id }, header = L("Fächer")) { subject, shape ->
                    val count = model.grades(subject.id).size
                    val average = model.gradeSummary(subject.id)?.exact
                    FormItemSurface(shape) {
                        ListItem(
                            headlineContent = { Text(subject.name) },
                            supportingContent = { Text(if (count == 0) L("Keine Noten") else L("%lld Noten", count)) },
                            leadingContent = { SubjectIcon(subject) },
                            trailingContent = average?.let { { GradeChip(model.gradeText(it)) } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(role = Role.Button) { navigator.push { SubjectGradesScreen(subject.id) } },
                        )
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
    if (settings) GradeSettingsSheet(onDismiss = { settings = false })
}

/** A grade as a tonal pill (only a label: comparing grades goes through the scale). */
@Composable
private fun GradeChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(horizontal = DS.spacingM, vertical = DS.spacingXS),
    )
}

/** One subject's grades, averages and the "what do I need" forecast. */
@Composable
fun SubjectGradesScreen(subjectID: String) {
    val model = LocalAppModel.current
    val subject = model.subject(subjectID)
    val summary = model.gradeSummary(subjectID)
    val points = model.preferences.gradeScale == GradeScaleChoice.points
    var target by remember(points) { mutableDoubleStateOf(if (points) 10.0 else 2.0) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GradeEntry?>(null) }
    val grades = model.grades(subjectID)

    MeldoScreen(
        title = subject?.name ?: L("Unbekanntes Fach"),
        actions = {
            IconButton(onClick = { adding = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(sfSymbol("plus"), L("Note hinzufügen"))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "stats") {
                FormSection {
                    Row(Modifier.fillMaxWidth().padding(vertical = DS.spacingL)) {
                        Stat(L("Schnitt"), summary?.exact, Modifier.weight(1f), hero = true)
                        Stat(L("Schriftlich"), summary?.writtenAverage, Modifier.weight(1f))
                        Stat(L("Mündlich"), summary?.oralAverage, Modifier.weight(1f))
                    }
                }
            }
            item(key = "forecast") {
                FormSection(header = L("Was brauche ich?"), footer = L("Für die nächste schriftliche Note, mit deiner Gewichtung.")) {
                    val range = if (points) 0.0..15.0 else 1.0..6.0
                    val step = if (points) 1.0 else 0.5
                    StepperRow(
                        title = L("Ziel"),
                        value = model.gradeText(target),
                        canDecrease = target - step >= range.start,
                        canIncrease = target + step <= range.endInclusive,
                        onDecrease = { target = (target - step).coerceIn(range) },
                        onIncrease = { target = (target + step).coerceIn(range) },
                    )
                    model.gradeNeeded(subjectID, target)?.let { NoteRow(forecastText(model, it), MaterialTheme.colorScheme.onSurface) }
                }
            }
            if (grades.isEmpty()) {
                item(key = "no-grades") {
                    FormSection(header = L("Noten"), modifier = meldoAnimateItem()) { NoteRow(L("Noch keine Noten")) }
                }
            } else {
                // Keyed so a swiped-away row's state never carries over to the next grade; rows slide on delete.
                formItems(sectionKey = "grades", items = grades, key = { it.id }, header = L("Noten")) { grade, shape ->
                    GradeRow(grade, shape, onClick = { editing = grade }, onDelete = { model.deleteGrade(grade.id) })
                }
            }
            item(key = "end") { Spacer(Modifier.height(DS.spacingXL)) }
        }
    }
    if (adding) GradeEditorSheet(grade = null, subjectID = subjectID, onDismiss = { adding = false })
    editing?.let { GradeEditorSheet(grade = it, subjectID = subjectID, onDismiss = { editing = null }) }
}

@Composable
private fun Stat(label: String, value: Double?, modifier: Modifier, hero: Boolean = false) {
    val model = LocalAppModel.current
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value?.let { model.gradeText(it) } ?: L("Keine"),
            style = if (hero) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (hero) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeRow(grade: GradeEntry, shape: Shape, onClick: () -> Unit, onDelete: () -> Unit) {
    val model = LocalAppModel.current
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        modifier = Modifier.clip(shape),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, shape).padding(horizontal = DS.spacingL),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(sfSymbol("trash"), L("Löschen"), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        FormItemSurface(shape) {
            ListItem(
                headlineContent = {
                    Text(grade.title.ifEmpty { if (grade.written) L("Schriftlich") else L("Mündlich") })
                },
                supportingContent = { Text(formatDate(grade.date, "dMMMy", model.calendar.zone)) },
                trailingContent = { GradeChip(model.gradeText(grade.value)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
            )
        }
    }
}

private fun forecastText(model: app.meldeplanner.android.model.AppModel, forecast: FfiForecast): String =
    when (val outcome = forecast.outcome) {
        is FfiForecastOutcome.AlreadySecured -> L("Das Ziel hast du sicher, egal wie die nächste Arbeit läuft.")
        is FfiForecastOutcome.Reachable -> L("Du brauchst %@ oder besser.", model.gradeText(outcome.required))
        is FfiForecastOutcome.Unreachable ->
            L("Mit einer Arbeit nicht erreichbar. Bestenfalls %@.", model.gradeText(outcome.bestPossibleAverage))
    }


/** Add or edit a grade: 1–6 with tendency, or 0–15 points. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeEditorSheet(grade: GradeEntry?, subjectID: String, onDismiss: () -> Unit) {
    val model = LocalAppModel.current
    val usesPoints = model.preferences.gradeScale == GradeScaleChoice.points
    // Initial values from the grade being edited (iOS: `load()` on appear).
    val initialWhole = grade?.let { it.value.roundToInt().coerceIn(1, 6) } ?: 2
    var written by remember { mutableStateOf(grade?.written ?: true) }
    var whole by remember { mutableIntStateOf(initialWhole) }
    var tendency by remember {
        mutableIntStateOf(
            grade?.let { g ->
                val diff = g.value - initialWhole
                if (diff < -0.01) 1 else if (diff > 0.01) -1 else 0
            } ?: 0,
        )
    }
    var points by remember { mutableIntStateOf(grade?.value?.roundToInt()?.coerceIn(0, 15) ?: 10) }
    var weight by remember { mutableDoubleStateOf(grade?.weight ?: 1.0) }
    var title by remember { mutableStateOf(grade?.title ?: "") }
    var date by remember { mutableStateOf(grade?.date ?: model.currentDate) }
    var pickingDate by remember { mutableStateOf(false) }

    /** The tendency step comes from the settings (e.g. 2+ = 1.67 with 0.33). */
    val value: Double = if (usesPoints) {
        points.toDouble()
    } else {
        val t = if (tendency > 0) FfiTendency.PLUS else if (tendency < 0) FfiTendency.MINUS else FfiTendency.NONE
        runCatching { gradeTraditionalValue(whole.toUByte(), t, model.preferences.tendencyStep) }.getOrDefault(whole.toDouble())
    }

    MeldoSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = DS.spacingL)) {
            SheetBar(
                title = if (grade == null) L("Neue Note") else L("Note bearbeiten"),
                onCancel = onDismiss,
                confirm = L("Sichern"),
                onConfirm = {
                    model.saveGrade(
                        GradeEntry(
                            id = grade?.id ?: app.meldeplanner.android.data.newID(),
                            subjectID = subjectID, value = value, written = written,
                            weight = weight, title = title, date = date,
                        ),
                    )
                    onDismiss()
                },
            )
            Segmented(
                options = listOf(true to L("Schriftlich"), false to L("Mündlich")),
                selected = written,
                onSelect = { written = it },
                modifier = Modifier.padding(horizontal = DS.spacingL, vertical = DS.spacingS),
            )
            FormSection(header = L("Note")) {
                if (usesPoints) {
                    StepperRow(
                        title = L("Punkte"), value = "$points",
                        canDecrease = points > 0, canIncrease = points < 15,
                        onDecrease = { points-- }, onIncrease = { points++ },
                    )
                } else {
                    // Big preview of the grade being entered, then the whole grade and its tendency.
                    Column(
                        Modifier.fillMaxWidth().padding(DS.spacingL).semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            model.gradeText(value),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(L("Wert"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ChoiceRow(
                        title = null,
                        options = (1..6).map { it to "$it" },
                        selected = whole,
                        onSelect = { whole = it },
                    )
                    ChoiceRow(
                        title = L("Tendenz"),
                        options = listOf(1 to "$whole+", 0 to "$whole", -1 to "$whole−"),
                        selected = tendency,
                        onSelect = { tendency = it },
                    )
                }
            }
            FormSection {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(L("Titel, z. B. 1. Klausur")) },
                    singleLine = true,
                    shape = DS.rowShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DS.spacingL, vertical = DS.spacingM),
                )
                NavRow(L("Datum"), sfSymbol("calendar"), trailing = formatDate(date, "dMMMy", model.calendar.zone)) { pickingDate = true }
                val format = remember(Localization.language) {
                    NumberFormat.getNumberInstance(Localization.locale).apply {
                        minimumFractionDigits = 1
                        maximumFractionDigits = 1
                    }
                }
                StepperRow(
                    title = L("Gewicht"), value = format.format(weight),
                    canDecrease = weight > 0.5, canIncrease = weight < 3.0,
                    onDecrease = { weight = (weight - 0.5).coerceAtLeast(0.5) },
                    onIncrease = { weight = (weight + 0.5).coerceAtMost(3.0) },
                )
            }
            if (grade != null) {
                FormSection {
                    ActionRow(L("Note löschen"), symbol = "trash", destructive = true) {
                        model.deleteGrade(grade.id)
                        onDismiss()
                    }
                }
            }
        }
    }

    if (pickingDate) {
        val zone = model.calendar.zone
        val state = rememberDatePickerState(
            initialSelectedDateMillis = model.calendar.localDate(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        MeldoDatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            // Keep the time of day, like the iOS date-only picker.
                            val time = date.atZone(zone).toLocalTime()
                            date = LocalDate.from(day).atTime(time).atZone(zone).toInstant()
                        }
                        pickingDate = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(L("Fertig")) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }, shapes = ButtonDefaults.shapes()) { Text(L("Abbrechen")) } },
        ) { DatePicker(state) }
    }
}

/** Notensystem: scale, written/oral weighting and the tendency step. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeSettingsSheet(onDismiss: () -> Unit) {
    val model = LocalAppModel.current
    val p = model.preferences
    // Local while dragging; saved once the finger lifts.
    var weight by remember(p.writtenWeight) { mutableFloatStateOf(p.writtenWeight.toFloat()) }

    MeldoSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = DS.spacingL)) {
            SheetBar(title = L("Notensystem"), onCancel = null, confirm = L("Fertig"), onConfirm = onDismiss)
            FormSection(header = L("Notensystem")) {
                ChoiceRow(
                    title = null,
                    options = listOf(GradeScaleChoice.traditional to L("Noten 1–6"), GradeScaleChoice.points to L("Punkte 0–15")),
                    selected = p.gradeScale,
                    onSelect = { model.setGradeScale(it) },
                )
            }
            FormSection(header = L("Gewichtung"), footer = L("Frag deine Lehrkraft, wie sie schriftlich und mündlich gewichtet.")) {
                val written = (weight * 100).roundToInt()
                Column(Modifier.fillMaxWidth().padding(vertical = DS.spacingXS)) {
                    ValueRow(L("Schriftlich"), "$written %")
                    Slider(
                        value = weight,
                        onValueChange = { weight = ((it * 20).roundToInt() / 20f) },
                        onValueChangeFinished = { model.setWrittenWeight(weight.toDouble()) },
                        steps = 19,
                        modifier = Modifier.padding(horizontal = DS.spacingL),
                    )
                    ValueRow(L("Mündlich"), "${100 - written} %")
                }
            }
            if (p.gradeScale == GradeScaleChoice.traditional) {
                FormSection(footer = L("So viel zählt ein Plus oder Minus, z. B. 2− = 2,33.")) {
                    ChoiceRow(
                        title = L("Plus und Minus"),
                        options = listOf(0.25 to "0,25", 0.33 to "0,33", 0.5 to "0,5"),
                        selected = p.tendencyStep,
                        onSelect = { model.setTendencyStep(it) },
                    )
                }
            }
        }
    }
}
