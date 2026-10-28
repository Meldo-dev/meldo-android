package app.meldeplanner.android

import android.app.Application
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.data.LocalStore
import app.meldeplanner.android.data.SecureStore
import app.meldeplanner.android.model.AppModel
import app.meldeplanner.android.model.BackgroundRefresh
import app.meldeplanner.android.model.ModelOwner
import app.meldeplanner.android.surfaces.SurfacesListener
import app.meldeplanner.android.ui.components.AppIcons

/**
 * The process-wide app model. Widgets, the Quick Settings tile and notification
 * actions run in this process too, so they share it (and its store lock).
 */
class MeldoApp : Application(), ModelOwner {
    override val model: AppModel by lazy {
        // The demo build shows sample data and stays off widgets, notifications and the tile.
        if (BuildConfig.DEMO) {
            // Notification previews (Status screen) open this activity; nothing else is hooked up.
            SurfacesListener.useActivity(MainActivity::class.java)
            return@lazy DemoData.model(this).also { m -> meldoFeatures.all.forEach { it.install(m) } }
        }
        AppModel(this, LocalStore.standard(this)).also {
            SurfacesListener.install(it, MainActivity::class.java)
            meldoFeatures.all.forEach { feature -> feature.install(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Localization.load(this)
        SecureStore.init(this)
        Localization.applyToSystem(this, model.preferences.language)
        if (BuildConfig.DEMO) return
        BackgroundRefresh.schedule(this)
        // Restored data can name another icon than the launcher shows.
        val icon = model.preferences.appIcon
        if (AppIcons.current(this) != icon) AppIcons.apply(this, icon)
    }
}
