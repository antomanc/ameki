package dev.antomanc.ameki.feature.locations

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val locationsModule = module {
    viewModelOf(::LocationsViewModel)
}
