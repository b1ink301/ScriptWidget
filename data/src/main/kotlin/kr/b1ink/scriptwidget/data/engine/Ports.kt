package kr.b1ink.scriptwidget.data.engine

import kotlinx.serialization.Serializable

/** Request description sent from JS (`Request` class in prelude.js). */
@Serializable
data class HttpRequestSpec(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** Seconds, as in Scriptable's `timeoutInterval`. */
    val timeout: Double = 30.0,
)

/** Response returned to JS. */
@Serializable
data class HttpResponseSpec(
    val url: String,
    val statusCode: Int,
    val headers: Map<String, String>,
    val mimeType: String?,
    val body: String,
)

/** Network access used by `Request`. */
interface HttpGateway {
    /** Returns the response for any status code, like Scriptable. */
    suspend fun loadString(spec: HttpRequestSpec): HttpResponseSpec

    /** @throws java.io.IOException on network errors or non-2xx status. */
    suspend fun loadBytes(spec: HttpRequestSpec): ByteArray
}

@Serializable
data class CachedImage(val src: String, val width: Int, val height: Int)

/**
 * Stores images downloaded by `Request.loadImage()` as files. Each run writes into its own
 * `runKey` directory under `cacheKey`, so a failed run never deletes the images the widget shows.
 */
interface ImageStore {
    fun store(runKey: String, index: Int, bytes: ByteArray): CachedImage
    fun retainOnly(cacheKey: String, runKey: String)
    fun clear(key: String)

    /** Raw bytes of an image previously returned by [store]; null for anything else. */
    fun bytes(src: String): ByteArray?
}

/** Persistent per-app secret storage behind `Keychain`. Values must be stored encrypted. */
interface SecretStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
    fun contains(key: String): Boolean
}

/**
 * File access behind `FileManager`. Every path must stay inside the script sandbox;
 * implementations throw [SecurityException] otherwise. Relative paths resolve against documents.
 */
interface ScriptFileSystem {
    val documentsDirectory: String
    val cacheDirectory: String
    val temporaryDirectory: String

    /** Where file bookmarks live (one file per bookmark name); null when there are none. */
    val bookmarksDirectory: String? get() = null

    fun readString(path: String): String?
    fun writeString(path: String, content: String)
    fun readBytes(path: String): ByteArray?
    fun writeBytes(path: String, bytes: ByteArray)
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun createDirectory(path: String, intermediate: Boolean)
    fun list(path: String): List<String>
    fun remove(path: String)
    fun modificationDate(path: String): Long?
    fun size(path: String): Long?
}

@Serializable
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val horizontalAccuracy: Double? = null,
    val verticalAccuracy: Double? = null,
    /** Epoch millis of the fix. */
    val timestamp: Long = 0,
)

@Serializable
data class Placemark(
    val name: String? = null,
    val thoroughfare: String? = null,
    val subThoroughfare: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val administrativeArea: String? = null,
    val subAdministrativeArea: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val isoCountryCode: String? = null,
)

enum class LocationAccuracy { Best, TenMeters, HundredMeters, Kilometer, ThreeKilometers }

class LocationUnavailableException(
    message: String,
    /** True when the user hasn't granted location access; cached locations must not be used then. */
    val permissionDenied: Boolean = false,
) : Exception(message)

/** Device location behind `Location`. */
interface LocationProvider {
    /** @throws LocationUnavailableException */
    suspend fun current(accuracy: LocationAccuracy): GeoLocation

    /** @throws LocationUnavailableException when geocoding isn't available. */
    suspend fun reverseGeocode(latitude: Double, longitude: Double, locale: String?): List<Placemark>
}

/** Source code of other scripts for `importModule()`. */
fun interface ModuleSource {
    fun load(name: String): String?
}

@Serializable
data class NotificationSpec(
    val identifier: String,
    val title: String = "",
    val subtitle: String? = null,
    val body: String = "",
    val threadIdentifier: String? = null,
    val openURL: String? = null,
    /** Epoch millis; null or past = deliver now. */
    val triggerAt: Long? = null,
    /** Script to run when the notification is tapped (and it has no [openURL]). */
    val scriptName: String? = null,
    /** JSON object handed back to that script as `args.notification.userInfo`. */
    val userInfo: String? = null,
    val actions: List<NotificationAction> = emptyList(),
)

/** A notification button that opens [url]. */
@kotlinx.serialization.Serializable
data class NotificationAction(val title: String, val url: String)

/** Local notifications behind `Notification`. Throws [SecurityException] without permission. */
interface Notifier {
    fun schedule(spec: NotificationSpec)

    /** Removes pending and delivered notifications with these identifiers. */
    fun remove(identifiers: List<String>)

    /** Scheduled, not yet shown (`Notification.allPending()`). */
    fun pending(): List<NotificationSpec> = emptyList()

    /** Shown and still in the notification shade (`Notification.allDelivered()`). */
    fun delivered(): List<NotificationSpec> = emptyList()
}

/** Renders a Material Icons glyph as a square PNG (backs `SFSymbol`). Null when the icon doesn't exist. */
fun interface SymbolRenderer {
    fun render(materialName: String, sizePx: Int, weight: String): ByteArray?
}

/** A snapshot of the device, taken once per run (backs `Device`). */
@Serializable
data class DeviceInfo(
    val name: String,
    val systemName: String = "Android",
    val systemVersion: String,
    val model: String,
    val isPhone: Boolean,
    val isPad: Boolean = !isPhone,
    /** Screen size in points (dp) and the pixels-per-point scale. */
    val screenWidth: Float,
    val screenHeight: Float,
    val screenScale: Float,
    val screenBrightness: Double,
    /** "portrait" | "landscapeLeft" | "landscapeRight" | "unknown" */
    val orientation: String,
    val batteryLevel: Double,
    val isCharging: Boolean,
    val isFullyCharged: Boolean,
    val preferredLanguages: List<String>,
    val locale: String,
    val isUsingDarkAppearance: Boolean,
    val volume: Double,
)

/** Opens a URL outside the script (backs `Safari`). */
fun interface UrlOpener {
    fun open(url: String)
}
