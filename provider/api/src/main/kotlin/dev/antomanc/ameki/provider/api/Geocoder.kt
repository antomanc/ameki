package dev.antomanc.ameki.provider.api

import dev.antomanc.ameki.core.model.PlaceSearchResult

/** City search. Kept separate from [WeatherProvider]: Google's Weather API has no geocoding. */
interface PlaceSearch {
    suspend fun search(query: String, language: String, limit: Int = 10): List<PlaceSearchResult>
}
