package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /api/check-in/station` (existing web endpoint, cookie-based proof).
 * Errors come back as `{ ok:false, error:"text", needsForce? }`.
 */
@Serializable
data class StationRequest(
    val organisationSlug: String,
    val eventSlug: String,
    val action: String,
    /** 8 digits, `unlock` only. Never log or persist. */
    val pin: String? = null,
    val rawInput: String? = null,
    val rsvpId: String? = null,
    val query: String? = null,
    val force: Boolean? = null,
) {
    override fun toString(): String = "StationRequest(org=$organisationSlug, event=$eventSlug, action=$action)"
}

object StationAction {
    const val UNLOCK = "unlock"
    const val BOOTSTRAP = "bootstrap"
    const val PREVIEW_TOKEN = "previewToken"
    const val PREVIEW_RSVP = "previewRsvp"
    const val CHECK_IN_RSVP = "checkInRsvp"
    const val LOOKUP = "lookup"
    const val UNDO = "undo"
}

@Serializable
data class StationBootstrapDto(
    val organisationName: String,
    val organisationLogoUrl: String? = null,
    val eventId: String,
    val eventTitle: String,
    val registrationFields: List<StationFieldDto> = emptyList(),
    val stats: StationStatsDto = StationStatsDto(),
    val recent: List<StationRecentDto> = emptyList(),
)

@Serializable
data class StationFieldDto(val key: String, val label: String)

@Serializable
data class StationStatsDto(val confirmed: Int = 0, val checkedIn: Int = 0)

@Serializable
data class StationRecentDto(
    val rsvpId: String,
    val displayName: String,
    val email: String? = null,
    val checkedInAt: String,
)

/** Preview / check-in result from the station endpoint. */
@Serializable
data class StationCheckInDto(
    val rsvpId: String,
    val displayName: String,
    val email: String? = null,
    val status: String,
    val alreadyCheckedIn: Boolean = false,
    val checkedInAt: String? = null,
    @SerialName("checkInQrToken") val checkInQrToken: String? = null,
    val checkInDetails: List<LabelValueDto> = emptyList(),
    val registrationDetails: List<LabelValueDto> = emptyList(),
    val gate: CheckInGateDto? = null,
) {
    override fun toString(): String = "StationCheckInDto(rsvpId=$rsvpId, status=$status)"
}

@Serializable
data class StationLookupDto(val rows: List<StationLookupRowDto> = emptyList())

@Serializable
data class StationLookupRowDto(
    val rsvpId: String,
    val displayName: String,
    val email: String? = null,
    val status: String,
    val checkedInAt: String? = null,
)
