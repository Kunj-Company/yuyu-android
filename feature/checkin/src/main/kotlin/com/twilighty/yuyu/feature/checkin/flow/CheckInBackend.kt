package com.twilighty.yuyu.feature.checkin.flow

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.feature.checkin.domain.AdmitResult
import com.twilighty.yuyu.feature.checkin.domain.GuestPreview
import com.twilighty.yuyu.feature.checkin.domain.LookupGuest

/**
 * The online side of a check-in desk: the organiser mobile API or the
 * PIN-unlocked door station. Raw QR/typed input is passed straight through to
 * the server, which parses it.
 */
interface CheckInBackend {
    suspend fun previewCode(rawInput: String): AppResult<GuestPreview>
    suspend fun previewRsvp(rsvpId: String): AppResult<GuestPreview>

    /**
     * [rawInput] is set when the guest was found by scanning or typing a code
     * (the organiser API records that as a scan); otherwise check in by id.
     * Fails with `AppError.NeedsForce` when an override is possible.
     */
    suspend fun checkIn(rsvpId: String, rawInput: String?, force: Boolean): AppResult<AdmitResult>
    suspend fun undo(rsvpId: String): AppResult<Unit>

    /** [query] has at least 2 characters. */
    suspend fun lookup(query: String): AppResult<List<LookupGuest>>
}

/** Result of matching a code against the offline roster. */
sealed interface OfflineMatch {
    data class Found(val guest: GuestPreview) : OfflineMatch
    data object NotInRoster : OfflineMatch
    data object NoRoster : OfflineMatch
}

/** The offline roster and queue (organiser scanner only; the station is online-only). */
interface OfflineCheckInBackend {
    suspend fun matchCode(rawInput: String): OfflineMatch
    suspend fun findRsvp(rsvpId: String, force: Boolean): GuestPreview?
    suspend fun record(guest: GuestPreview, force: Boolean): AdmitResult

    /** True when a queued (unsynced) check-in was removed. */
    suspend fun undo(rsvpId: String): Boolean
    suspend fun lookup(query: String): List<LookupGuest>
}
