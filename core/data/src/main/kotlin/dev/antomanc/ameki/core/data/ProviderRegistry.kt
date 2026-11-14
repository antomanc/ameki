package dev.antomanc.ameki.core.data

import dev.antomanc.ameki.core.model.ProviderId
import dev.antomanc.ameki.provider.api.WeatherProvider

/** All providers the app knows. Add a module, add it here: the rest of the app adapts. */
class ProviderRegistry(providers: List<WeatherProvider>) {
    private val byId = providers.associateBy { it.id }

    operator fun get(id: ProviderId): WeatherProvider = byId[id] ?: error("Provider $id not registered")

    val all: Collection<WeatherProvider> get() = byId.values
}
