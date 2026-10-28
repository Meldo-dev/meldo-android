package app.meldeplanner.android.model

import android.content.Context

/**
 * The application, which holds the process-wide model. Widgets, the Quick Settings tile,
 * notification actions and background work run in this process too, so they share it
 * (and its store lock).
 */
interface ModelOwner {
    val model: AppModel
}

/** The process-wide model. */
val Context.meldoModel: AppModel get() = (applicationContext as ModelOwner).model
