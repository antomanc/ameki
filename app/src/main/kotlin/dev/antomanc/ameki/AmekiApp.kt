package dev.antomanc.ameki

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import dev.antomanc.ameki.core.data.di.dataModule
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.RefreshScheduler
import dev.antomanc.ameki.core.ui.AppRequest
import dev.antomanc.ameki.feature.home.homeModule
import dev.antomanc.ameki.feature.locations.locationsModule
import dev.antomanc.ameki.feature.settings.settingsModule
import dev.antomanc.ameki.widget.WidgetUpdater
import dev.antomanc.ameki.widget.widgetModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
            // Selection only changes the app pager; widget rendering follows the other settings.
            launch {
                get<SettingsRepository>().settings.map { it.copy(selectedPlaceId = null) }.distinctUntilChanged().drop(1).collect { get<WidgetUpdater>().onWeatherUpdated() }
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

    /** Called from a foreground Activity on IO; background workers never publish shortcuts. */
    fun publishShortcuts() {
        val shortcuts = getSystemService(android.content.pm.ShortcutManager::class.java)
        if (shortcuts.isRateLimitingActive) return
        val entries = listOf(
            Triple(AppRequest.ACTION_PLACES, AppRequest.OpenPlaces, getString(R.string.shortcut_places)),
            Triple(AppRequest.ACTION_REFRESH, AppRequest.Refresh, getString(R.string.shortcut_refresh)),
            Triple(AppRequest.ACTION_WIDGET, AppRequest.PinWidget, getString(R.string.shortcut_widget)),
        ).filter { (_, request, _) -> request != AppRequest.PinWidget || android.appwidget.AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported }.map { (action, request, label) ->
            android.content.pm.ShortcutInfo.Builder(this, action)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(android.graphics.drawable.Icon.createWithResource(this, when (request) {
                    AppRequest.OpenPlaces -> android.R.drawable.ic_menu_mylocation
                    AppRequest.Refresh -> android.R.drawable.ic_popup_sync
                    else -> android.R.drawable.ic_input_add
                }))
                .setIntent(request.writeTo(android.content.Intent(this, MainActivity::class.java)
                    .setAction(android.content.Intent.ACTION_VIEW)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)))
                .build()
        }
        shortcuts.setDynamicShortcuts(entries)
    }

    companion object {
        val appModules = listOf(dataModule, homeModule, locationsModule, settingsModule, widgetModule)
    }
}
