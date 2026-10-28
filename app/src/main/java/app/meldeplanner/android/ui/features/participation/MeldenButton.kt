@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.participation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import app.meldeplanner.android.ui.theme.MeldoSheet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.meldeplanner.android.ui.components.rememberReduceMotion
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.MeldeEngine
import app.meldeplanner.android.data.AppTheme
import app.meldeplanner.android.data.ParticipationEntry
import app.meldeplanner.android.data.Subject
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.timetableBlocks
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.components.SubjectIcon
import app.meldeplanner.android.ui.components.rememberHaptic
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.features.dashboard.rememberTickingNow
import app.meldeplanner.android.ui.theme.MeldoColors
import app.meldeplanner.android.ui.theme.StateColors
import app.meldeplanner.android.ui.theme.brandColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.meldeplanner.android.ui.components.SubjectPickerSheet

// Port of meldo-ios/MeldePlanner/Features/Participation/MeldenButton.swift.

/**
 * The floating Melden button on Start and Stundenplan. One tap records a
 * Meldung for the current lesson (the badge counts this lesson's Meldungen);
 * outside lessons it suggests the likely lesson instead of listing every
 * subject. After recording it shows the subject with Undo for a few seconds.
 */
/**
 * Melden. `inAccessory`: the iOS tab bar accessory look, a wide row in the floating bar
 * (orange icon and label, this lesson's count, undo on the right); otherwise the floating button.
 */
@Composable
fun MeldenButton(inAccessory: Boolean = false) {
    val model = LocalAppModel.current
    val haptic = rememberHaptic()
    val reduceMotion = rememberReduceMotion()
    var lastEntry by remember { mutableStateOf<ParticipationEntry?>(null) }
    var choosingSubject by remember { mutableStateOf(false) }
    val now = rememberTickingNow(model)

    LaunchedEffect(lastEntry?.id) {
        if (lastEntry == null) return@LaunchedEffect
        delay(4_000)
        lastEntry = null
    }

    // Meldungen in the running lesson today (reads model.data, so it updates on every change).
    val lessonCount = model.data.let { data ->
        model.currentLesson(now)?.let { MeldeEngine.count(data, it.lessonId, now, model.calendar) } ?: 0
    }
    val entry = lastEntry
    val label = if (entry == null) L("Melden") else model.subject(entry.subjectID)?.name ?: L("Gemeldet")

    // Brand color: Meldo is always orange (no theme choice, like iOS).
    val container = AppTheme.orange.brandColor()
    val content = MeldoColors.ink

    // Expressive press morph: the pill squeezes to a rounder square while pressed.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 20.dp else 40.dp,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "melden-corner",
    )
    // A short springy pop when a Meldung is recorded.
    val pop = remember { Animatable(1f) }
    val popSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(entry?.id) {
        if (entry == null || reduceMotion) return@LaunchedEffect
        pop.animateTo(1.08f, popSpec)
        pop.animateTo(1f, settleSpec)
    }
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val spatialSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    fun record() {
        when (val outcome = model.recordParticipation()) {
            is AppModel.RecordOutcome.Recorded -> {
                lastEntry = outcome.entry
                haptic(HapticFeedbackType.Confirm)
            }
            AppModel.RecordOutcome.NeedsSubject -> choosingSubject = true
        }
    }

    if (inAccessory) {
        val tint = AppTheme.orange.brandColor()
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = ::record)
                    .semantics {
                        contentDescription = L("Meldung erfassen")
                        if (lessonCount > 0) stateDescription = L("%lld× in dieser Stunde", lessonCount)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(sfSymbol(if (entry == null) "hand.raised.fill" else "checkmark"), null, Modifier.size(22.dp), tint = tint)
                Spacer(Modifier.size(DS.spacingS))
                Text(label, color = tint, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
                if (lessonCount > 0) {
                    Spacer(Modifier.size(DS.spacingS))
                    Text(
                        "$lessonCount×", color = tint, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.background(tint.copy(alpha = 0.15f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)
                            .clearAndSetSemantics {},
                    )
                }
            }
            if (entry != null) {
                Box(Modifier.size(width = 1.dp, height = 22.dp).background(MaterialTheme.colorScheme.outlineVariant))
                androidx.compose.material3.IconButton(onClick = {
                    lastEntry?.let { model.deleteParticipation(it.id) }
                    lastEntry = null
                }, modifier = Modifier.size(52.dp)) {
                    Icon(sfSymbol("arrow.uturn.backward"), L("Rückgängig"), tint = tint)
                }
            }
        }
    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
        AnimatedVisibility(
            visible = entry != null,
            enter = if (reduceMotion) fadeIn(effects) else slideInHorizontally(spatialOffset) { it } + fadeIn(effects),
            exit = if (reduceMotion) fadeOut(effects) else slideOutHorizontally(spatialOffset) { it } + fadeOut(effects),
        ) {
            FilledTonalButton(
                onClick = {
                    lastEntry?.let { model.deleteParticipation(it.id) }
                    lastEntry = null
                },
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = DS.spacingL, vertical = DS.spacingS),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Icon(sfSymbol("arrow.uturn.backward"), null, Modifier.size(20.dp))
                Spacer(Modifier.size(DS.spacingS))
                Text(L("Rückgängig"), style = MaterialTheme.typography.titleSmall)
            }
        }
        Box {
            MediumExtendedFloatingActionButton(
                onClick = ::record,
                shape = RoundedCornerShape(corner),
                containerColor = container,
                contentColor = content,
                interactionSource = interaction,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .semantics {
                        contentDescription = L("Meldung erfassen")
                        if (lessonCount > 0) stateDescription = L("%lld× in dieser Stunde", lessonCount)
                    },
                icon = {
                    AnimatedContent(
                        targetState = entry == null,
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn(fastEffects) togetherWith fadeOut(fastEffects)
                            } else {
                                (scaleIn(popSpec, initialScale = 0.4f) + fadeIn(fastEffects)) togetherWith
                                    (scaleOut(popSpec, targetScale = 0.4f) + fadeOut(fastEffects))
                            }
                        },
                        label = "melden-icon",
                    ) { idle ->
                        Icon(sfSymbol(if (idle) "hand.raised.fill" else "checkmark"), null, Modifier.size(28.dp))
                    }
                },
                text = {
                    // The label rolls to the subject name and the button resizes with the theme spring.
                    AnimatedContent(
                        targetState = label,
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn(effects) togetherWith fadeOut(effects) using SizeTransform(clip = false) { _, _ -> snap() }
                            } else {
                                (slideInVertically(spatialOffset) { it / 2 } + fadeIn(effects)) togetherWith
                                    (slideOutVertically(spatialOffset) { -it / 2 } + fadeOut(effects)) using
                                    SizeTransform(clip = false) { _, _ -> spatialSize }
                            }
                        },
                        label = "melden-label",
                    ) { text ->
                        Text(
                            text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
            if (lessonCount > 0 && entry == null) {
                Text(
                    lessonCount.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(4.dp, (-4).dp)
                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                        .background(StateColors.orange(), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clearAndSetSemantics {},
                )
            }
        }
    }

    if (choosingSubject) {
        SubjectPickerSheet(onDismiss = { choosingSubject = false }) { subjectID ->
            lastEntry = model.recordParticipation(subjectID)
            haptic(HapticFeedbackType.Confirm)
        }
    }
}
