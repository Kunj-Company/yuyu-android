package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.map
import com.twilighty.yuyu.core.common.result.onSuccess
import com.twilighty.yuyu.core.data.crypto.sha256Hex
import com.twilighty.yuyu.core.data.sync.CheckInSyncScheduler
import com.twilighty.yuyu.core.data.util.CheckInPayload
import com.twilighty.yuyu.core.database.dao.PendingCheckInDao
import com.twilighty.yuyu.core.database.dao.RosterDao
import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import com.twilighty.yuyu.core.database.entity.RosterRowEntity
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.error.apiCallUnit
import com.twilighty.yuyu.core.network.model.CheckInPreviewDto
import com.twilighty.yuyu.core.network.model.CheckInPreviewRequest
import com.twilighty.yuyu.core.network.model.CheckInRequest
import com.twilighty.yuyu.core.network.model.CheckInResultDto
import com.twilighty.yuyu.core.network.model.CheckInStatsDto
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.core.network.model.LabelValueDto
import com.twilighty.yuyu.core.network.model.OfflineCheckInDto
import com.twilighty.yuyu.core.network.model.RsvpIdRequest
import com.twilighty.yuyu.core.network.model.SyncCheckInsRequest
import com.twilighty.yuyu.core.network.model.SyncCheckInsResultDto
import com.twilighty.yuyu.core.network.model.SyncFailureDto
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

/** An offline roster row, details decoded. */
data class OfflineGuest(
    val rsvpId: String,
    val displayName: String,
    val email: String?,
    val status: String,
    val checkedInAt: String?,
    val isGuest: Boolean,
    val checkInDetails: List<LabelValueDto>,
    val registrationDetails: List<LabelValueDto>,
)

data class RosterInfo(val generatedAt: String?, val count: Int)

/**
 * Organiser check-in (requires the event CHECK_IN permission server-side),
 * plus the offline roster and queue. Offline decisions are hints only: the
 * server re-applies every rule when the queue syncs.
 */
interface CheckInRepository {
    suspend fun preview(orgSlug: String, eventId: String, rawInput: String): AppResult<CheckInPreviewDto>
    suspend fun previewByRsvp(orgSlug: String, eventId: String, rsvpId: String): AppResult<CheckInPreviewDto>

    /** Fails with [AppError.NeedsForce] when an override is possible; retry with `force = true`. */
    suspend fun checkInByCode(orgSlug: String, eventId: String, rawInput: String, force: Boolean = false): AppResult<CheckInResultDto>
    suspend fun checkInByRsvp(orgSlug: String, eventId: String, rsvpId: String, force: Boolean = false): AppResult<CheckInResultDto>
    suspend fun undo(orgSlug: String, eventId: String, rsvpId: String): AppResult<Unit>

    /** [query] must be at least 2 characters. */
    suspend fun lookup(orgSlug: String, eventId: String, query: String): AppResult<List<GuestDto>>
    suspend fun stats(orgSlug: String, eventId: String): AppResult<CheckInStatsDto>

    /** Downloads `…/check-in/roster` into Room (replacing the previous copy). */
    suspend fun downloadRoster(orgSlug: String, eventId: String): AppResult<RosterInfo>
    suspend fun rosterInfo(eventId: String): RosterInfo
    fun observeRoster(eventId: String): Flow<List<OfflineGuest>>
    suspend fun findOfflineByCode(eventId: String, rawInput: String): OfflineGuest?
    suspend fun findOfflineByRsvp(eventId: String, rsvpId: String): OfflineGuest?
    suspend fun searchOffline(eventId: String, query: String): List<OfflineGuest>

    /** Records an offline check-in, marks the roster row and schedules a background sync. */
    suspend fun recordOfflineCheckIn(orgSlug: String, eventId: String, rsvpId: String, force: Boolean = false)

    /**
     * Undo for an offline check-in that hasn't synced yet: drops it from the
     * queue and clears the roster mark. Returns false when nothing was queued
     * for [rsvpId] (it already synced, so undo it online instead).
     */
    suspend fun undoOfflineCheckIn(eventId: String, rsvpId: String): Boolean
    fun observePending(eventId: String): Flow<List<PendingCheckInEntity>>
    fun observePendingCount(): Flow<Int>

    /** Pushes queued check-ins for one event now. */
    suspend fun syncPending(orgSlug: String, eventId: String): AppResult<SyncCheckInsResultDto>

    /** Pushes every queued event (used by the background worker). */
    suspend fun syncAllPending(): AppResult<Unit>
}

