package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class CheckInResultDto(
    val rsvpId: String,
    val displayName: String,
    val email: String? = null,
    val status: String,
    val alreadyCheckedIn: Boolean = false,
    val checkedInAt: String? = null,
    val checkInDetails: List<LabelValueDto> = emptyList(),
    val registrationDetails: List<LabelValueDto> = emptyList(),
)

/** `{ "ok": true }` or `{ "ok": false, "reason", "needsForce", "blocked"? }`. */
@Serializable
data class CheckInGateDto(
    val ok: Boolean,
    val reason: String? = null,
    val needsForce: Boolean = false,
    val blocked: Boolean? = null,
)

/** `CheckInResult & { gate }`. */
@Serializable
data class CheckInPreviewDto(
    val rsvpId: String,
    val displayName: String,
    val email: String? = null,
    val status: String,
    val alreadyCheckedIn: Boolean = false,
    val checkedInAt: String? = null,
    val checkInDetails: List<LabelValueDto> = emptyList(),
    val registrationDetails: List<LabelValueDto> = emptyList(),
    val gate: CheckInGateDto = CheckInGateDto(ok = true),
)

/** Exactly one of [rawInput] / [rsvpId]. */
@Serializable
data class CheckInPreviewRequest(
    val rawInput: String? = null,
    val rsvpId: String? = null,
) {
    init {
        require((rawInput == null) != (rsvpId == null)) { "Provide exactly one of rawInput or rsvpId" }
    }
}

/** Exactly one of [rawInput] / [rsvpId]. */
@Serializable
data class CheckInRequest(
    val rawInput: String? = null,
    val rsvpId: String? = null,
    val force: Boolean = false,
) {
    init {
        require((rawInput == null) != (rsvpId == null)) { "Provide exactly one of rawInput or rsvpId" }
    }
}

@Serializable
data class RsvpIdRequest(val rsvpId: String)

@Serializable
data class LookupRowsDto(val rows: List<GuestDto> = emptyList())

@Serializable
data class CheckInStatsDto(
    val confirmed: Int = 0,
    val checkedIn: Int = 0,
    val recent: List<RecentCheckInDto> = emptyList(),
)

@Serializable
data class RecentCheckInDto(
    val rsvpId: String,
    val displayName: String,
    val checkedInAt: String,
)

@Serializable
data class RosterDto(
    val generatedAt: String,
    val rows: List<RosterRowDto> = emptyList(),
)

/** `Guest & { ticketToken, checkInDetails, registrationDetails }`. Results never carry a QR token otherwise. */
@Serializable
data class RosterRowDto(
    val rsvpId: String,
    val displayName: String,
    val email: String? = null,
    val status: String,
    val checkedInAt: String? = null,
    val registeredAt: String,
    val isGuest: Boolean = false,
    val ticketToken: String? = null,
    val checkInDetails: List<LabelValueDto> = emptyList(),
    val registrationDetails: List<LabelValueDto> = emptyList(),
) {
    override fun toString(): String = "RosterRowDto(rsvpId=$rsvpId, status=$status)"
}

@Serializable
data class SyncCheckInsRequest(val checkIns: List<OfflineCheckInDto>)

@Serializable
data class OfflineCheckInDto(
    val rsvpId: String,
    val checkedInAt: String,
    val force: Boolean = false,
    /** Idempotency key (8–128 chars): a UUID generated when the check-in was queued. */
    val clientMutationId: String? = null,
)

@Serializable
data class SyncCheckInsResultDto(
    val syncedIds: List<String> = emptyList(),
    val alreadyCheckedInIds: List<String> = emptyList(),
    val failed: List<SyncFailureDto> = emptyList(),
)

@Serializable
data class SyncFailureDto(val rsvpId: String, val error: String)
