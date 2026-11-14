package dev.antomanc.nembo.core.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dev.antomanc.nembo.core.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Device position through the platform LocationManager only: no Play Services, so the same
 * code works on F-Droid builds and de-Googled phones. Coarse location is plenty for weather.
 */
class DeviceLocation(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** A fresh fix if one arrives within [timeoutMs], else the most recent known one. */
    suspend fun locate(timeoutMs: Long = 10_000): GeoPoint? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val fresh = provider?.let { p ->
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        lm.getCurrentLocation(p, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                    } catch (_: SecurityException) {
                        cont.resume(null)
                    } catch (_: IllegalArgumentException) {
                        cont.resume(null)
                    }
                }
            }
        }
        val location = fresh ?: lastKnown(lm)
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private fun lastKnown(lm: LocationManager) = try {
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    } catch (_: SecurityException) {
        null
    }

    /** Best-effort place name. Geocoder can be missing on de-Googled devices: then null. */
    suspend fun placeName(point: GeoPoint, locale: Locale = Locale.getDefault()): Pair<String, String?>? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, locale)
        return withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(point.latitude, point.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            cont.resume(addresses.firstOrNull()?.let(::nameOf))
                        }

                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(point.latitude, point.longitude, 1) }
                        .getOrNull()?.firstOrNull()?.let(::nameOf)
                }
            }
        }
    }

    private fun nameOf(a: android.location.Address): Pair<String, String?>? {
        val name = a.locality ?: a.subAdminArea ?: a.adminArea ?: return null
        return name to (a.adminArea?.takeIf { it != name } ?: a.countryName)
    }
}
