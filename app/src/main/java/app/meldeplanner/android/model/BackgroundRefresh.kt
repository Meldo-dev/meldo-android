package app.meldeplanner.android.model

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Keeps WebUntis (and Teams) data current in the background, the counterpart of
 * iOS' Background App Refresh task in MeldePlannerApp.swift. Android decides the
 * exact time; WorkManager runs it about every 20 minutes when there is a network.
 */
object BackgroundRefresh {
    const val WORK_NAME = "app.meldeplanner.android.refresh"

    /** Plans the periodic background update; an existing schedule takes the new period. */
    fun schedule(context: Context) {
        // WebUntis in the background every 20 minutes; Google Drive sync adds the 5-minute runs below.
        val request = PeriodicWorkRequestBuilder<Worker>(20, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    const val SOON_WORK_NAME = "app.meldeplanner.android.sync-soon"

    /**
     * One more Google Drive sync about 5 minutes from now: after leaving Meldo and after
     * each background run, so changes keep flowing between the 20-minute periodic runs.
     * Android may delay it (battery saving); the periodic run still comes.
     */
    fun scheduleSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<Worker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(SOON_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Runs when Android wakes Meldo: sync with Google Drive, then update WebUntis and Teams if they're due. */
    suspend fun run(model: AppModel) = withContext(Dispatchers.Main) {
        model.reloadFromDisk()
        // Sync first, so a disconnect made on another device is seen before refreshing.
        model.refreshConnections(background = true)
    }

    /**
     * While the app is open: sync with Google Drive and update WebUntis every 30 seconds,
     * so other devices' changes and timetable changes arrive quickly.
     * Call from a lifecycle-bound coroutine (e.g. `repeatOnLifecycle(STARTED)`), so it
     * stops in the background.
     */
    suspend fun foregroundLoop(model: AppModel) {
        while (coroutineContext.isActive) {
            delay(30_000)
            withContext(Dispatchers.Main) {
                model.refreshConnections(onlyFrequent = true)
            }
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // The app's model once it exists; a pure background start creates it here.
            val model = applicationContext.meldoModel
            BackgroundRefresh.run(model)
            // Keep the 5-minute rhythm only while Google Drive sync is on.
            if (model.wantsRefreshSoon) scheduleSoon(applicationContext)
            return Result.success()
        }
    }
}
