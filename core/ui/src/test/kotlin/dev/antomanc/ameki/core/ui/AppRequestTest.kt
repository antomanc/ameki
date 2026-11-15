package dev.antomanc.ameki.core.ui

import android.content.Intent
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class AppRequestTest {
    @Test
    fun `every request survives the intent round trip`() {
        listOf(
            AppRequest.Show(3),
            AppRequest.Show(3, LocalDate.of(2026, 11, 17), Instant.parse("2026-11-17T08:00:00Z")),
            AppRequest.OpenPlaces,
            AppRequest.Refresh,
            AppRequest.PinWidget,
        ).forEach { request ->
            assertThat(AppRequest.from(request.writeTo(Intent()).extras)).isEqualTo(request)
        }
    }

    @Test
    fun `a plain launch carries no request`() {
        assertThat(AppRequest.from(null)).isNull()
        assertThat(AppRequest.from(Bundle())).isNull()
    }

    @Test
    fun `extras written by earlier versions still work`() {
        val shortcut = Bundle().apply { putString("dev.antomanc.ameki.ACTION", "refresh") }
        assertThat(AppRequest.from(shortcut)).isEqualTo(AppRequest.Refresh)
        val config = Bundle().apply { putBoolean("dev.antomanc.ameki.OPEN_PLACES", true) }
        assertThat(AppRequest.from(config)).isEqualTo(AppRequest.OpenPlaces)
    }

    @Test
    fun `clearing removes the request but keeps other extras`() {
        val intent = AppRequest.Show(3, LocalDate.of(2026, 11, 17)).writeTo(Intent()).putExtra("other", 1)
        AppRequest.clear(intent)
        assertThat(AppRequest.from(intent.extras)).isNull()
        assertThat(intent.getIntExtra("other", 0)).isEqualTo(1)
    }
}
