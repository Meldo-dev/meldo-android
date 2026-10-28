package app.meldeplanner.android.surfaces

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.meldeplanner.android.MainActivity
import app.meldeplanner.android.core.L
import app.meldeplanner.android.model.AppModel

/** Deep links into the app (`meldeplanner://…`), like `DeepLink` on iOS. */
internal object SurfaceIntents {
    /** Opens the subject picker to log a Meldung outside a lesson. */
    const val MELDEN = "meldeplanner://melden"
    const val HOMEWORK = "meldeplanner://hausaufgaben"

    fun intent(context: Context, link: String?): Intent =
        Intent(context, MainActivity::class.java).apply {
            if (link != null) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(link)
            } else {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    fun openApp(context: Context, link: String?): PendingIntent = PendingIntent.getActivity(
        context, link.hashCode(), intent(context, link),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * One tap from a widget, the Quick Settings tile or the live lesson
 * notification: records a Meldung for the current lesson. Outside lessons it is
 * logged without a subject, and the app offers to assign it later. Port of
 * meldo-ios/Shared/LogParticipationIntent.swift. Call on the main thread.
 */
internal object LogParticipation {
    /** Taps closer together than this count once. */
    private val DOUBLE_TAP: java.time.Duration = java.time.Duration.ofSeconds(2)

    /** What the Siri dialog says on iOS; used where a surface can show a short confirmation. */
    fun perform(context: Context): String {
        val model = context.meldoModel
        model.reloadFromDisk()
        // A double tap on a widget, the tile or a notification button is one Meldung, not two.
        // Any Meldung in the last two seconds; one "from the future" (clock changed) is no double tap.
        val recent = model.data.participation.any {
            val since = java.time.Duration.between(it.recordedAt, model.currentDate)
            !since.isNegative && since < DOUBLE_TAP
        }
        if (recent) {
            return L("Schon gemeldet.")
        }
        return when (val outcome = model.recordParticipation()) {
            is AppModel.RecordOutcome.Recorded -> {
                val name = model.subject(outcome.entry.subjectID)?.name ?: L("Unterricht")
                L("Gemeldet in %@.", name)
            }
            AppModel.RecordOutcome.NeedsSubject -> {
                model.recordParticipation(subjectID = null)
                L("Gemeldet, ohne Fach. Ordne es in der App zu.")
            }
        }
    }
}
