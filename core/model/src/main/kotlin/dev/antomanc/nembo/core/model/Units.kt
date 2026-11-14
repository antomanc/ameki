package dev.antomanc.nembo.core.model

import kotlin.math.roundToInt

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"), FAHRENHEIT("°F");

    fun convert(celsius: Double): Double = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }
}

enum class WindSpeedUnit(val symbol: String) {
    KILOMETERS_PER_HOUR("km/h"), METERS_PER_SECOND("m/s"), MILES_PER_HOUR("mph"), KNOTS("kn");

    fun convert(kmh: Double): Double = when (this) {
        KILOMETERS_PER_HOUR -> kmh
        METERS_PER_SECOND -> kmh / 3.6
        MILES_PER_HOUR -> kmh / 1.609344
        KNOTS -> kmh / 1.852
    }
}

enum class PressureUnit(val symbol: String) {
    HECTOPASCAL("hPa"), MILLIBAR("mbar"), INCH_OF_MERCURY("inHg"), MILLIMETER_OF_MERCURY("mmHg");

    fun convert(hpa: Double): Double = when (this) {
        HECTOPASCAL, MILLIBAR -> hpa
        INCH_OF_MERCURY -> hpa * 0.0295299830714
        MILLIMETER_OF_MERCURY -> hpa * 0.750061683
    }
}

enum class PrecipitationUnit(val symbol: String) {
    MILLIMETER("mm"), INCH("in");

    fun convert(mm: Double): Double = when (this) {
        MILLIMETER -> mm
        INCH -> mm / 25.4
    }
}

enum class DistanceUnit(val symbol: String) {
    KILOMETER("km"), MILE("mi");

    fun convert(km: Double): Double = when (this) {
        KILOMETER -> km
        MILE -> km / 1.609344
    }
}

data class Units(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeed: WindSpeedUnit = WindSpeedUnit.KILOMETERS_PER_HOUR,
    val pressure: PressureUnit = PressureUnit.HECTOPASCAL,
    val precipitation: PrecipitationUnit = PrecipitationUnit.MILLIMETER,
    val distance: DistanceUnit = DistanceUnit.KILOMETER,
) {
    /** "21°": the short form used almost everywhere, as on Pixel. */
    fun temp(celsius: Double): String = "${temperature.convert(celsius).roundToInt()}°"

    fun tempWithUnit(celsius: Double): String = "${temperature.convert(celsius).roundToInt()}${temperature.symbol}"

    fun wind(kmh: Double): String = "${windSpeed.convert(kmh).roundToInt()} ${windSpeed.symbol}"

    fun pressure(hpa: Double): String = when (pressure) {
        PressureUnit.INCH_OF_MERCURY -> "%.2f %s".format(pressure.convert(hpa), pressure.symbol)
        else -> "${pressure.convert(hpa).roundToInt()} ${pressure.symbol}"
    }

    fun precipitation(mm: Double): String = when (precipitation) {
        PrecipitationUnit.MILLIMETER -> if (mm < 10) "%.1f mm".format(mm) else "${mm.roundToInt()} mm"
        PrecipitationUnit.INCH -> "%.2f in".format(precipitation.convert(mm))
    }

    fun distance(km: Double): String {
        val v = distance.convert(km)
        return if (v < 10) "%.1f %s".format(v, distance.symbol) else "${v.roundToInt()} ${distance.symbol}"
    }

    companion object {
        /** Sensible defaults by region: imperial only where people actually use it. */
        fun forCountry(countryCode: String?): Units = when (countryCode?.uppercase()) {
            "US", "LR", "MM" -> Units(
                TemperatureUnit.FAHRENHEIT, WindSpeedUnit.MILES_PER_HOUR, PressureUnit.INCH_OF_MERCURY,
                PrecipitationUnit.INCH, DistanceUnit.MILE,
            )
            "GB" -> Units(windSpeed = WindSpeedUnit.MILES_PER_HOUR, distance = DistanceUnit.MILE)
            else -> Units()
        }
    }
}

/** 16-point compass, index 0 = N. */
fun compassPoint(degrees: Int): Int = (((degrees % 360 + 360) % 360 + 11.25) / 22.5).toInt() % 16
