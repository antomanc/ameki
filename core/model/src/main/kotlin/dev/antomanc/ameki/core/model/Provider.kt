package dev.antomanc.ameki.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderId(val requiresApiKey: Boolean) {
    GOOGLE(requiresApiKey = true),
    OPEN_METEO(requiresApiKey = false),
}

enum class Capability {
    CURRENT,
    HOURLY,
    DAILY,
    ALERTS,
    SUN_EVENTS,
    MOON_PHASE,
    UV,
    VISIBILITY,
}
