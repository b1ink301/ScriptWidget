package kr.b1ink.scriptwidget.data.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kr.b1ink.scriptwidget.data.engine.GeoLocation
import kr.b1ink.scriptwidget.data.engine.LocationAccuracy
import kr.b1ink.scriptwidget.data.engine.LocationProvider
import kr.b1ink.scriptwidget.data.engine.LocationUnavailableException
import kr.b1ink.scriptwidget.data.engine.Placemark
import java.util.Locale
import kotlin.coroutines.resume

/** [LocationProvider] using the platform [LocationManager] (no Play Services dependency). */
class AndroidLocationProvider(
    context: Context,
    private val hasPermission: () -> Boolean = { hasLocationPermission(context) },
    private val fixTimeoutMillis: Long = 15_000,
) : LocationProvider {
    private val context = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun current(accuracy: LocationAccuracy): GeoLocation {
        if (!hasPermission()) {
            throw LocationUnavailableException(
                "Location permission not granted. Open ScriptWidget and allow location access.",
                permissionDenied = true,
            )
        }
        try {
            val providers = providersFor(accuracy).filter { manager.isProviderEnabled(it) }
            // One time budget for all providers, so a missing GPS fix can't use up the script's timeout.
            val deadline = System.currentTimeMillis() + fixTimeoutMillis
            for (provider in providers) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                currentFix(provider, left)?.let { return it.toGeoLocation() }
            }
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { return it.toGeoLocation() }
        } catch (e: SecurityException) {
            throw LocationUnavailableException("Location permission not granted: ${e.message}", permissionDenied = true)
        }
        throw LocationUnavailableException("Location is currently unavailable")
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(provider: String, timeoutMillis: Long): Location? = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(context)) {
                if (cont.isActive) cont.resume(it)
            }
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun reverseGeocode(latitude: Double, longitude: Double, locale: String?): List<Placemark> {
        if (!Geocoder.isPresent()) throw LocationUnavailableException("Reverse geocoding is not available on this device")
        val geocoder = Geocoder(context, locale?.let(Locale::forLanguageTag) ?: Locale.getDefault())
        return withContext(Dispatchers.IO) {
            geocoder.getFromLocation(latitude, longitude, MAX_RESULTS).orEmpty().map {
                Placemark(
                    name = it.featureName,
                    thoroughfare = it.thoroughfare,
                    subThoroughfare = it.subThoroughfare,
                    locality = it.locality,
                    subLocality = it.subLocality,
                    administrativeArea = it.adminArea,
                    subAdministrativeArea = it.subAdminArea,
                    postalCode = it.postalCode,
                    country = it.countryName,
                    isoCountryCode = it.countryCode,
                )
            }
        }
    }

    private fun providersFor(accuracy: LocationAccuracy): List<String> {
        val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(LocationManager.FUSED_PROVIDER) else emptyList()
        return when (accuracy) {
            LocationAccuracy.Best, LocationAccuracy.TenMeters ->
                listOf(LocationManager.GPS_PROVIDER) + fused + LocationManager.NETWORK_PROVIDER
            else -> fused + LocationManager.NETWORK_PROVIDER + LocationManager.GPS_PROVIDER
        }
    }

    private fun Location.toGeoLocation() = GeoLocation(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else null,
        horizontalAccuracy = if (hasAccuracy()) accuracy.toDouble() else null,
        verticalAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters.toDouble() else null,
        timestamp = time,
    )

    companion object {
        private const val MAX_RESULTS = 5

        fun hasLocationPermission(context: Context): Boolean =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
    }
}
