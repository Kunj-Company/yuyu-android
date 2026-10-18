package com.twilighty.yuyu.feature.checkin.domain

import com.twilighty.yuyu.core.data.repository.OfflineGuest
import com.twilighty.yuyu.core.network.model.CheckInGateDto
import com.twilighty.yuyu.core.network.model.CheckInPreviewDto
import com.twilighty.yuyu.core.network.model.CheckInResultDto
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.core.network.model.StationCheckInDto
import com.twilighty.yuyu.core.network.model.StationLookupRowDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun CheckInGateDto?.toGate(): GateInfo =
    if (this == null || ok) GateInfo.Ok else GateInfo(false, reason, needsForce, blocked == true)

internal fun CheckInPreviewDto.toPreview() = GuestPreview(
    rsvpId = rsvpId,
    displayName = displayName,
    email = email,
    status = status,
    alreadyCheckedIn = alreadyCheckedIn,
    checkedInAt = checkedInAt,
    checkInDetails = checkInDetails,
    registrationDetails = registrationDetails,
    gate = gate.toGate(),
)

internal fun CheckInResultDto.toPreview() = GuestPreview(
    rsvpId = rsvpId,
    displayName = displayName,
    email = email,
    status = status,
    alreadyCheckedIn = alreadyCheckedIn,
    checkedInAt = checkedInAt,
    checkInDetails = checkInDetails,
    registrationDetails = registrationDetails,
)

internal fun StationCheckInDto.toPreview() = GuestPreview(
    rsvpId = rsvpId,
    displayName = displayName,
    email = email,
    status = status,
    alreadyCheckedIn = alreadyCheckedIn,
    checkedInAt = checkedInAt,
    checkInDetails = checkInDetails,
    registrationDetails = registrationDetails,
    gate = gate.toGate(),
)

/** An offline roster row as a preview, gated locally exactly as the server would. */
internal fun OfflineGuest.toPreview(force: Boolean = false) = GuestPreview(
    rsvpId = rsvpId,
    displayName = displayName,
    email = email,
    status = status,
    alreadyCheckedIn = checkedInAt != null,
    checkedInAt = checkedInAt,
    checkInDetails = checkInDetails,
    registrationDetails = registrationDetails,
    gate = LocalCheckInGate.gate(status, force),
    offline = true,
)

internal fun GuestDto.toLookup() = LookupGuest(rsvpId, displayName, email, status, checkedInAt)
internal fun StationLookupRowDto.toLookup() = LookupGuest(rsvpId, displayName, email, status, checkedInAt)
internal fun OfflineGuest.toLookup() = LookupGuest(rsvpId, displayName, email, status, checkedInAt)

/** Door times are shown in the phone's zone: the volunteer is at the venue. */
object CheckInTime {
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dayTime = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.US)

    fun time(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? =
        parse(iso)?.let { time.format(it.atZone(zone)) }

    fun dayTime(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? =
        parse(iso)?.let { dayTime.format(it.atZone(zone)) }

    private fun parse(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
}

/** Status chip words, as on the web check-in desk. */
internal fun statusLabel(status: String): String = when (status) {
    "CONFIRMED" -> "Confirmed"
    "WAITLISTED" -> "Waitlisted"
    "PENDING_APPROVAL" -> "Pending approval"
    "REJECTED" -> "Rejected"
    "CANCELLED" -> "Cancelled"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
}
