package com.twilighty.yuyu.feature.organiser

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.CachedRegistrations
import com.twilighty.yuyu.core.data.repository.DayOfRepository
import com.twilighty.yuyu.core.data.repository.GuestQuery
import com.twilighty.yuyu.core.data.repository.GuestsRepository
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.core.data.repository.SavedSchedule
import com.twilighty.yuyu.core.data.repository.ScheduleRepository
import com.twilighty.yuyu.core.network.model.EventCountsDto
import com.twilighty.yuyu.core.network.model.EventPermissionsDto
import com.twilighty.yuyu.core.network.model.GuestDetailDto
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.core.network.model.GuestPageDto
import com.twilighty.yuyu.core.network.model.MeDto
import com.twilighty.yuyu.core.network.model.OrgEventDetailDto
import com.twilighty.yuyu.core.network.model.OrgEventDetailResponseDto
import com.twilighty.yuyu.core.network.model.OrgEventDto
import com.twilighty.yuyu.core.network.model.OrgEventsDto
import com.twilighty.yuyu.core.network.model.OrgRefDto
import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.core.network.model.RegistrationStateDto
import com.twilighty.yuyu.core.network.model.RegistrationsDto
import com.twilighty.yuyu.core.network.model.UserDto
import com.twilighty.yuyu.feature.organiser.common.OrganiserPreferences
import com.twilighty.yuyu.feature.organiser.common.WebLinks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

val AllPermissions = EventPermissionsDto(
    checkIn = true,
    manageRegistrations = true,
    sendEmails = true,
    schedule = true,
    manageFeedback = true,
    manageRegistrationWindow = true,
)
val CheckInOnly = EventPermissionsDto(checkIn = true)

fun guest(id: String, status: String = "CONFIRMED", name: String = "Guest $id", checkedInAt: String? = null) =
    GuestDto(rsvpId = id, displayName = name, email = "$id@example.test", status = status, checkedInAt = checkedInAt, registeredAt = "2030-04-10T10:00:00Z")

fun orgEvent(id: String, org: String = "civic-tech", counts: EventCountsDto? = EventCountsDto(10, 2, 1, 3, 40)) = OrgEventDto(
    id = id,
    slug = "event-$id",
    title = "Event $id",
    startDateTime = "2030-05-10T17:00:00Z",
    timezone = "Europe/London",
    status = "PUBLISHED",
    organisation = OrgRefDto(org, org),
    counts = counts,
    myPermissions = AllPermissions,
)

fun eventDetail(
    permissions: EventPermissionsDto = AllPermissions,
    registrationOpen: Boolean = true,
    manuallyClosed: Boolean = false,
    feedbackOpen: Boolean = false,
) = OrgEventDetailResponseDto(
    event = OrgEventDetailDto(
        id = "e1",
        slug = "spring",
        title = "Spring Hack Night",
        startDateTime = "2030-05-10T17:00:00Z",
        endDateTime = "2030-05-10T21:00:00Z",
        timezone = "Europe/London",
        status = "PUBLISHED",
        organisation = OrgRefDto("civic-tech", "Civic Tech"),
        registrationOpen = registrationOpen,
        registrationClosedReason = if (registrationOpen) null else "Registration has closed.",
        registrationManuallyClosed = manuallyClosed,
        feedbackOpen = feedbackOpen,
        eventUrl = "https://yuyu.example/civic-tech/spring",
        shortUrl = "https://yuyu.example/Ab3",
    ),
    counts = EventCountsDto(42, 5, 2, 12, 80),
    myPermissions = permissions,
)

class FakeMeRepository(initial: MeDto) : MeRepository {
    var next: MeDto = initial
    private val state = MutableStateFlow<MeDto?>(null)
    override val me: StateFlow<MeDto?> = state
    override suspend fun refreshMe(): AppResult<MeDto> {
        state.value = next
        return AppResult.Success(next)
    }
    override suspend fun updateName(name: String): AppResult<UserDto> = error("unused")
    override suspend fun registrations(): AppResult<RegistrationsDto> = error("unused")
    override fun cachedRegistrations(): Flow<CachedRegistrations> = emptyFlow()
    override suspend fun cachedRegistration(rsvpId: String): RegistrationDto? = null
    override suspend fun cancelRegistration(rsvpId: String): AppResult<Unit> = error("unused")
}

class FakeEventsRepository : OrganiserEventsRepository {
    val eventCalls = mutableListOf<Pair<String, String>>()
    var eventsResult: (String, String) -> AppResult<OrgEventsDto> = { org, _ -> AppResult.Success(OrgEventsDto(listOf(orgEvent("${org}-1", org)))) }
    var detail: AppResult<OrgEventDetailResponseDto> = AppResult.Success(eventDetail())
    var detailCalls = 0

    override suspend fun events(orgSlug: String, filter: String): AppResult<OrgEventsDto> {
        eventCalls += orgSlug to filter
        return eventsResult(orgSlug, filter)
    }

    override suspend fun event(orgSlug: String, eventId: String): AppResult<OrgEventDetailResponseDto> {
        detailCalls++
        return detail
    }
}

