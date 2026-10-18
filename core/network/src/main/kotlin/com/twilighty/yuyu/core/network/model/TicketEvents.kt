package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable

/** Events from `GET /api/ticket/:token/events` (SSE). */
sealed interface TicketEvent {
    /** `event: state` — current check-in state (sent first, and on reconnect). */
    data class State(val checkedIn: Boolean, val checkedInAt: String?) : TicketEvent

    /** `event: checked-in` — the attendee was just admitted; the stream then closes. */
    data class CheckedIn(val checkedInAt: String?) : TicketEvent
}

@Serializable
internal data class TicketCheckedInPayload(val checkedInAt: String? = null)
