package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable

/**
 * `GET …/schedule`: every organiser of the event can read it. [generatedAt] and
 * the session fields after `delayMinutes` are absent on servers older than the
 * speakers change.
 */
@Serializable
data class OrgScheduleDto(
    val tracks: List<OrgTrackDto> = emptyList(),
    val generatedAt: String? = null,
)

@Serializable
data class OrgTrackDto(
    val id: String? = null,
    val name: String,
    val sessions: List<OrgSessionDto> = emptyList(),
)

@Serializable
data class OrgSessionDto(
    val id: String,
    val title: String,
    val startDateTime: String,
    val endDateTime: String? = null,
    /** This session's own stored delay; it shifts only later sessions in the track. */
    val delayMinutes: Int = 0,
    val type: String? = null,
    /** DRAFT, PUBLISHED or HIDDEN. */
    val visibility: String? = null,
    /** Planned times shifted by earlier delays in the track, computed by the server. */
    val effectiveStartDateTime: String? = null,
    val effectiveEndDateTime: String? = null,
    val speakers: List<ScheduleSpeakerDto> = emptyList(),
)

@Serializable
data class ScheduleSpeakerDto(
    val id: String,
    val name: String,
    val headline: String? = null,
    val organisation: String? = null,
    val photoUrl: String? = null,
)

/** 0..1440 minutes. */
@Serializable
data class SessionDelayRequest(val delayMinutes: Int) {
    init {
        require(delayMinutes in 0..1440) { "delayMinutes must be within 0..1440" }
    }
}

@Serializable
data class OpenStateRequest(val open: Boolean)

@Serializable
data class FeedbackStateDto(val open: Boolean)

/**
 * `POST …/registration/state`. Reopening is allowed only while
 * [registrationManuallyClosed] is true (otherwise 409 CONFLICT) and restores the
 * stored cutoff, which may itself have passed ([registrationOpen] false + reason).
 */
@Serializable
data class RegistrationStateDto(
    val registrationOpen: Boolean,
    val registrationClosesAt: String? = null,
    val registrationClosedReason: String? = null,
    val registrationManuallyClosed: Boolean = false,
)