class FakePreferences(var slug: String? = null) : OrganiserPreferences {
    val writes = mutableListOf<String>()
    override suspend fun lastOrgSlug(): String? = slug
    override suspend fun setLastOrgSlug(slug: String) {
        writes += slug
        this.slug = slug
    }
}

object FakeWebLinks : WebLinks {
    override fun url(path: String): String = "https://yuyu.example/$path"
}

class FakeGuestsRepository : GuestsRepository {
    val queries = mutableListOf<GuestQuery>()
    var pages: (GuestQuery) -> AppResult<GuestPageDto> = { AppResult.Success(GuestPageDto(listOf(guest("a"), guest("b", "PENDING_APPROVAL"), guest("c", "WAITLISTED")))) }

    /** When set, lifecycle calls wait for it to complete before answering. */
    var gate: CompletableDeferred<Unit>? = null
    var lifecycleResult: (String, String) -> AppResult<GuestDto> = { id, status -> AppResult.Success(guest(id, status, name = "Server $id")) }
    var deleteResult: AppResult<String> = AppResult.Success("undo-1")
    var restoreResult: AppResult<Unit> = AppResult.Success(Unit)
    var addResult: AppResult<GuestDto> = AppResult.Success(guest("new", name = "Ada"))
    val calls = mutableListOf<String>()

    override suspend fun guests(orgSlug: String, eventId: String, query: GuestQuery): AppResult<GuestPageDto> {
        queries += query
        return pages(query)
    }

    override suspend fun guest(orgSlug: String, eventId: String, rsvpId: String): AppResult<GuestDetailDto> =
        AppResult.Success(GuestDetailDto(rsvpId, "Guest $rsvpId", null, "CONFIRMED", null, "2030-04-10T10:00:00Z"))

    private suspend fun lifecycle(name: String, id: String, status: String): AppResult<GuestDto> {
        calls += "$name:$id"
        gate?.await()
        return lifecycleResult(id, status)
    }

    override suspend fun approve(orgSlug: String, eventId: String, rsvpId: String) = lifecycle("approve", rsvpId, "CONFIRMED")
    override suspend fun reject(orgSlug: String, eventId: String, rsvpId: String) = lifecycle("reject", rsvpId, "REJECTED")
    override suspend fun promote(orgSlug: String, eventId: String, rsvpId: String) = lifecycle("promote", rsvpId, "CONFIRMED")

    override suspend fun add(orgSlug: String, eventId: String, name: String, email: String): AppResult<GuestDto> {
        calls += "add:$name:$email"
        return addResult
    }

    override suspend fun delete(orgSlug: String, eventId: String, rsvpId: String): AppResult<String> {
        calls += "delete:$rsvpId"
        gate?.await()
        return deleteResult
    }

    override suspend fun restore(orgSlug: String, eventId: String, undoId: String): AppResult<Unit> {
        calls += "restore:$undoId"
        return restoreResult
    }
}

class FakeDayOfRepository : DayOfRepository {
    var schedule: AppResult<OrgScheduleDto> = AppResult.Success(OrgScheduleDto())
    val delayCalls = mutableListOf<Pair<String, Int>>()
    var delayResult: AppResult<Unit> = AppResult.Success(Unit)
    var feedbackResult: (Boolean) -> AppResult<Boolean> = { AppResult.Success(it) }
    val registrationCalls = mutableListOf<Boolean>()
    var registrationResult: (Boolean) -> AppResult<RegistrationStateDto> = { open ->
        AppResult.Success(RegistrationStateDto(registrationOpen = open, registrationManuallyClosed = !open))
    }

    override suspend fun schedule(orgSlug: String, eventId: String) = schedule
    override suspend fun setSessionDelay(orgSlug: String, eventId: String, sessionId: String, delayMinutes: Int): AppResult<Unit> {
        delayCalls += sessionId to delayMinutes
        return delayResult
    }
    override suspend fun setFeedbackOpen(orgSlug: String, eventId: String, open: Boolean) = feedbackResult(open)
    override suspend fun setRegistrationOpen(orgSlug: String, eventId: String, open: Boolean): AppResult<RegistrationStateDto> {
        registrationCalls += open
        return registrationResult(open)
    }
}

class FakeScheduleRepository : ScheduleRepository {
    val saved = MutableStateFlow<SavedSchedule?>(null)
    var syncResult: () -> AppResult<SavedSchedule> = { AppResult.Failure(AppError.Network()) }
    var syncCalls = 0

    override fun observe(orgSlug: String, eventId: String): Flow<SavedSchedule?> = saved
    override fun observeAll(): Flow<List<SavedSchedule>> = saved.map(::listOfNotNull)
    override suspend fun sync(orgSlug: String, eventId: String): AppResult<SavedSchedule> {
        syncCalls++
        return syncResult().also { if (it is AppResult.Success) saved.value = it.data }
    }
}

fun savedSchedule(schedule: OrgScheduleDto = OrgScheduleDto(), canEditDelays: Boolean = false, syncedAt: Long = 0) = SavedSchedule(
    orgSlug = "civic-tech",
    eventId = "e1",
    eventTitle = "Event e1",
    timezone = "Europe/London",
    location = "Kochi Makerspace",
    mapLinkUrl = null,
    canEditDelays = canEditDelays,
    schedule = schedule,
    generatedAt = null,
    syncedAt = syncedAt,
)
