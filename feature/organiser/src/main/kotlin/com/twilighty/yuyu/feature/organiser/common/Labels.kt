package com.twilighty.yuyu.feature.organiser.common

import com.twilighty.yuyu.core.common.time.EventTimeFormatter
import com.twilighty.yuyu.core.designsystem.component.StatusTone
import com.twilighty.yuyu.core.network.model.EventPermissionsDto
import java.time.Instant

/** RSVP statuses as sent by the API. */
object RsvpStatus {
    const val CONFIRMED = "CONFIRMED"
    const val WAITLISTED = "WAITLISTED"
    const val PENDING_APPROVAL = "PENDING_APPROVAL"
    const val REJECTED = "REJECTED"
}

data class StatusLabel(val text: String, val tone: StatusTone)

/** Event status chip: Draft, Live, Offline, Cancelled. */
fun eventStatusLabel(status: String?): StatusLabel = when (status) {
    "DRAFT" -> StatusLabel("Draft", StatusTone.Neutral)
    "PUBLISHED" -> StatusLabel("Live", StatusTone.Success)
    "HIDDEN" -> StatusLabel("Offline", StatusTone.Warning)
    "CANCELLED" -> StatusLabel("Cancelled", StatusTone.Danger)
    else -> StatusLabel("Draft", StatusTone.Neutral)
}

/** Guest status chip, worded as on the web dashboard. */
fun guestStatusLabel(status: String): StatusLabel = when (status) {
    RsvpStatus.CONFIRMED -> StatusLabel("Going", StatusTone.Success)
    RsvpStatus.WAITLISTED -> StatusLabel("Waitlist", StatusTone.Warning)
    RsvpStatus.PENDING_APPROVAL -> StatusLabel("To approve", StatusTone.Warning)
    RsvpStatus.REJECTED -> StatusLabel("Declined", StatusTone.Danger)
    else -> StatusLabel(status.lowercase().replaceFirstChar { it.uppercase() }, StatusTone.Neutral)
}

/** "Checked in 9:30 AM" (same day as [now]) or "Checked in Wed 14 Oct", in the event timezone. */
fun checkedInLabel(checkedInAt: String?, timezone: String?, now: Instant = Instant.now()): String? {
    val at = EventTimeFormatter.parseInstant(checkedInAt) ?: return null
    val zone = EventTimeFormatter.zone(timezone)
    val sameDay: Boolean = at.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
    return "Checked in " + if (sameDay) EventTimeFormatter.time(at, timezone) else EventTimeFormatter.dayShort(at, timezone)
}

/** Whole-event date line in the event timezone: `Wednesday, 14 October · 5:00 PM – 9:00 PM BST`. */
fun eventDateLine(start: String, end: String?, timezone: String?): String {
    val s = EventTimeFormatter.parseInstant(start) ?: return ""
    val e = EventTimeFormatter.parseInstant(end)
    val sameDay = e == null || EventTimeFormatter.zoned(s, timezone).toLocalDate() == EventTimeFormatter.zoned(e, timezone).toLocalDate()
    return if (sameDay) {
        EventTimeFormatter.dayLong(s, timezone) + " · " + EventTimeFormatter.timeRange(s, e, timezone)
    } else {
        EventTimeFormatter.timeRange(s, e, timezone)
    }
}

/** Which areas an organiser may open; flags only hide controls (the server re-checks). */
val EventPermissionsDto.canSeeGuests: Boolean get() = manageRegistrations || checkIn
val EventPermissionsDto.hasDayOfControls: Boolean get() = schedule || manageFeedback || manageRegistrationWindow
