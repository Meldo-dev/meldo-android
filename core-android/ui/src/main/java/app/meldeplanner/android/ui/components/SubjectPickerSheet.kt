package app.meldeplanner.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.timetableBlocks
import app.meldeplanner.android.ui.theme.MeldoSheet
import kotlinx.coroutines.launch

private data class Suggestion(val subject: Subject, val reason: String)

/** A row in a grouped list: large outer corners on the first and last row, small ones between (Expressive lists). */
private fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val outer = DS.rowRadius
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/**
 * Outside a lesson: the lesson that just ended, the next one and the subjects
 * used most, as big buttons. Every subject stays one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectPickerSheet(onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val model = LocalAppModel.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    var showingAll by remember { mutableStateOf(false) }

    fun pick(id: String?) {
        onPick(id)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val suggestions = remember(model.data) { suggestions(model) }
    val spatialSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    MeldoSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DS.spacingL)
                .padding(bottom = DS.spacingL)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(DS.spacingM),
        ) {
            // Title centered in the sheet, independent of the cancel button's width.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Text(L("Abbrechen"))
                }
                Text(
                    L("Melden"),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (model.subjects.isEmpty()) {
                Text(
                    L("Lege zuerst Fächer an, damit Meldungen zugeordnet werden können."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    L("Gerade läuft keine Stunde. Wofür war die Meldung?"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                    suggestions.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                            row.forEach { s ->
                                Surface(
                                    onClick = { pick(s.subject.id) },
                                    shape = DS.rowShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(
                                        Modifier.padding(DS.spacingL),
                                        verticalArrangement = Arrangement.spacedBy(DS.spacingS),
                                    ) {
                                        SubjectIcon(s.subject, 36.dp)
                                        Text(
                                            s.subject.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(s.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                // DisclosureGroup "Anderes Fach": a grouped list whose first row is the toggle.
                val chevron by animateFloatAsState(
                    if (showingAll) 90f else 0f,
                    animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "chevron",
                )
                val count = model.subjects.size
                // The toggle row's bottom corners morph from outer to inner as the group opens.
                val toggleBottom by animateDpAsState(
                    if (showingAll && count > 0) 4.dp else DS.rowRadius,
                    animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "toggle-corner",
                )
                Column(Modifier.padding(top = DS.spacingS)) {
                    Surface(
                        onClick = { showingAll = !showingAll },
                        shape = RoundedCornerShape(
                            topStart = DS.rowRadius, topEnd = DS.rowRadius,
                            bottomStart = toggleBottom, bottomEnd = toggleBottom,
                        ),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = DS.spacingL, vertical = DS.spacingM),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(L("Anderes Fach"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Icon(sfSymbol("chevron.right"), null, Modifier.rotate(chevron), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    AnimatedVisibility(
                        showingAll,
                        enter = if (reduceMotion) fadeIn(effects) else expandVertically(spatialSize) + fadeIn(effects),
                        exit = if (reduceMotion) fadeOut(effects) else shrinkVertically(spatialSize) + fadeOut(effects),
                    ) {
                        Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            model.subjects.forEachIndexed { index, subject ->
                                key(subject.id) {
                                    // Grouped rows: small inner corners, the last row rounder.
                                    Surface(
                                        onClick = { pick(subject.id) },
                                        shape = groupedShape(index + 1, count + 1),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = DS.spacingL, vertical = DS.spacingM),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(DS.spacingM),
                                        ) {
                                            SubjectIcon(subject, 32.dp)
                                            Text(subject.name, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = { pick(null) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = DS.spacingS),
            ) {
                Text(L("Ohne Fach erfassen"))
            }
        }
    }
}

private fun suggestions(model: AppModel): List<Suggestion> {
    val now = model.currentDate
    val minute = model.calendar.minuteOfDay(now)
    val blocks = model.timetableBlocks(now).filter { it.kind != app.meldeplanner.android.model.TimetableBlock.Kind.cancelled }
    val list = mutableListOf<Suggestion>()
    fun add(id: String?, reason: String) {
        val subject = model.subject(id) ?: return
        if (list.any { it.subject.id == subject.id }) return
        list.add(Suggestion(subject, reason))
    }
    blocks.lastOrNull { it.endMinute <= minute && minute - it.endMinute <= 90 }?.let { add(it.subjectID, L("Gerade vorbei")) }
    blocks.firstOrNull { it.startMinute > minute }?.let { add(it.subjectID, L("Als Nächstes")) }
    val counts = model.data.participation.mapNotNull { it.subjectID }.groupingBy { it }.eachCount()
    counts.entries.sortedByDescending { it.value }.take(4).forEach { add(it.key, L("Oft gemeldet")) }
    return list.take(4)
}
