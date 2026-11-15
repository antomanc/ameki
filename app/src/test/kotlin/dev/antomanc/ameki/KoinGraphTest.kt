package dev.antomanc.ameki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.sync.WeatherUpdateListener
import dev.antomanc.ameki.feature.home.HomeViewModel
import dev.antomanc.ameki.feature.locations.LocationsViewModel
import dev.antomanc.ameki.feature.settings.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Koin resolves at runtime (ADR 0003), so this test resolves every entry point the app uses.
 * A missing binding fails here instead of on a user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AmekiApp::class)
class KoinGraphTest {
    @After fun tearDown() = stopKoin()

    @Test
    fun everyEntryPointResolves() {
        ApplicationProvider.getApplicationContext<Context>()
        val koin = GlobalContext.get()
        koin.get<WeatherRepository>()
        koin.get<dev.antomanc.ameki.core.data.widget.WidgetInstances>()
        koin.get<HomeViewModel>()
        koin.get<LocationsViewModel>()
        koin.get<SettingsViewModel>()
        assertEquals(1, koin.getAll<WeatherUpdateListener>().size)
    }
}
