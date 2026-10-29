package kr.b1ink.scriptwidget.data.location

import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.data.engine.GeoLocation
import kr.b1ink.scriptwidget.data.engine.LocationAccuracy
import kr.b1ink.scriptwidget.data.engine.LocationProvider
import kr.b1ink.scriptwidget.data.engine.LocationUnavailableException
import kr.b1ink.scriptwidget.data.engine.Placemark
import java.io.File

/**
 * Remembers the last successful fix in [cacheFile] and returns it when a new fix can't be
 * obtained (background widgets often can't get one). Never used when permission is missing.
 * Scripts can check `timestamp` to decide whether the location is fresh enough.
 */
class CachingLocationProvider(
    private val delegate: LocationProvider,
    private val cacheFile: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val hasPermission: () -> Boolean = { true },
) : LocationProvider {

    override suspend fun current(accuracy: LocationAccuracy): GeoLocation {
        // A fix that is recent enough for the requested accuracy saves turning on GPS again.
        if (hasPermission()) {
            load()?.takeIf { now() - it.timestamp in 0..maxAge(accuracy) }?.let { return it }
        }
        return try {
            delegate.current(accuracy).also(::save)
        } catch (e: LocationUnavailableException) {
            if (e.permissionDenied) throw e
            load() ?: throw e
        }
    }

    /** How old a reused fix may be: precise requests want a fresh one, coarse ones (weather) don't. */
    private fun maxAge(accuracy: LocationAccuracy): Long = when (accuracy) {
        LocationAccuracy.Best, LocationAccuracy.TenMeters -> 60_000L
        LocationAccuracy.HundredMeters -> 5 * 60_000L
        LocationAccuracy.Kilometer, LocationAccuracy.ThreeKilometers -> 15 * 60_000L
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double, locale: String?): List<Placemark> =
        delegate.reverseGeocode(latitude, longitude, locale)

    private fun save(location: GeoLocation) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(Json.encodeToString(GeoLocation.serializer(), location))
        }
    }

    private fun load(): GeoLocation? = runCatching {
        Json.decodeFromString(GeoLocation.serializer(), cacheFile.readText())
    }.getOrNull()
}
