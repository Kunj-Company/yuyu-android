package com.twilighty.yuyu.core.common.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The one date/time formatter (docs/DESIGN.md §6.1). Every event time is shown
 * in the **event's** IANA timezone, never the phone's, with no seconds and no
 * US `MM/DD/YYYY`. Show the zone abbreviation once per section via [zoneLabel].
 */
object EventTimeFormatter {
    private val locale: Locale get() = Locale.UK

    /** Parses an API ISO-8601 instant; null when missing or malformed. */
    fun parseInstant(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** Resolves an IANA zone id, falling back to UTC for unknown ids. */
    fun zone(timezone: String?): ZoneId = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")

    data class DateBlock(val month: String, val day: String)

    /** `OCT` / `14`. */
    fun dateBlock(instant: Instant, timezone: String?): DateBlock {
        val z = instant.atZone(zone(timezone))
        return DateBlock(
            month = z.month.getDisplayName(TextStyle.SHORT, locale).take(3).uppercase(locale),
            day = z.dayOfMonth.toString(),
        )
    }

    /** `Wednesday, 14 October`. */
    fun dayLong(instant: Instant, timezone: String?): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(instant.atZone(zone(timezone)))

    /** `Wed 14 Oct`. */
    fun dayShort(instant: Instant, timezone: String?): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(instant.atZone(zone(timezone)))

    /** `9:30 AM`. */
    fun time(instant: Instant, timezone: String?): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(instant.atZone(zone(timezone)))

    /** `IST`, `BST`, `UTC`: the zone abbreviation at that instant. */
    fun zoneLabel(instant: Instant, timezone: String?): String =
        DateTimeFormatter.ofPattern("zzz", Locale.US).format(instant.atZone(zone(timezone)))

    /**
     * `9:30 AM – 5:30 PM IST` on one day, or `Wed 14 Oct, 9:30 AM – Thu 15 Oct, 5:30 PM IST`
     * across days. [withZone] appends the abbreviation once.
     */
    fun timeRange(start: Instant, end: Instant?, timezone: String?, withZone: Boolean = true): String {
        val z = zone(timezone)
        val suffix = if (withZone) " " + zoneLabel(start, timezone) else ""
        if (end == null) return time(start, timezone) + suffix
        val sameDay = start.atZone(z).toLocalDate() == end.atZone(z).toLocalDate()
        return if (sameDay) {
            "${time(start, timezone)} – ${time(end, timezone)}$suffix"
        } else {
            "${dayShort(start, timezone)}, ${time(start, timezone)} – ${dayShort(end, timezone)}, ${time(end, timezone)}$suffix"
        }
    }

    /** `Today` / `Tomorrow` / `In 9 days` / `Yesterday` / `3 days ago` / `Wed 14 Oct`. */
    fun relativeDay(instant: Instant, timezone: String?, now: Instant = Instant.now()): String {
        val z = zone(timezone)
        val days = ChronoUnit.DAYS.between(now.atZone(z).toLocalDate(), instant.atZone(z).toLocalDate())
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days == -1L -> "Yesterday"
            days in 2..30 -> "In $days days"
            days in -7..-2 -> "${-days} days ago"
            else -> dayShort(instant, timezone)
        }
    }

    /** `Just now` / `2 min ago` / `3 h ago` / `Yesterday` / `13 Oct 2026` (older than 7 days). */
    fun relativeTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String {
        val elapsed = Duration.between(instant, now)
        val minutes = elapsed.toMinutes()
        val today: LocalDate = now.atZone(zone).toLocalDate()
        val day: LocalDate = instant.atZone(zone).toLocalDate()
        return when {
            elapsed.isNegative || minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            day == today -> "${elapsed.toHours()} h ago"
            day == today.minusDays(1) -> "Yesterday"
            ChronoUnit.DAYS.between(day, today) < 7 -> "${ChronoUnit.DAYS.between(day, today)} days ago"
            else -> DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(instant.atZone(zone))
        }
    }

    /** True when the event has ended (or started, without an end) before [now]. */
    fun isPast(start: Instant, end: Instant?, now: Instant = Instant.now()): Boolean = (end ?: start).isBefore(now)

    fun zoned(instant: Instant, timezone: String?): ZonedDateTime = instant.atZone(zone(timezone))
}
