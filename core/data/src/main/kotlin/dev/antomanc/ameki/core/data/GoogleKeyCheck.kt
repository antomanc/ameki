package dev.antomanc.ameki.core.data

import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.source.api.SourceException
import dev.antomanc.ameki.core.model.AndroidAppIdentity
import dev.antomanc.ameki.source.google.GoogleWeatherSource
import kotlinx.coroutines.CancellationException

/** Checks a Google key with one call. Null [source] means this build has no Google source. */
class GoogleKeyCheck(private val source: GoogleWeatherSource?, val appIdentity: AndroidAppIdentity?) {
    val available: Boolean get() = source != null

    suspend fun verify(key: String): SyncError? {
        val s = source ?: return SyncError.UNSUPPORTED
        return try {
            s.verifyKey(key.trim())
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceException) {
            SyncError.from(e).let { if (it == SyncError.MISSING_KEY) SyncError.INVALID_KEY else it }
        }
    }
}
