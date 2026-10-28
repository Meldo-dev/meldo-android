package app.meldeplanner.android.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import app.meldeplanner.android.ui.R
import app.meldeplanner.android.data.AppIconChoice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Launcher icons, the counterpart of iOS alternate app icons. Each choice is an
 * `<activity-alias>` of MainActivity in AndroidManifest.xml with its own adaptive
 * icon; exactly one alias is enabled. `DONT_KILL_APP` keeps Meldo running while
 * the launcher swaps the icon.
 */
object AppIcons {
    /** The `<activity-alias>` class name for [choice]. */
    fun alias(choice: AppIconChoice): String = when (choice) {
        AppIconChoice.standard -> "app.meldeplanner.android.LauncherStandard"
        AppIconChoice.orange -> "app.meldeplanner.android.LauncherOrange"
        AppIconChoice.night -> "app.meldeplanner.android.LauncherNight"
    }

    @DrawableRes
    fun background(choice: AppIconChoice): Int = when (choice) {
        AppIconChoice.standard -> R.drawable.ic_launcher_background
        AppIconChoice.orange -> R.drawable.ic_launcher_orange_background
        AppIconChoice.night -> R.drawable.ic_launcher_night_background
    }

    /** Every choice shares the same hand; only the background changes. */
    @Suppress("UNUSED_PARAMETER")
    @DrawableRes
    fun foreground(choice: AppIconChoice): Int = R.drawable.ic_launcher_foreground

    /** Enables [choice]'s alias and disables the others. Returns `false` if the system refused. */
    fun apply(context: Context, choice: AppIconChoice): Boolean = try {
        val pm = context.packageManager
        // Enable the new one first so there is never a moment without a launcher entry.
        pm.setComponentEnabledSetting(
            ComponentName(context, alias(choice)),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AppIconChoice.entries.filter { it != choice }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(context, alias(it)),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        true
    } catch (e: Exception) {
        false
    }

    /** The icon the launcher shows now (the standard alias is enabled by default in the manifest). */
    fun current(context: Context): AppIconChoice {
        val pm = context.packageManager
        return AppIconChoice.entries.firstOrNull {
            when (pm.getComponentEnabledSetting(ComponentName(context, alias(it)))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> it == AppIconChoice.standard
                else -> false
            }
        } ?: AppIconChoice.standard
    }
}
