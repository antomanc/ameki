package dev.antomanc.ameki.core.data

import dev.antomanc.ameki.core.model.SourceId
import dev.antomanc.ameki.source.api.WeatherSource

/** All sources the app knows. Add a module, add it here: the rest of the app adapts. */
class SourceRegistry(sources: List<WeatherSource>) {
    private val byId = sources.associateBy { it.id }

    operator fun get(id: SourceId): WeatherSource = byId[id] ?: error("Source $id not registered")

    val all: Collection<WeatherSource> get() = byId.values

    val ids: Set<SourceId> get() = byId.keys
}
