package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The shared event card shape. Times are ISO-8601 UTC; format them in [timezone]. */
@Serializable
data class EventSummaryDto(
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
)

@Serializable
data class EventPageDto(
    val events: List<EventSummaryDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class ShortLinkDto(val orgSlug: String, val eventSlug: String)

/** `GET /public/events/:orgSlug/:eventSlug`. */
@Serializable
data class PublicEventResponseDto(
    val event: PublicEventDto,
    val registration: RegistrationInfoDto,
    val schedule: List<ScheduleTrackDto> = emptyList(),
    val speakers: List<SpeakerDto> = emptyList(),
    val feedbackOpen: Boolean = false,
)

/** `EventSummary & { description, tags, capacity, ... }`. */
@Serializable
data class PublicEventDto(
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
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val capacity: Int? = null,
    val showRegistrationCount: Boolean = false,
    val confirmedCount: Int? = null,
    val privacyType: String? = null,
    val registrationOpen: Boolean = false,
    /** Capacity reached: registering joins the waitlist (unless approval is required). */
    val registrationFull: Boolean = false,
    val registrationClosedReason: String? = null,
    /** Always null today (Yuyu stores no separate joining URL); kept for forward compatibility. */
    val onlineUrl: String? = null,
)

@Serializable
data class RegistrationInfoDto(
    val requiresApproval: Boolean = false,
    val fields: List<RegistrationFieldDto> = emptyList(),
    val myRsvp: MyRsvpDto? = null,
)

@Serializable
data class MyRsvpDto(
    val rsvpId: String,
    val status: String,
    val ticketToken: String? = null,
) {
    override fun toString(): String = "MyRsvpDto(rsvpId=$rsvpId, status=$status)"
}

/** Mirrors the web registration-field settings model; the server stays the authority. */
@Serializable
data class RegistrationFieldDto(
    val id: String,
    val key: String,
    val label: String,
    /** TEXT|TEXTAREA|EMAIL|PHONE|SELECT|MULTI_SELECT|RADIO|CHECKBOX|NUMBER|DATE, see [FieldType]. */
    val type: String,
    val required: Boolean = false,
    val helpText: String? = null,
    val placeholder: String? = null,
    val options: List<String> = emptyList(),
    val settings: FieldSettingsDto = FieldSettingsDto(),
    val showIf: ShowIfDto? = null,
)

object FieldType {
    const val TEXT = "TEXT"
    const val TEXTAREA = "TEXTAREA"
    const val EMAIL = "EMAIL"
    const val PHONE = "PHONE"
    const val SELECT = "SELECT"
    const val MULTI_SELECT = "MULTI_SELECT"
    const val RADIO = "RADIO"
    const val CHECKBOX = "CHECKBOX"
    const val NUMBER = "NUMBER"
    const val DATE = "DATE"
}

@Serializable
data class FieldSettingsDto(
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
    val minDate: String? = null,
    val maxDate: String? = null,
    val maxSelections: Int? = null,
    val allowOther: Boolean? = null,
)

/**
 * Show the field only when the earlier field [fieldKey] has one of [values]
 * (CHECKBOX answers are "Yes"/"No"). A UI hint: the server drops hidden answers.
 */
@Serializable
data class ShowIfDto(val fieldKey: String, val values: List<String> = emptyList())

@Serializable
data class ScheduleTrackDto(
    val trackName: String? = null,
    val sessions: List<PublicSessionDto> = emptyList(),
)

@Serializable
data class PublicSessionDto(
    val id: String,
    val title: String,
    val startDateTime: String,
    val endDateTime: String? = null,
    /** Minutes running late; [startDateTime]/[endDateTime] already include it. */
    val delayMinutes: Int = 0,
    /** Always null today. */
    val room: String? = null,
    val speakers: List<String> = emptyList(),
)

@Serializable
data class SpeakerDto(
    val name: String,
    val title: String? = null,
    val photoUrl: String? = null,
)

/**
 * `POST /rsvp` (same body as `POST /api/rsvp`). Provide [eventSlug] or [eventInstanceId].
 * [answers] is keyed by registration field key; values are strings, string
 * arrays (multi-select) or numbers, as the web form sends them.
 */
@Serializable
data class RsvpRequest(
    val orgSlug: String,
    val eventSlug: String? = null,
    val eventInstanceId: String? = null,
    /** Required for guests; optional for signed-in users who already have a name. */
    val name: String? = null,
    /** Guests only. */
    val guestEmail: String? = null,
    val answers: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class RsvpResultDto(
    val rsvpId: String,
    /** CONFIRMED | WAITLISTED | PENDING_APPROVAL. */
    val status: String,
    val ticketToken: String? = null,
) {
    override fun toString(): String = "RsvpResultDto(rsvpId=$rsvpId, status=$status)"
}

@Serializable
data class FeedbackFormDto(
    val title: String,
    /** Always null today. */
    val intro: String? = null,
    val thankYouMessage: String? = null,
    val open: Boolean = false,
    val certificatesEnabled: Boolean = false,
    val fields: List<RegistrationFieldDto> = emptyList(),
)

/**
 * `POST /api/feedback` (existing web endpoint). [email] is required only when
 * certificates are enabled. [answers] keyed by field key.
 */
@Serializable
data class FeedbackSubmitRequest(
    val orgSlug: String,
    val eventSlug: String,
    val email: String? = null,
    val answers: Map<String, JsonElement> = emptyMap(),
)

/**
 * `POST /api/feedback` success data. The certificate image is at
 * `GET /api/feedback/certificate/{certificateToken}`.
 */
@Serializable
data class FeedbackSubmitResultDto(val certificateToken: String? = null) {
    override fun toString(): String = "FeedbackSubmitResultDto(certificate=${certificateToken != null})"
}

/** `GET /api/ticket/:token/status` (bare JSON, no envelope). Also the SSE `state` payload. */
@Serializable
data class TicketStatusDto(
    val checkedIn: Boolean = false,
    val checkedInAt: String? = null,
)
