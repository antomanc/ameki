package dev.antomanc.ameki.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.db.WidgetDao
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val weather: WeatherRepository by inject()
    private val widgets: WidgetDao by inject()

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, false)
        val widgetPlaces = widgets.all().mapTo(mutableSetOf()) { it.placeId }
        val results = if (force) {
            widgetPlaces.filterNotNull().associateWith { weather.refresh(it, force = true, notify = false) } +
                weather.refreshActive(widgetPlaces)
        } else {
            weather.refreshActive(widgetPlaces)
        }
        val errors = results.values.filterNotNull()
        return when {
            errors.isEmpty() -> Result.success()
            // Transient failures: let WorkManager back off exponentially and try again.
            errors.all { it == SyncError.NETWORK || it == SyncError.UNKNOWN || it == SyncError.QUOTA } &&
                runAttemptCount < MAX_RETRIES -> Result.retry()
            // Wrong key, no location...: retrying burns battery and quota, wait for the user.
            else -> Result.success()
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val MAX_RETRIES = 4
    }
}

/** Redraws retained weather without requiring a network connection. */
class CacheExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val weather: WeatherRepository by inject()
    override suspend fun doWork(): Result {
        weather.refreshCachedState()
        return Result.success()
    }
}
