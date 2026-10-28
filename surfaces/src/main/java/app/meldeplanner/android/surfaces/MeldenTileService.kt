package app.meldeplanner.android.surfaces

import android.net.Uri
import android.content.Intent
import android.os.Build
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.meldeplanner.android.surfaces.R
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.MeldeEngine
import java.time.Instant
import app.meldeplanner.android.model.meldoModel

/**
 * Quick Settings tile "Melden", the counterpart of the iOS Control Center /
 * Lock Screen control (meldo-ios/MeldeWidgets/MeldenControl.swift): one tap
 * records a Meldung for the current lesson, even with the phone locked.
 * Outside lessons it opens the subject picker (`meldeplanner://melden`).
 */
class MeldenTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val model = applicationContext.meldoModel
        model.reloadFromDisk()
        render()
    }

    override fun onClick() {
        super.onClick()
        val model = applicationContext.meldoModel
        model.reloadFromDisk()
        if (MeldeEngine.participationLesson(model.data, Instant.now(), model.calendar) != null) {
            LogParticipation.perform(applicationContext)
            render()
        } else {
            val open = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(SurfaceIntents.openApp(applicationContext, SurfaceIntents.MELDEN))
                } else {
                    // Before Android 14 the tile opens the app with an Intent.
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(
                        Intent(Intent.ACTION_VIEW, Uri.parse(SurfaceIntents.MELDEN), applicationContext, SurfaceIntents.mainActivity)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            if (isLocked) unlockAndRun(open) else open()
        }
    }

    /** Label, the running lesson with its count as subtitle, active while a lesson runs. */
    private fun render() {
        val tile = qsTile ?: return
        val model = applicationContext.meldoModel
        val data = model.data
        val now = Instant.now()
        val lesson = MeldeEngine.participationLesson(data, now, model.calendar)
        tile.label = L("Melden")
        tile.icon = Icon.createWithResource(this, R.drawable.ic_surfaces_hand)
        tile.contentDescription = L("Meldung erfassen")
        if (lesson != null) {
            val name = MeldeEngine.subject(data, lesson.subjectId)?.name ?: L("Unterricht")
            val count = MeldeEngine.count(data, lesson.lessonId, now, model.calendar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "$name · ${L("%lld×", count)}"
            tile.state = Tile.STATE_ACTIVE
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = null
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
