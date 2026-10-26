package com.twilighty.yuyu.feature.organiser.schedule

import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import com.twilighty.yuyu.core.network.model.OrgSessionDto
import java.time.Duration
import java.time.Instant

/** A session with how far earlier delays in its track push it back (the web's "min behind"). */
data class SessionUi(
    val session: OrgSessionDto,
    /** Sum of the delays of earlier sessions in the same track; this session's own delay shifts only later ones. */
    val shiftMinutes: Int,
) {
    val plannedStart: Instant? get() = EventTimeFormatter.parseInstant(session.startDateTime)
    val plannedEnd: Instant? get() = EventTimeFormatter.parseInstant(session.endDateTime)

    /**
     * When it actually runs: planned + [shiftMinutes]. Computed here rather than
     * read from the server's `effective*` fields (the same rule) so a delay
     * saved from this device shows at once and older servers work too.
     */
    val liveStart: Instant? get() = plannedStart?.plusSeconds(shiftMinutes * 60L)
    val liveEnd: Instant? get() = plannedEnd?.plusSeconds(shiftMinutes * 60L)
}

data class TrackUi(val id: String?, val name: String, val sessions: List<SessionUi>)

/**
 * Mirrors the website's `effectiveEventProgram`: planned times never change; a
 * session's delay is added to every later session in the same track, and
 * delays add up down the track.
 */
internal fun OrgScheduleDto.toTrackUi(): List<TrackUi> = tracks.map { track ->
    var running = 0
    val sorted = track.sessions.withIndex().sortedWith(
        compareBy({ EventTimeFormatter.parseInstant(it.value.startDateTime)?.toEpochMilli() ?: Long.MAX_VALUE }, { it.index }),
    ).map { it.value }
    TrackUi(
        id = track.id,
        name = track.name,
        sessions = sorted.map { session ->
            SessionUi(session, running).also { running += session.delayMinutes }
        },
    )
}

/** Where a session is relative to [now], from its live times. */
sealed interface LiveStatus {
    /** [startsIn] until it begins. */
    data class Upcoming(val startsIn: Duration) : LiveStatus

    /** Running: [remaining] until its live end, [progress] 0..1 through it. */
    data class Live(val remaining: Duration, val progress: Float) : LiveStatus

    data object Ended : LiveStatus

    /** No usable start time. */
    data object Unknown : LiveStatus
}

/** A session without an end counts as running for its first [OPEN_ENDED] after the start. */
private val OPEN_ENDED: Duration = Duration.ofMinutes(30)

fun SessionUi.liveStatus(now: Instant): LiveStatus {
    val start = liveStart ?: return LiveStatus.Unknown
    val end = liveEnd?.takeIf { it.isAfter(start) } ?: start.plus(OPEN_ENDED)
    return when {
        now.isBefore(start) -> LiveStatus.Upcoming(Duration.between(now, start))
        now.isBefore(end) -> {
            val total = Duration.between(start, end).toMillis().coerceAtLeast(1)
            val done = Duration.between(start, now).toMillis()
            LiveStatus.Live(Duration.between(now, end), (done.toFloat() / total).coerceIn(0f, 1f))
        }
        else -> LiveStatus.Ended
    }
}

/** `45 s`, `9:05` (under ten minutes, ticking), `25 min`, `1 h 5 min`, `2 days`. */
fun formatCountdown(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "$seconds s"
        seconds < 600 -> "%d:%02d".format(seconds / 60, seconds % 60)
        seconds < 3600 -> "${(seconds + 59) / 60} min"
        seconds < 86_400 -> {
            val minutes = (seconds + 59) / 60
            if (minutes % 60 == 0L) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
        }
        else -> {
            val days = seconds / 86_400
            if (days == 1L) "1 day" else "$days days"
        }
    }
}

fun LiveStatus.label(): String? = when (this) {
    is LiveStatus.Upcoming -> "Starts in ${formatCountdown(startsIn)}"
    is LiveStatus.Live -> "${formatCountdown(remaining)} left"
    LiveStatus.Ended -> "Ended"
    LiveStatus.Unknown -> null
}

/** One session with its track, for the "now" and "next" summaries. */
data class TrackedSession(val trackName: String, val item: SessionUi)

data class NowAndNext(val now: List<TrackedSession>, val next: List<TrackedSession>)

/**
 * Sessions running at [now] in any track, and the next session to start in each
 * track (soonest first, at most [maxNext]).
 */
fun List<TrackUi>.nowAndNext(now: Instant, maxNext: Int = 3): NowAndNext {
    val running = flatMap { track ->
        track.sessions.filter { it.liveStatus(now) is LiveStatus.Live }.map { TrackedSession(track.name, it) }
    }.sortedBy { it.item.liveEnd ?: Instant.MAX }
    val upcoming = mapNotNull { track ->
        track.sessions.filter { it.liveStatus(now) is LiveStatus.Upcoming }
            .minByOrNull { it.liveStart ?: Instant.MAX }
            ?.let { TrackedSession(track.name, it) }
    }.sortedBy { it.item.liveStart ?: Instant.MAX }.take(maxNext)
    return NowAndNext(running, upcoming)
}
