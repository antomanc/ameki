package dev.antomanc.ameki.core.data.update

import dev.antomanc.ameki.core.data.settings.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/** Outcome of a GitHub Releases update check. */
sealed interface UpdateCheckResult {
    /** Checks are off (debug applicationId or build flag). */
    data object Disabled : UpdateCheckResult
    /** Inside the throttle window and nothing useful was cached. */
    data object Throttled : UpdateCheckResult
    /** Remote is not newer than the installed versionName. */
    data object UpToDate : UpdateCheckResult
    /** Remote matches a version the user already dismissed (cold-start only). */
    data object Dismissed : UpdateCheckResult
    /** A newer release is available. */
    data class Available(val versionName: String) : UpdateCheckResult
    /** Network or API failure. */
    data object Failed : UpdateCheckResult
}

/**
 * Notify-only check against GitHub Releases. No APK download.
 *
 * Cold start uses [check] with `force = false` (24h throttle, dismiss respected, failures silent).
 * Settings → About uses `force = true` (bypass throttle and dismiss; surface [UpdateCheckResult.Failed]).
 */
class ReleaseUpdateChecker(
    private val http: HttpClient,
    private val settings: SettingsRepository,
    private val installedVersionName: () -> String,
    private val enabled: Boolean,
    private val clock: () -> Instant = Instant::now,
    private val apiUrl: String = DEFAULT_API_URL,
) {
    suspend fun check(force: Boolean = false): UpdateCheckResult {
        if (!enabled) return UpdateCheckResult.Disabled
        val now = clock()
        val remote = if (force || shouldFetch(now)) {
            when (val fetched = fetchRemoteVersion()) {
                null -> return UpdateCheckResult.Failed
                else -> {
                    settings.setUpdateLastCheckAt(now)
                    settings.setUpdateLastRemoteVersion(fetched)
                    fetched
                }
            }
        } else {
            settings.updateLastRemoteVersion() ?: return UpdateCheckResult.Throttled
        }
        if (!VersionNames.isNewer(remote, installedVersionName())) return UpdateCheckResult.UpToDate
        if (!force && remote == settings.updateDismissedVersion()) return UpdateCheckResult.Dismissed
        return UpdateCheckResult.Available(remote)
    }

    suspend fun dismiss(versionName: String) {
        settings.setUpdateDismissedVersion(VersionNames.normalize(versionName))
    }

    private suspend fun shouldFetch(now: Instant): Boolean {
        val last = settings.updateLastCheckAt() ?: return true
        return Duration.between(last, now) >= THROTTLE
    }

    private suspend fun fetchRemoteVersion(): String? = runCatching {
        val response = http.get(apiUrl) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) return null
        VersionNames.normalize(json.decodeFromString(LatestRelease.serializer(), text).tagName)
            .takeIf { it.isNotEmpty() }
    }.getOrNull()

    @Serializable
    private data class LatestRelease(@SerialName("tag_name") val tagName: String)

    companion object {
        val THROTTLE: Duration = Duration.ofHours(24)
        const val DEFAULT_API_URL = "https://api.github.com/repos/antomanc/ameki/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/antomanc/ameki/releases/latest"
        const val DEBUG_APPLICATION_ID = "dev.antomanc.ameki.debug"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
