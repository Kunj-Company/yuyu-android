package com.twilighty.yuyu.feature.checkin.scanner

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.map
import com.twilighty.yuyu.core.data.repository.CheckInRepository
import com.twilighty.yuyu.feature.checkin.domain.AdmitResult
import com.twilighty.yuyu.feature.checkin.domain.GuestPreview
import com.twilighty.yuyu.feature.checkin.domain.LookupGuest
import com.twilighty.yuyu.feature.checkin.domain.toLookup
import com.twilighty.yuyu.feature.checkin.domain.toPreview
import com.twilighty.yuyu.feature.checkin.flow.CheckInBackend
import com.twilighty.yuyu.feature.checkin.flow.OfflineCheckInBackend
import com.twilighty.yuyu.feature.checkin.flow.OfflineMatch
import java.time.Instant

/** The organiser mobile API (`…/check-in/…`, bearer auth, event CHECK_IN permission). */
internal class OrganiserCheckInBackend(
    private val repository: CheckInRepository,
    private val orgSlug: String,
    private val eventId: String,
) : CheckInBackend {
    override suspend fun previewCode(rawInput: String) =
        repository.preview(orgSlug, eventId, rawInput).map { it.toPreview() }

    override suspend fun previewRsvp(rsvpId: String) =
        repository.previewByRsvp(orgSlug, eventId, rsvpId).map { it.toPreview() }

    override suspend fun checkIn(rsvpId: String, rawInput: String?, force: Boolean): AppResult<AdmitResult> {
        val result = if (rawInput != null) {
            repository.checkInByCode(orgSlug, eventId, rawInput, force)
        } else {
            repository.checkInByRsvp(orgSlug, eventId, rsvpId, force)
        }
        return result.map { AdmitResult(it.toPreview(), it.checkedInAt, it.alreadyCheckedIn) }
    }

    override suspend fun undo(rsvpId: String) = repository.undo(orgSlug, eventId, rsvpId)

    override suspend fun lookup(query: String): AppResult<List<LookupGuest>> =
        repository.lookup(orgSlug, eventId, query).map { rows -> rows.map { it.toLookup() } }
}

/**
 * The offline roster (tokens stored only as SHA-256) and queue in Room. The
 * gate is applied locally with the server's rules; the server re-checks on sync.
 */
internal class OrganiserOfflineBackend(
    private val repository: CheckInRepository,
    private val orgSlug: String,
    private val eventId: String,
    private val now: () -> Instant = Instant::now,
) : OfflineCheckInBackend {
    override suspend fun matchCode(rawInput: String): OfflineMatch {
        val guest = repository.findOfflineByCode(eventId, rawInput)
        return when {
            guest != null -> OfflineMatch.Found(guest.toPreview())
            repository.rosterInfo(eventId).count == 0 -> OfflineMatch.NoRoster
            else -> OfflineMatch.NotInRoster
        }
    }

    override suspend fun findRsvp(rsvpId: String, force: Boolean): GuestPreview? =
        repository.findOfflineByRsvp(eventId, rsvpId)?.toPreview(force)

    override suspend fun record(guest: GuestPreview, force: Boolean): AdmitResult {
        repository.recordOfflineCheckIn(orgSlug, eventId, guest.rsvpId, force)
        val checkedInAt = repository.findOfflineByRsvp(eventId, guest.rsvpId)?.checkedInAt ?: now().toString()
        return AdmitResult(guest.copy(alreadyCheckedIn = false, checkedInAt = checkedInAt), checkedInAt, alreadyCheckedIn = false, offline = true)
    }

    override suspend fun undo(rsvpId: String): Boolean = repository.undoOfflineCheckIn(eventId, rsvpId)

    override suspend fun lookup(query: String): List<LookupGuest> =
        repository.searchOffline(eventId, query).map { it.toLookup() }
}
