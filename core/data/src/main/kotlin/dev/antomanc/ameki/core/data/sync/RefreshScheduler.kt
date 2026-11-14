package dev.antomanc.ameki.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** One shared periodic job refreshes everything; widgets never fetch on their own. */
class RefreshScheduler(private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Idempotent: call on start and whenever the interval changes. */
    fun schedule(intervalMinutes: Int, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Refresh now, e.g. after adding a widget or changing provider. */
    fun refreshNow(force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .setInputData(androidx.work.workDataOf(RefreshWorker.KEY_FORCE to force))
            .addTag(TAG)
            .build()
        wm.enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val TAG = "ameki-refresh"
        const val PERIODIC = "ameki-refresh-periodic"
        const val ONE_SHOT = "ameki-refresh-now"
    }
}
