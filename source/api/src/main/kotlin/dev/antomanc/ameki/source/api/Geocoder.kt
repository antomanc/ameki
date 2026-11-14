package dev.antomanc.ameki.source.api

import dev.antomanc.ameki.core.model.PlaceSearchResult

/** City search. Kept separate from [WeatherSource]: Google's Weather API has no geocoding. */
interface PlaceSearch {
    suspend fun search(query: String, language: String, limit: Int = 10): List<PlaceSearchResult>
}
