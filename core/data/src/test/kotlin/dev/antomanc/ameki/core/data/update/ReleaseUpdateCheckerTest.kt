package dev.antomanc.ameki.core.data.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.antomanc.ameki.core.data.settings.SecretCipher
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

private object PlainCipher : SecretCipher {
    override fun encrypt(plain: String) = plain
    override fun decrypt(encoded: String) = encoded
}

class ReleaseUpdateCheckerTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = Instant.parse("2026-11-17T12:00:00Z")
    private var installed = "0.2.1"
    private var hits = 0

    private fun settings() = SettingsRepository(
        PreferenceDataStoreFactory.create { tmp.newFile("s.preferences_pb") },
        PlainCipher,
    )

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, tag: String = "v0.2.2") = HttpClient(MockEngine {
        hits++
        respond(
            """{"tag_name":"$tag"}""",
            status,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
    })

    private fun checker(
        settings: SettingsRepository = settings(),
        enabled: Boolean = true,
        http: HttpClient = client(),
    ) = ReleaseUpdateChecker(
        http = http,
        settings = settings,
        installedVersionName = { installed },
        enabled = enabled,
        clock = { now },
    )

    @Test
    fun `offers a newer remote versionName`() = runTest {
        val result = checker().check()
        assertThat(result).isEqualTo(UpdateCheckResult.Available("0.2.2"))
        assertThat(hits).isEqualTo(1)
    }

    @Test
    fun `up to date when remote matches installed`() = runTest {
        assertThat(checker(http = client(tag = "v0.2.1")).check()).isEqualTo(UpdateCheckResult.UpToDate)
    }

    @Test
    fun `skips when disabled`() = runTest {
        assertThat(checker(enabled = false).check()).isEqualTo(UpdateCheckResult.Disabled)
        assertThat(hits).isEqualTo(0)
    }

    @Test
    fun `throttles repeat GitHub hits within 24 hours`() = runTest {
        val settings = settings()
        val c = checker(settings)
        assertThat(c.check()).isEqualTo(UpdateCheckResult.Available("0.2.2"))
        now = now.plusSeconds(3600)
        assertThat(c.check()).isEqualTo(UpdateCheckResult.Available("0.2.2"))
        assertThat(hits).isEqualTo(1)
    }

    @Test
    fun `force bypasses throttle`() = runTest {
        val settings = settings()
        val c = checker(settings)
        c.check()
        now = now.plusSeconds(60)
        assertThat(c.check(force = true)).isEqualTo(UpdateCheckResult.Available("0.2.2"))
        assertThat(hits).isEqualTo(2)
    }

    @Test
    fun `dismiss suppresses that version until a newer one appears`() = runTest {
        val settings = settings()
        val c = checker(settings)
        assertThat(c.check()).isEqualTo(UpdateCheckResult.Available("0.2.2"))
        // View / Later / dialog dismiss all call dismiss — once per version on cold start.
        c.dismiss("0.2.2")
        now = now.plusSeconds(60)
        assertThat(c.check()).isEqualTo(UpdateCheckResult.Dismissed)
        assertThat(hits).isEqualTo(1)
        now = now.plus(ReleaseUpdateChecker.THROTTLE).plusSeconds(1)
        assertThat(c.check()).isEqualTo(UpdateCheckResult.Dismissed)
        assertThat(hits).isEqualTo(2)
        now = now.plus(ReleaseUpdateChecker.THROTTLE).plusSeconds(1)
        assertThat(checker(settings, http = client(tag = "v0.2.3")).check())
            .isEqualTo(UpdateCheckResult.Available("0.2.3"))
    }

    @Test
    fun `About force re-opens a dismissed version`() = runTest {
        val settings = settings()
        val c = checker(settings)
        c.check()
        c.dismiss("0.2.2")
        assertThat(c.check(force = true)).isEqualTo(UpdateCheckResult.Available("0.2.2"))
    }

    @Test
    fun `API failure returns Failed without updating last check`() = runTest {
        val settings = settings()
        val c = checker(settings, http = client(status = HttpStatusCode.InternalServerError))
        assertThat(c.check()).isEqualTo(UpdateCheckResult.Failed)
        assertThat(settings.lastUpdateCheckAt()).isNull()
        now = now.plusSeconds(60)
        assertThat(checker(settings, http = client(tag = "v0.2.2")).check())
            .isEqualTo(UpdateCheckResult.Available("0.2.2"))
    }
}
