package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class OrgScheduleDto(val tracks: List<OrgTrackDto> = emptyList())

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
    val delayMinutes: Int = 0,
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
