package dev.antomanc.ameki

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import dev.antomanc.ameki.core.data.di.dataModule
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.RefreshScheduler
import dev.antomanc.ameki.feature.home.homeModule
import dev.antomanc.ameki.feature.locations.locationsModule
import dev.antomanc.ameki.feature.settings.settingsModule
import dev.antomanc.ameki.widget.WidgetUpdater
import dev.antomanc.ameki.widget.widgetModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin

class AmekiApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AmekiApp)
            modules(appModules)
        }
        WorkManager.initialize(this, Configuration.Builder().setWorkerFactory(KoinWorkerFactory()).build())
        appScope.launch {
            // Units, theme, time format... change what widgets draw: redraw them on every change.
            launch {
                get<SettingsRepository>().settings.drop(1).collect { get<WidgetUpdater>().onWeatherUpdated() }
            }
            val settings = get<SettingsRepository>().current()
            get<RefreshScheduler>().schedule(settings.refreshIntervalMinutes)
            // Widget picker previews (Android 15+). Rate-limited by the system, so once per version.
            val prefs = getSharedPreferences("app", MODE_PRIVATE)
            if (prefs.getInt("previews_for", 0) != BuildConfig.VERSION_CODE) {
                get<WidgetUpdater>().publishPreviews()
                prefs.edit().putInt("previews_for", BuildConfig.VERSION_CODE).apply()
            }
        }
    }

    companion object {
        val appModules = listOf(dataModule, homeModule, locationsModule, settingsModule, widgetModule)
    }
}
