package com.twilighty.yuyu.feature.checkin.domain

import com.twilighty.yuyu.core.network.model.LabelValueDto

/** The server's `CheckInGate`: `{ ok:true }` or `{ ok:false, reason, needsForce, blocked? }`. */
data class GateInfo(
    val ok: Boolean,
    val reason: String? = null,
    val needsForce: Boolean = false,
    val blocked: Boolean = false,
) {
    companion object {
        val Ok = GateInfo(ok = true)
    }
}

/**
 * One guest at the door, from an online preview (organiser or station) or the
 * offline roster ([offline] = true; its gate was computed locally).
 * Never carries the ticket token.
 */
data class GuestPreview(
    val rsvpId: String,
    val displayName: String,
    val email: String?,
    val status: String,
    val alreadyCheckedIn: Boolean,
    val checkedInAt: String?,
    val checkInDetails: List<LabelValueDto> = emptyList(),
    val registrationDetails: List<LabelValueDto> = emptyList(),
    val gate: GateInfo = GateInfo.Ok,
    val offline: Boolean = false,
)

/** Outcome of a successful check-in request. */
data class AdmitResult(
    val guest: GuestPreview,
    val checkedInAt: String?,
    /** The guest had already been checked in (by someone else, or earlier). */
    val alreadyCheckedIn: Boolean,
    val offline: Boolean = false,
)

/** A name/email search result. */
data class LookupGuest(
    val rsvpId: String,
    val displayName: String,
    val email: String?,
    val status: String,
    val checkedInAt: String?,
)

data class RecentCheckIn(val rsvpId: String, val displayName: String, val checkedInAt: String)

data class CheckInCounts(val checkedIn: Int, val confirmed: Int) {
    val progress: Float get() = if (confirmed <= 0) 0f else (checkedIn.toFloat() / confirmed).coerceIn(0f, 1f)
}

/** The three result feedbacks (colour, haptic and sound). */
enum class FeedbackKind { Valid, Already, Invalid }

/** How the desk finds a guest. */
enum class DeskMode(val label: String) { Scan("Scan"), Code("Type code"), Search("Search") }
