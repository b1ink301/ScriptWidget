package kr.b1ink.scriptwidget.data.format

import kotlinx.serialization.Serializable
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAccessor
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

/** Backs `Intl.NumberFormat`, `Intl.DateTimeFormat`, `toLocale*String()` and `DateFormatter` (QuickJS has no Intl). */
class NativeFormatter(private val defaultLocale: Locale, private val defaultZone: TimeZone) {

    @Serializable
    data class NumberSpec(
        val value: Double,
        val locale: String? = null,
        val style: String = "decimal",
        val currency: String? = null,
        val minimumFractionDigits: Int? = null,
        val maximumFractionDigits: Int? = null,
        val useGrouping: Boolean = true,
    )

    @Serializable
    data class DateSpec(
        val time: Double,
        val locale: String? = null,
        val dateStyle: String? = null,
        val timeStyle: String? = null,
        val pattern: String? = null,
        val timeZone: String? = null,
    )

    @Serializable
    data class ParseSpec(
        val text: String,
        val pattern: String,
        val locale: String? = null,
        val timeZone: String? = null,
    )

    fun number(spec: NumberSpec): String {
        val locale = localeOf(spec.locale)
        val format = when (spec.style) {
            "percent" -> NumberFormat.getPercentInstance(locale)
            "currency" -> NumberFormat.getCurrencyInstance(locale).apply {
                spec.currency?.let { currency = Currency.getInstance(it.uppercase()) }
            }
            else -> NumberFormat.getNumberInstance(locale)
        }
        format.roundingMode = RoundingMode.HALF_UP
        format.isGroupingUsed = spec.useGrouping
        spec.minimumFractionDigits?.let {
            format.minimumFractionDigits = it
            if (format.maximumFractionDigits < it) format.maximumFractionDigits = it
        }
        spec.maximumFractionDigits?.let {
            format.maximumFractionDigits = it
            if (format.minimumFractionDigits > it) format.minimumFractionDigits = it
        }
        return normalize(format.format(spec.value))
    }

    fun date(spec: DateSpec): String {
        val locale = localeOf(spec.locale)
        val dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(spec.time.toLong()), zoneOf(spec.timeZone))
        val formatter = when {
            spec.pattern != null -> DateTimeFormatter.ofPattern(spec.pattern, locale)
            spec.dateStyle != null && spec.timeStyle != null ->
                DateTimeFormatter.ofLocalizedDateTime(style(spec.dateStyle), style(spec.timeStyle))
            spec.timeStyle != null -> DateTimeFormatter.ofLocalizedTime(style(spec.timeStyle))
            else -> DateTimeFormatter.ofLocalizedDate(style(spec.dateStyle ?: "short"))
        }
        return normalize(formatter.withLocale(locale).format(dateTime))
    }

    /** Epoch millis, or null when [ParseSpec.text] doesn't match the pattern. */
    fun parse(spec: ParseSpec): Double? = runCatching {
        val zone = zoneOf(spec.timeZone)
        val parsed: TemporalAccessor = DateTimeFormatter.ofPattern(spec.pattern, localeOf(spec.locale))
            .parseBest(spec.text, ZonedDateTime::from, LocalDateTime::from, LocalDate::from)
        val instant = when (parsed) {
            is ZonedDateTime -> parsed.toInstant()
            is LocalDateTime -> parsed.atZone(zone).toInstant()
            is LocalDate -> parsed.atStartOfDay(zone).toInstant()
            else -> return@runCatching null
        }
        instant.toEpochMilli().toDouble()
    }.getOrNull()

    /** `{"locale":"ko-KR","timeZone":"Asia/Seoul"}` for `Intl.*.resolvedOptions()`. */
    fun defaults(): String = """{"locale":"${defaultLocale.toLanguageTag()}","timeZone":"${defaultZone.id}"}"""

    private fun localeOf(tag: String?): Locale = tag?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: defaultLocale

    private fun zoneOf(id: String?): ZoneId = id?.let(ZoneId::of) ?: defaultZone.toZoneId()

    private fun style(name: String): FormatStyle = when (name) {
        "full" -> FormatStyle.FULL
        "long" -> FormatStyle.LONG
        "short" -> FormatStyle.SHORT
        else -> FormatStyle.MEDIUM
    }

    /** CLDR uses narrow/no-break spaces (e.g. before AM/PM); scripts expect plain spaces. */
    private fun normalize(text: String) = text.replace(' ', ' ').replace(' ', ' ')
}
