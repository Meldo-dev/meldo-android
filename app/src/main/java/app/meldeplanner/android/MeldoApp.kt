package app.meldeplanner.android

import android.app.Application
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.data.LocalStore
import app.meldeplanner.android.data.SecureStore
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.BackgroundRefresh
import app.meldeplanner.android.ui.features.more.AppIcons

/**
 * The process-wide app model. Widgets, the Quick Settings tile and notification
 * actions run in this process too, so they share it (and its store lock).
 */
class MeldoApp : Application() {
    val model: AppModel by lazy { AppModel(this, LocalStore.standard(this)) }

    override fun onCreate() {
        super.onCreate()
        Localization.load(this)
        SecureStore.init(this)
        Localization.applyToSystem(this, model.preferences.language)
        BackgroundRefresh.schedule(this)
        // Restored data can name another icon than the launcher shows.
        val icon = model.preferences.appIcon
        if (AppIcons.current(this) != icon) AppIcons.apply(this, icon)
    }
}