@Singleton
class CheckInRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
    private val rosterDao: RosterDao,
    private val pendingDao: PendingCheckInDao,
    private val syncScheduler: CheckInSyncScheduler,
) : CheckInRepository {
    private val detailsSerializer = ListSerializer(LabelValueDto.serializer())

    override suspend fun preview(orgSlug: String, eventId: String, rawInput: String) =
        apiCall { api.checkInPreview(orgSlug, eventId, CheckInPreviewRequest(rawInput = rawInput.trim())) }

    override suspend fun previewByRsvp(orgSlug: String, eventId: String, rsvpId: String) =
        apiCall { api.checkInPreview(orgSlug, eventId, CheckInPreviewRequest(rsvpId = rsvpId)) }

    override suspend fun checkInByCode(orgSlug: String, eventId: String, rawInput: String, force: Boolean) =
        apiCall { api.checkIn(orgSlug, eventId, CheckInRequest(rawInput = rawInput.trim(), force = force)) }
            .onSuccess { result -> markLocally(eventId, result.rsvpId, result.checkedInAt) }

    override suspend fun checkInByRsvp(orgSlug: String, eventId: String, rsvpId: String, force: Boolean) =
        apiCall { api.checkIn(orgSlug, eventId, CheckInRequest(rsvpId = rsvpId, force = force)) }
            .onSuccess { result -> markLocally(eventId, result.rsvpId, result.checkedInAt) }

    override suspend fun undo(orgSlug: String, eventId: String, rsvpId: String) =
        apiCallUnit { api.undoCheckIn(orgSlug, eventId, RsvpIdRequest(rsvpId)) }
            .onSuccess { markLocally(eventId, rsvpId, null) }

    override suspend fun lookup(orgSlug: String, eventId: String, query: String): AppResult<List<GuestDto>> {
        val q = query.trim()
        if (q.length < 2) return AppResult.Failure(AppError.InvalidRequest("Type at least 2 letters to search."))
        return apiCall { api.checkInLookup(orgSlug, eventId, q) }.map { it.rows }
    }

    override suspend fun stats(orgSlug: String, eventId: String) = apiCall { api.checkInStats(orgSlug, eventId) }

    /** The offline copy mirrors server check-ins; a local database error must not fail the check-in itself. */
    private suspend fun markLocally(eventId: String, rsvpId: String, checkedInAt: String?) {
        runCatching { rosterDao.markCheckedIn(eventId, rsvpId, checkedInAt) }
    }

    override suspend fun downloadRoster(orgSlug: String, eventId: String): AppResult<RosterInfo> = try {
        saveRoster(orgSlug, eventId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unknown("Couldn't save the guest list on this phone. Check the free space and try again."))
    }

    private suspend fun saveRoster(orgSlug: String, eventId: String): AppResult<RosterInfo> =
        apiCall { api.checkInRoster(orgSlug, eventId) }.map { roster ->
            val rows = roster.rows.map { row ->
                RosterRowEntity(
                    eventId = eventId,
                    rsvpId = row.rsvpId,
                    orgSlug = orgSlug,
                    displayName = row.displayName,
                    email = row.email,
                    status = row.status,
                    checkedInAt = row.checkedInAt,
                    registeredAt = row.registeredAt,
                    isGuest = row.isGuest,
                    ticketTokenSha256 = row.ticketToken?.let(::sha256Hex),
                    checkInDetailsJson = NetworkJson.encodeToString(detailsSerializer, row.checkInDetails),
                    registrationDetailsJson = NetworkJson.encodeToString(detailsSerializer, row.registrationDetails),
                    generatedAt = roster.generatedAt,
                )
            }
            rosterDao.replaceRoster(eventId, rows)
            // Re-apply queued offline check-ins on top of the fresh copy.
            pendingDao.forEvent(eventId).forEach { rosterDao.markCheckedIn(eventId, it.rsvpId, it.checkedInAt) }
            RosterInfo(roster.generatedAt, rows.size)
        }

    override suspend fun rosterInfo(eventId: String) =
        runCatching { RosterInfo(rosterDao.generatedAt(eventId), rosterDao.count(eventId)) }.getOrElse { RosterInfo(null, 0) }

    override fun observeRoster(eventId: String): Flow<List<OfflineGuest>> =
        rosterDao.observeRoster(eventId).map { rows -> rows.map { it.toOffline() } }

    override suspend fun findOfflineByCode(eventId: String, rawInput: String): OfflineGuest? {
        val token = CheckInPayload.extractToken(rawInput).ifEmpty { return null }
        return rosterDao.findByTokenHash(eventId, sha256Hex(token))?.toOffline()
    }

    override suspend fun findOfflineByRsvp(eventId: String, rsvpId: String): OfflineGuest? =
        rosterDao.find(eventId, rsvpId)?.toOffline()

    override suspend fun searchOffline(eventId: String, query: String): List<OfflineGuest> =
        rosterDao.search(eventId, query.trim()).map { it.toOffline() }

    override suspend fun recordOfflineCheckIn(orgSlug: String, eventId: String, rsvpId: String, force: Boolean) {
        val now = Instant.now()
        pendingDao.enqueue(
            PendingCheckInEntity(
                orgSlug = orgSlug,
                eventId = eventId,
                rsvpId = rsvpId,
                checkedInAt = now.toString(),
                force = force,
                createdAt = now.toEpochMilli(),
                clientMutationId = UUID.randomUUID().toString(),
            ),
        )
        rosterDao.markCheckedIn(eventId, rsvpId, now.toString())
        syncScheduler.schedule()
    }

    override suspend fun undoOfflineCheckIn(eventId: String, rsvpId: String): Boolean {
        if (pendingDao.forEvent(eventId).none { it.rsvpId == rsvpId }) return false
        pendingDao.remove(eventId, listOf(rsvpId))
        rosterDao.markCheckedIn(eventId, rsvpId, null)
        return true
    }

    override fun observePending(eventId: String) = pendingDao.observeForEvent(eventId)

    override fun observePendingCount() = pendingDao.observeTotalCount()

    /**
     * The server accepts at most [MAX_SYNC_BATCH] check-ins per request, so the
     * queue goes up in order, one batch at a time. Each batch's results are
     * applied before the next is sent; on the first error the remaining batches
     * stay queued (already-applied ones are not resent) and that error is
     * returned so the caller can retry later. Every batch carries its rows'
     * idempotency keys, so a retried batch never double-counts.
     */
    override suspend fun syncPending(orgSlug: String, eventId: String): AppResult<SyncCheckInsResultDto> {
        val pending = pendingDao.forEvent(eventId)
        if (pending.isEmpty()) return AppResult.Success(SyncCheckInsResultDto())
        val synced = mutableListOf<String>()
        val alreadyCheckedIn = mutableListOf<String>()
        val failed = mutableListOf<SyncFailureDto>()
        for (batch in pending.chunked(MAX_SYNC_BATCH)) {
            val request = SyncCheckInsRequest(batch.map { OfflineCheckInDto(it.rsvpId, it.checkedInAt, it.force, it.clientMutationId) })
            when (val result = apiCall { api.syncCheckIns(orgSlug, eventId, request) }) {
                is AppResult.Failure -> return result
                is AppResult.Success -> {
                    val data = result.data
                    pendingDao.remove(eventId, data.syncedIds + data.alreadyCheckedInIds)
                    data.failed.forEach { pendingDao.markFailed(eventId, it.rsvpId, it.error) }
                    synced += data.syncedIds
                    alreadyCheckedIn += data.alreadyCheckedInIds
                    failed += data.failed
                }
            }
        }
        return AppResult.Success(SyncCheckInsResultDto(synced, alreadyCheckedIn, failed))
    }

    override suspend fun syncAllPending(): AppResult<Unit> {
        val byEvent = pendingDao.all().groupBy { it.orgSlug to it.eventId }
        var lastFailure: AppResult.Failure? = null
        byEvent.keys.forEach { (orgSlug, eventId) ->
            val result = syncPending(orgSlug, eventId)
            if (result is AppResult.Failure) lastFailure = result
        }
        return lastFailure ?: AppResult.Success(Unit)
    }

    private fun RosterRowEntity.toOffline() = OfflineGuest(
        rsvpId = rsvpId,
        displayName = displayName,
        email = email,
        status = status,
        checkedInAt = checkedInAt,
        isGuest = isGuest,
        checkInDetails = runCatching { NetworkJson.decodeFromString(detailsSerializer, checkInDetailsJson) }.getOrDefault(emptyList()),
        registrationDetails = runCatching { NetworkJson.decodeFromString(detailsSerializer, registrationDetailsJson) }.getOrDefault(emptyList()),
    )

    companion object {
        /** Server cap on `checkIns` per `POST …/check-in/sync` (`.max(500)`). */
        const val MAX_SYNC_BATCH = 500
    }
}
