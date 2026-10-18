package com.twilighty.yuyu.feature.attendee.reminders

import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.core.network.model.RsvpStatus
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The two local reminders per confirmed registration. */
enum class ReminderKind(val before: Duration, val suffix: String) {
    DayBefore(Duration.ofHours(24), "24h"),
    HourBefore(Duration.ofHours(1), "1h"),
}

data class PlannedReminder(val rsvpId: String, val kind: ReminderKind, val fireAt: Instant) {
    /** Unique WorkManager name: one reminder per registration and kind (deduplicated by rsvpId). */
    val workName: String get() = workName(rsvpId, kind)

    companion object {
        const val PREFIX = "yuyu-reminder-"
        fun workName(rsvpId: String, kind: ReminderKind) = "$PREFIX$rsvpId-${kind.suffix}"
    }
}

/** Which reminders should exist for [registrations] at [now]. Pure, so it is unit-tested. */
object ReminderPlanner {
    fun plan(registrations: List<RegistrationDto>, now: Instant): List<PlannedReminder> =
        registrations
            .filter { it.status == RsvpStatus.CONFIRMED && it.checkedInAt == null }
            .flatMap { reg ->
                val start = EventTimeFormatter.parseInstant(reg.event.startDateTime) ?: return@flatMap emptyList()
                ReminderKind.entries.mapNotNull { kind ->
                    val fireAt = start.minus(kind.before)
                    if (fireAt.isAfter(now)) PlannedReminder(reg.rsvpId, kind, fireAt) else null
                }
            }
}

/** The WorkManager operations reminders need; faked in tests. */
interface ReminderWork {
    /** Unique names of reminder work that is still enqueued (not finished or cancelled). */
    suspend fun scheduledNames(): Set<String>

    /** Enqueue or replace the unique work [reminder.workName] to fire after [delayMillis]. */
    fun schedule(reminder: PlannedReminder, delayMillis: Long)

    fun cancel(workName: String)
}

/**
 * Keeps WorkManager in line with the user's upcoming confirmed registrations:
 * schedules 24 h and 1 h reminders (replacing earlier ones, so a changed start
 * time reschedules), and cancels reminders whose registration is gone,
 * cancelled, checked in or past — or all of them when reminders are off.
 */
@Singleton
class ReminderScheduler @Inject constructor(private val work: ReminderWork) {

    /** Returns the reminders that are now scheduled. */
    suspend fun sync(upcoming: List<RegistrationDto>, enabled: Boolean, now: Instant = Instant.now()): List<PlannedReminder> {
        val planned = if (enabled) ReminderPlanner.plan(upcoming, now) else emptyList()
        val wanted = planned.map { it.workName }.toSet()
        work.scheduledNames().filter { it.startsWith(PlannedReminder.PREFIX) && it !in wanted }.forEach(work::cancel)
        planned.forEach { work.schedule(it, Duration.between(now, it.fireAt).toMillis().coerceAtLeast(0)) }
        return planned
    }
}
