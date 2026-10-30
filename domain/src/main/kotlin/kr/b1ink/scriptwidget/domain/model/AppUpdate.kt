package kr.b1ink.scriptwidget.domain.model

/** A published app release (a GitHub release). */
data class AppRelease(
    /** The tag, e.g. "v0.2.0". */
    val version: String,
    val pageUrl: String,
    /** The APK for this device, if the release has one. */
    val apkUrl: String?,
    val notes: String,
)

sealed interface UpdateStatus {
    data class UpToDate(val current: String) : UpdateStatus
    data class Available(val current: String, val release: AppRelease) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/** "1.2.3" or "v1.2.3-dev": numeric parts, then a pre-release suffix that sorts before the release. */
data class AppVersion(val parts: List<Int>, val preRelease: String?) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val diff = parts.getOrElse(i) { 0 } - other.parts.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0
    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode() * 31 + preRelease.hashCode()

    companion object {
        private val PATTERN = Regex("""v?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]+))?""")

        fun parse(text: String): AppVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            return AppVersion(match.groupValues[1].split('.').map { it.toInt() }, match.groupValues[2].ifEmpty { null })
        }
    }
}
