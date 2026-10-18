package com.twilighty.yuyu.feature.checkin

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.CheckInRepository
import com.twilighty.yuyu.core.data.repository.OfflineGuest
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.core.data.repository.RosterInfo
import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import com.twilighty.yuyu.core.network.model.CheckInGateDto
import com.twilighty.yuyu.core.network.model.CheckInPreviewDto
import com.twilighty.yuyu.core.network.model.CheckInResultDto
import com.twilighty.yuyu.core.network.model.CheckInStatsDto
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.core.network.model.OrgEventDetailResponseDto
import com.twilighty.yuyu.core.network.model.OrgEventsDto
import com.twilighty.yuyu.core.network.model.SyncCheckInsResultDto
import com.twilighty.yuyu.feature.checkin.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal fun previewDto(
    status: String = "CONFIRMED",
    gate: CheckInGateDto = CheckInGateDto(ok = true),
    alreadyCheckedIn: Boolean = false,
    checkedInAt: String? = null,
    rsvpId: String = "r1",
    name: String = "Ada Lovelace",
) = CheckInPreviewDto(rsvpId, name, "ada@example.org", status, alreadyCheckedIn, checkedInAt, gate = gate)

internal fun resultDto(rsvpId: String = "r1", already: Boolean = false, at: String = "2026-10-18T10:00:00Z") =
    CheckInResultDto(rsvpId, "Ada Lovelace", "ada@example.org", "CONFIRMED", already, at)

internal fun offlineGuest(rsvpId: String = "r1", status: String = "CONFIRMED", checkedInAt: String? = null, name: String = "Ada Lovelace") =
    OfflineGuest(rsvpId, name, "ada@example.org", status, checkedInAt, false, emptyList(), emptyList())

/** In-memory [CheckInRepository] recording every call. */
internal class FakeCheckInRepository : CheckInRepository {
    var previewResult: AppResult<CheckInPreviewDto> = AppResult.Success(previewDto())
    var previewByRsvpResult: AppResult<CheckInPreviewDto> = AppResult.Success(previewDto())
    var checkInResults = ArrayDeque<AppResult<CheckInResultDto>>()
    var undoResult: AppResult<Unit> = AppResult.Success(Unit)
    var statsResult: AppResult<CheckInStatsDto> = AppResult.Success(CheckInStatsDto(confirmed = 10, checkedIn = 3))
    var syncResult: AppResult<SyncCheckInsResultDto> = AppResult.Success(SyncCheckInsResultDto())

    /** Offline roster: rsvpId → guest, and raw code → rsvpId (stands in for the hash lookup). */
    val roster = MutableStateFlow<Map<String, OfflineGuest>>(emptyMap())
    val codes = mutableMapOf<String, String>()
    val pending = MutableStateFlow<List<PendingCheckInEntity>>(emptyList())

    val previews = mutableListOf<String>()
    val checkIns = mutableListOf<Triple<String, String?, Boolean>>()
    val undos = mutableListOf<String>()
    val recorded = mutableListOf<Pair<String, Boolean>>()
    val offlineUndos = mutableListOf<String>()
    var syncCalls = 0
    var statsCalls = 0

    override suspend fun preview(orgSlug: String, eventId: String, rawInput: String): AppResult<CheckInPreviewDto> {
        previews += rawInput
        return previewResult
    }

    override suspend fun previewByRsvp(orgSlug: String, eventId: String, rsvpId: String) = previewByRsvpResult

    override suspend fun checkInByCode(orgSlug: String, eventId: String, rawInput: String, force: Boolean): AppResult<CheckInResultDto> {
        checkIns += Triple("code", rawInput, force)
        return checkInResults.removeFirstOrNull() ?: AppResult.Success(resultDto())
    }

    override suspend fun checkInByRsvp(orgSlug: String, eventId: String, rsvpId: String, force: Boolean): AppResult<CheckInResultDto> {
        checkIns += Triple("rsvp", rsvpId, force)
        return checkInResults.removeFirstOrNull() ?: AppResult.Success(resultDto(rsvpId))
    }

    override suspend fun undo(orgSlug: String, eventId: String, rsvpId: String): AppResult<Unit> {
        undos += rsvpId
        return undoResult
    }

    override suspend fun lookup(orgSlug: String, eventId: String, query: String): AppResult<List<GuestDto>> =
        AppResult.Success(emptyList())

    override suspend fun stats(orgSlug: String, eventId: String): AppResult<CheckInStatsDto> {
        statsCalls++
        return statsResult
    }

    override suspend fun downloadRoster(orgSlug: String, eventId: String): AppResult<RosterInfo> =
        AppResult.Success(RosterInfo("2026-10-18T09:00:00Z", roster.value.size))

    override suspend fun rosterInfo(eventId: String) = RosterInfo(if (roster.value.isEmpty()) null else "2026-10-18T09:00:00Z", roster.value.size)

    override fun observeRoster(eventId: String): Flow<List<OfflineGuest>> = roster.map { it.values.toList() }

    override suspend fun findOfflineByCode(eventId: String, rawInput: String): OfflineGuest? =
        codes[rawInput]?.let { roster.value[it] }

    override suspend fun findOfflineByRsvp(eventId: String, rsvpId: String): OfflineGuest? = roster.value[rsvpId]

    override suspend fun searchOffline(eventId: String, query: String): List<OfflineGuest> =
        roster.value.values.filter { it.displayName.contains(query, ignoreCase = true) }

    override suspend fun recordOfflineCheckIn(orgSlug: String, eventId: String, rsvpId: String, force: Boolean) {
        recorded += rsvpId to force
        val at = "2026-10-18T10:05:00Z"
        pending.value = pending.value + PendingCheckInEntity(
            orgSlug = orgSlug, eventId = eventId, rsvpId = rsvpId, checkedInAt = at, force = force,
            createdAt = pending.value.size.toLong(), clientMutationId = "cm-$rsvpId-000",
        )
        roster.value[rsvpId]?.let { roster.value = roster.value + (rsvpId to it.copy(checkedInAt = at)) }
    }

    override suspend fun undoOfflineCheckIn(eventId: String, rsvpId: String): Boolean {
        offlineUndos += rsvpId
        if (pending.value.none { it.rsvpId == rsvpId }) return false
        pending.value = pending.value.filterNot { it.rsvpId == rsvpId }
        roster.value[rsvpId]?.let { roster.value = roster.value + (rsvpId to it.copy(checkedInAt = null)) }
        return true
    }

    override fun observePending(eventId: String): Flow<List<PendingCheckInEntity>> = pending
    override fun observePendingCount(): Flow<Int> = pending.map { it.size }

    override suspend fun syncPending(orgSlug: String, eventId: String): AppResult<SyncCheckInsResultDto> {
        syncCalls++
        val result = syncResult
        if (result is AppResult.Success) {
            val done = (result.data.syncedIds + result.data.alreadyCheckedInIds).toSet()
            pending.value = pending.value.filterNot { it.rsvpId in done }
        }
        return result
    }

    override suspend fun syncAllPending(): AppResult<Unit> = AppResult.Success(Unit)
}

internal class FakeEvents(var result: AppResult<OrgEventDetailResponseDto> = AppResult.Failure(AppError.Network())) :
    OrganiserEventsRepository {
    override suspend fun events(orgSlug: String, filter: String): AppResult<OrgEventsDto> = AppResult.Failure(AppError.Network())
    override suspend fun event(orgSlug: String, eventId: String) = result
}

internal class FakeConnectivity(initial: Boolean = true) : ConnectivityObserver {
    val online = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> = online
}
