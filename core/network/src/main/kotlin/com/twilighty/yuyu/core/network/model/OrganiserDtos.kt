package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class EventCountsDto(
    val confirmed: Int = 0,
    val waitlisted: Int = 0,
    val pending: Int = 0,
    val checkedIn: Int = 0,
    val capacity: Int? = null,
    /**
     * What [capacity] is compared with: equals [confirmed] on individual events;
     * on team events, confirmed teams plus confirmed solo registrants, while
     * [confirmed] stays a count of people. Required by the contract; null only
     * from servers older than the field.
     */
    val capacityUsed: Int? = null,
) {
    /** Places taken against [capacity] (falls back to [confirmed] for older servers). */
    val placesTaken: Int get() = capacityUsed ?: confirmed
}

/**
 * UI hints only: every mutation is re-checked on the server. [manageRegistrations],
 * [sendEmails], [manageFeedback] and [manageRegistrationWindow] are true only
 * for organisation owners/admins; hide those controls otherwise.
 */
@Serializable
data class EventPermissionsDto(
    val checkIn: Boolean = false,
    val manageRegistrations: Boolean = false,
    val sendEmails: Boolean = false,
    val schedule: Boolean = false,
    val manageFeedback: Boolean = false,
    val manageRegistrationWindow: Boolean = false,
)

/** `EventSummary & { counts, myPermissions }` in `GET /orgs/:orgSlug/events`. */
@Serializable
data class OrgEventDto(
    val id: String,
    val slug: String,
    val title: String,
    val coverImageUrl: String? = null,
    val startDateTime: String,
    val endDateTime: String? = null,
    val timezone: String = "UTC",
    val location: String? = null,
    val mapLinkUrl: String? = null,
    val isOnline: Boolean = false,
    val accentColor: String? = null,
    val status: String? = null,
    val organisation: OrgRefDto,
    /** Null when the caller has neither manageRegistrations nor checkIn: hide counts then. */
    val counts: EventCountsDto? = null,
    val myPermissions: EventPermissionsDto = EventPermissionsDto(),
)

@Serializable
data class OrgEventsDto(val events: List<OrgEventDto> = emptyList())

/** `filter` query values for `GET /orgs/:orgSlug/events`. */
object OrgEventsFilter {
    const val UPCOMING = "upcoming"
    const val PAST = "past"
    const val ALL = "all"
}

@Serializable
data class OrgEventDetailResponseDto(
    val event: OrgEventDetailDto,
    /** Null when the caller has neither manageRegistrations nor checkIn. */
    val counts: EventCountsDto? = null,
    val myPermissions: EventPermissionsDto = EventPermissionsDto(),
)

@Serializable
data class OrgEventDetailDto(
    val id: String,
    val slug: String,
    val title: String,
    val coverImageUrl: String? = null,
    val startDateTime: String,
    val endDateTime: String? = null,
    val timezone: String = "UTC",
    val location: String? = null,
    val mapLinkUrl: String? = null,
    val isOnline: Boolean = false,
    val accentColor: String? = null,
    val status: String? = null,
    val organisation: OrgRefDto,
    val privacyType: String? = null,
    val registrationOpen: Boolean = false,
    val registrationClosesAt: String? = null,
    /** UI-safe reason when registration is closed. */
    val registrationClosedReason: String? = null,
    /** Sign-ups were closed from an app and can be reopened there: show "Reopen sign-ups" only then. */
    val registrationManuallyClosed: Boolean = false,
    val feedbackOpen: Boolean = false,
    val eventUrl: String? = null,
    val shortUrl: String? = null,
)

@Serializable
data class GuestDto(
    val rsvpId: String,
    val displayName: String,
    val email: String? = null,
    val status: String,
    val checkedInAt: String? = null,
    val registeredAt: String,
    val isGuest: Boolean = false,
)

@Serializable
data class GuestPageDto(
    val guests: List<GuestDto> = emptyList(),
    val nextCursor: String? = null,
)

/** `Guest & { answers }`. */
@Serializable
data class GuestDetailDto(
    val rsvpId: String,
    val displayName: String,
    val email: String? = null,
    val status: String,
    val checkedInAt: String? = null,
    val registeredAt: String,
    val isGuest: Boolean = false,
    val answers: List<LabelValueDto> = emptyList(),
)

/** Query values for the guest list. */
object GuestFilter {
    const val STATUS_ALL = "ALL"
    const val CHECKED_IN_ANY = "any"
    const val CHECKED_IN_YES = "yes"
    const val CHECKED_IN_NO = "no"
}

/** `POST …/guests` (201). [answers] keyed by registration field key. */
@Serializable
data class AddGuestRequest(
    val name: String,
    val email: String,
    val answers: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

@Serializable
data class UndoIdDto(val undoId: String)

@Serializable
data class RestoreGuestRequest(val undoId: String)
