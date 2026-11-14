package dev.antomanc.ameki.core.data.di

import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dev.antomanc.ameki.core.data.AppIdentity
import dev.antomanc.ameki.core.data.GoogleKeyCheck
import dev.antomanc.ameki.core.data.BuildConfig
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.SourceRegistry
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.db.AmekiDatabase
import dev.antomanc.ameki.core.data.location.DeviceLocation
import dev.antomanc.ameki.core.data.settings.KeystoreSecretCipher
import dev.antomanc.ameki.core.data.settings.SecretCipher
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.RefreshScheduler
import dev.antomanc.ameki.core.data.sync.RefreshWorker
import dev.antomanc.ameki.core.data.sync.WeatherUpdateListener
import dev.antomanc.ameki.source.api.PlaceSearch
import dev.antomanc.ameki.source.google.GoogleWeatherSource
import dev.antomanc.ameki.source.openmeteo.OpenMeteoPlaceSearch
import dev.antomanc.ameki.source.openmeteo.OpenMeteoSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

private val android.content.Context.dataStore by preferencesDataStore(name = "settings")

val dataModule = module {
    single {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
            install(UserAgent) { agent = "Ameki (open source weather app)" }
        }
    }
    single {
        Room.databaseBuilder(androidContext(), AmekiDatabase::class.java, "ameki.db").build()
    }
    single { get<AmekiDatabase>().places() }
    single { get<AmekiDatabase>().snapshots() }
    single { get<AmekiDatabase>().widgets() }
    single<SecretCipher> { KeystoreSecretCipher() }
    single { SettingsRepository(androidContext().dataStore, get(), get<SourceRegistry>().ids) }
    single { DeviceLocation(androidContext()) }
    single<PlaceSearch> { OpenMeteoPlaceSearch(get()) }
    single {
        val koin = getKoin()
        PlaceRepository(get(), get(), get(), onRestored = { koin.get<RefreshScheduler>().refreshNow() })
    }
    single { OpenMeteoSource(get()) }
    single {
        // Resolve settings lazily: SettingsRepository depends on the source registry, which
        // contains this source, so an eager get() here loops forever.
        val koin = getKoin()
        GoogleWeatherSource(
            http = get(),
            apiKey = { koin.get<SettingsRepository>().googleApiKey() },
            appIdentity = AppIdentity.of(androidContext()),
        )
    }
    single {
        SourceRegistry(
            listOfNotNull(
                get<OpenMeteoSource>(),
                if (BuildConfig.GOOGLE_SOURCE_ENABLED) get<GoogleWeatherSource>() else null,
            ),
        )
    }
    single {
        val koin = getKoin()
        WeatherRepository(
            placeDao = get(), snapshotDao = get(), settings = get(), sources = get(), places = get(),
            listeners = { koin.getAll<WeatherUpdateListener>() },
            onNextExpiry = { koin.get<RefreshScheduler>().scheduleExpiry(it) },
        )
    }
    single { RefreshScheduler(androidContext()) }
    single {
        GoogleKeyCheck(
            source = if (BuildConfig.GOOGLE_SOURCE_ENABLED) get<GoogleWeatherSource>() else null,
            appIdentity = AppIdentity.of(androidContext()),
        )
    }
    worker { RefreshWorker(get(), get()) }
    worker { dev.antomanc.ameki.core.data.sync.CacheExpiryWorker(get(), get()) }
}
