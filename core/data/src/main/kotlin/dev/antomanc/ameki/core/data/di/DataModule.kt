package dev.antomanc.ameki.core.data.di

import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dev.antomanc.ameki.core.data.AppIdentity
import dev.antomanc.ameki.core.data.PlaceRepository
import dev.antomanc.ameki.core.data.ProviderRegistry
import dev.antomanc.ameki.core.data.WeatherRepository
import dev.antomanc.ameki.core.data.db.AmekiDatabase
import dev.antomanc.ameki.core.data.location.DeviceLocation
import dev.antomanc.ameki.core.data.settings.KeystoreSecretCipher
import dev.antomanc.ameki.core.data.settings.SecretCipher
import dev.antomanc.ameki.core.data.settings.SettingsRepository
import dev.antomanc.ameki.core.data.sync.RefreshScheduler
import dev.antomanc.ameki.core.data.sync.RefreshWorker
import dev.antomanc.ameki.core.data.sync.WeatherUpdateListener
import dev.antomanc.ameki.provider.api.PlaceSearch
import dev.antomanc.ameki.provider.google.GoogleWeatherProvider
import dev.antomanc.ameki.provider.openmeteo.OpenMeteoPlaceSearch
import dev.antomanc.ameki.provider.openmeteo.OpenMeteoProvider
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
    single { SettingsRepository(androidContext().dataStore, get()) }
    single { DeviceLocation(androidContext()) }
    single<PlaceSearch> { OpenMeteoPlaceSearch(get()) }
    single { PlaceRepository(get(), get(), get()) }
    single { OpenMeteoProvider(get()) }
    single {
        val settings = get<SettingsRepository>()
        GoogleWeatherProvider(
            http = get(),
            apiKey = { settings.googleApiKey() },
            appIdentity = AppIdentity.of(androidContext()),
        )
    }
    single { ProviderRegistry(listOf(get<OpenMeteoProvider>(), get<GoogleWeatherProvider>())) }
    single {
        val koin = getKoin()
        WeatherRepository(
            placeDao = get(), snapshotDao = get(), settings = get(), providers = get(), places = get(),
            listeners = { koin.getAll<WeatherUpdateListener>() },
        )
    }
    single { RefreshScheduler(androidContext()) }
    worker { RefreshWorker(get(), get()) }
}
