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

    /** Refresh now, e.g. after adding a widget or changing source. */
    private var lastForcedAt = Long.MIN_VALUE

    @Synchronized
    fun refreshNow(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (force && lastForcedAt != Long.MIN_VALUE && now - lastForcedAt < 5_000) return
        if (force) lastForcedAt = now
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .setInputData(androidx.work.workDataOf(RefreshWorker.KEY_FORCE to force))
            .addTag(TAG)
            .build()
        // A forced run joins the queue; an ordinary one must not cancel work already in flight.
        val policy = ExistingWorkPolicy.APPEND_OR_REPLACE
        wm.enqueueUniqueWork(ONE_SHOT, policy, request)
    }

    /** A network-free redraw at the next data/freshness boundary. Android may defer it in Doze. */
    fun scheduleExpiry(at: java.time.Instant?) {
        if (at == null) { wm.cancelUniqueWork(CACHE_EXPIRY); return }
        val delay = java.time.Duration.between(java.time.Instant.now(), at).toMillis().coerceAtLeast(1_000)
        val request = OneTimeWorkRequestBuilder<CacheExpiryWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        wm.enqueueUniqueWork(CACHE_EXPIRY, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val CACHE_EXPIRY = "ameki-cache-expiry"
        const val TAG = "ameki-refresh"
        const val PERIODIC = "ameki-refresh-periodic"
        const val ONE_SHOT = "ameki-refresh-now"
    }
}
