package dev.antomanc.ameki.core.ui

import android.content.Intent
import android.os.Bundle
import java.time.Instant
import java.time.LocalDate

/**
 * What a widget, a launcher shortcut or the places list asks the app to show. It travels as
 * Intent extras, and this is the only code that knows their names.
 */
sealed interface AppRequest {
    /** Show [placeId]; with [day], open that day, scrolled to [hour] when given. */
    data class Show(val placeId: Long, val day: LocalDate? = null, val hour: Instant? = null) : AppRequest
    data object OpenPlaces : AppRequest
    /** Refresh the place on screen. */
    data object Refresh : AppRequest
    data object PinWidget : AppRequest

    /** Adds this request to [intent]'s extras. */
    fun writeTo(intent: Intent): Intent = intent.putExtras(toBundle())

    fun toBundle(): Bundle = Bundle().apply {
        when (val r = this@AppRequest) {
            is Show -> {
                putLong(PLACE_ID, r.placeId)
                r.day?.let { putLong(DAY, it.toEpochDay()) }
                r.hour?.let { putLong(HOUR, it.epochSecond) }
            }
            OpenPlaces -> putString(ACTION, ACTION_PLACES)
            Refresh -> putString(ACTION, ACTION_REFRESH)
            PinWidget -> putString(ACTION, ACTION_WIDGET)
        }
    }

    companion object {
        // Names kept from earlier versions: launcher shortcuts and widgets already hold them.
        private const val PLACE_ID = "dev.antomanc.ameki.PLACE_ID"
        private const val DAY = "dev.antomanc.ameki.DAY_EPOCH_DAY"
        private const val HOUR = "dev.antomanc.ameki.HOUR_EPOCH_SECOND"
        private const val ACTION = "dev.antomanc.ameki.ACTION"
        private const val LEGACY_OPEN_PLACES = "dev.antomanc.ameki.OPEN_PLACES"
        private val ALL = listOf(PLACE_ID, DAY, HOUR, ACTION, LEGACY_OPEN_PLACES, "dev.antomanc.ameki.REQUEST_ID")

        /** Launcher shortcut actions, as published by the app. */
        const val ACTION_PLACES = "places"
        const val ACTION_REFRESH = "refresh"
        const val ACTION_WIDGET = "widget"

        /** The request in [extras], or null when they hold none (a plain launch). */
        fun from(extras: Bundle?): AppRequest? {
            extras ?: return null
            if (extras.getBoolean(LEGACY_OPEN_PLACES)) return OpenPlaces
            when (extras.getString(ACTION)) {
                ACTION_PLACES -> return OpenPlaces
                ACTION_REFRESH -> return Refresh
                ACTION_WIDGET -> return PinWidget
            }
            val place = extras.getLong(PLACE_ID, -1L).takeIf { it >= 0 } ?: return null
            return Show(
                placeId = place,
                day = if (extras.containsKey(DAY)) LocalDate.ofEpochDay(extras.getLong(DAY)) else null,
                hour = if (extras.containsKey(HOUR)) Instant.ofEpochSecond(extras.getLong(HOUR)) else null,
            )
        }

        /** Removes any request from [intent], so recreating the activity does not replay it. */
        fun clear(intent: Intent?) {
            ALL.forEach { intent?.removeExtra(it) }
        }
    }
}

/** A request plus an id, so tapping the same thing twice makes two requests. */
data class PendingRequest(val id: Long, val request: AppRequest)
