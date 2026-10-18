package com.twilighty.yuyu.feature.attendee

import android.net.Uri
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.CachedRegistrations
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.data.repository.PublicEventsRepository
import com.twilighty.yuyu.core.data.repository.ServerInfo
import com.twilighty.yuyu.core.data.repository.SessionState
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.AuthConfigDto
import com.twilighty.yuyu.core.network.model.EventPageDto
import com.twilighty.yuyu.core.network.model.FeedbackFormDto
import com.twilighty.yuyu.core.network.model.FeedbackSubmitRequest
import com.twilighty.yuyu.core.network.model.FeedbackSubmitResultDto
import com.twilighty.yuyu.core.network.model.MeDto
import com.twilighty.yuyu.core.network.model.PublicEventResponseDto
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.core.network.model.RegistrationsDto
import com.twilighty.yuyu.core.network.model.RsvpRequest
import com.twilighty.yuyu.core.network.model.RsvpResultDto
import com.twilighty.yuyu.core.network.model.ShortLinkDto
import com.twilighty.yuyu.core.network.model.TicketEvent
import com.twilighty.yuyu.core.network.model.TicketStatusDto
import com.twilighty.yuyu.core.network.model.UserDto
import com.twilighty.yuyu.feature.attendee.common.CertificateStore
import com.twilighty.yuyu.feature.attendee.reminders.NotificationPermission
import com.twilighty.yuyu.feature.attendee.reminders.PlannedReminder
import com.twilighty.yuyu.feature.attendee.reminders.ReminderPreferences
import com.twilighty.yuyu.feature.attendee.reminders.ReminderWork
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Contract fixtures shared with :core:network (docs/mobile-api-fixtures). */
object Fixtures {
    private val dir: File
        get() = listOf("../../core/network/src/test/resources/fixtures", "core/network/src/test/resources/fixtures")
            .map(::File).first { it.isDirectory }

    fun <T> data(name: String, serializer: KSerializer<T>): T =
        NetworkJson.decodeFromString(ApiEnvelope.serializer(serializer), File(dir, name).readText()).data!!

    fun publicEvent(): PublicEventResponseDto = data("public-event.json", PublicEventResponseDto.serializer())
    fun registrations(): RegistrationsDto = data("registrations.json", RegistrationsDto.serializer())
}

class FakeAuthRepository(user: UserDto? = UserDto("u1", name = "Ada Lovelace", email = "ada@example.org")) : AuthRepository {
    private val server = "https://yuyu.example/".toHttpUrl()
    override val sessionState: Flow<SessionState> =
        flowOf(if (user != null) SessionState.SignedIn(server, user) else SessionState.SignedOut(server))
    override val forcedSignOuts: SharedFlow<Unit> = MutableSharedFlow()
    override suspend fun initialize() = Unit
    override suspend fun probeServer(url: HttpUrl): AppResult<ServerInfo> = error("unused")
    override suspend fun selectServer(url: HttpUrl) = Unit
    override suspend fun clearServer() = Unit
    override suspend fun authConfig(): AppResult<AuthConfigDto> = AppResult.Success(AuthConfigDto())
    override suspend fun signIn(email: String, password: String, totp: String?): AppResult<UserDto> = error("unused")
    override suspend fun signInWithGoogle(idToken: String): AppResult<UserDto> = error("unused")
    override suspend fun signOut() = Unit
    override suspend fun signOutEverywhere(): AppResult<Unit> = AppResult.Success(Unit)
    override fun webUrl(path: String): String = "https://yuyu.example/$path"
}

class FakePublicEventsRepository : PublicEventsRepository {
    var event: AppResult<PublicEventResponseDto> = AppResult.Success(Fixtures.publicEvent())
    var rsvp: (RsvpRequest) -> AppResult<RsvpResultDto> = { AppResult.Success(RsvpResultDto("r1", "CONFIRMED", "tkFRESHticketToken0001")) }
    val rsvpRequests = mutableListOf<RsvpRequest>()
    var feedback: AppResult<FeedbackFormDto> = AppResult.Failure(AppError.NotFound())
    var feedbackResult: AppResult<FeedbackSubmitResultDto> = AppResult.Success(FeedbackSubmitResultDto(null))
    val feedbackRequests = mutableListOf<FeedbackSubmitRequest>()

    /** Each call to [ticketEvents] takes the next scripted stream. */
    val streams = ArrayDeque<Flow<TicketEvent>>()
    var streamCalls = 0
    /** Each call to [ticketStatus] takes the next scripted status (the last one repeats). */
    val statuses = ArrayDeque<AppResult<TicketStatusDto>>()
    var statusCalls = 0

    override suspend fun discover(cursor: String?, limit: Int?): AppResult<EventPageDto> = AppResult.Success(EventPageDto())
    override suspend fun search(query: String): AppResult<EventPageDto> = AppResult.Success(EventPageDto())
    override suspend fun resolveShortLink(code: String): AppResult<ShortLinkDto> = AppResult.Failure(AppError.NotFound())
    override suspend fun event(orgSlug: String, eventSlug: String) = event
    override suspend fun register(request: RsvpRequest): AppResult<RsvpResultDto> {
        rsvpRequests += request
        return rsvp(request)
    }
    override suspend fun feedbackForm(orgSlug: String, eventSlug: String) = feedback
    override suspend fun submitFeedback(request: FeedbackSubmitRequest): AppResult<FeedbackSubmitResultDto> {
        feedbackRequests += request
        return feedbackResult
    }
    override suspend fun ticketStatus(ticketToken: String): AppResult<TicketStatusDto> {
        statusCalls++
        return if (statuses.size > 1) statuses.removeFirst() else statuses.firstOrNull() ?: AppResult.Success(TicketStatusDto())
    }
    override fun ticketEvents(ticketToken: String): Flow<TicketEvent> {
        streamCalls++
        return streams.removeFirstOrNull() ?: flow { throw java.io.IOException("no stream") }
    }
    override fun certificateUrl(certificateToken: String): String = "https://yuyu.example/api/feedback/certificate/$certificateToken"
}

class FakeMeRepository(initial: RegistrationsDto = RegistrationsDto()) : MeRepository {
    override val me: StateFlow<MeDto?> = MutableStateFlow(null)
    var remote: AppResult<RegistrationsDto> = AppResult.Success(initial)
    private val cache = MutableStateFlow(CachedRegistrations(initial.upcoming, initial.past))
    var registrationCalls = 0
    val cancelled = mutableListOf<String>()
    var cancelResult: AppResult<Unit> = AppResult.Success(Unit)

    fun setCache(upcoming: List<RegistrationDto>, past: List<RegistrationDto> = emptyList()) {
        cache.value = CachedRegistrations(upcoming, past)
    }

    override suspend fun refreshMe(): AppResult<MeDto> = error("unused")
    override suspend fun updateName(name: String): AppResult<UserDto> = error("unused")
    override suspend fun registrations(): AppResult<RegistrationsDto> {
        registrationCalls++
        val r = remote
        if (r is AppResult.Success) cache.value = CachedRegistrations(r.data.upcoming, r.data.past)
        return r
    }
    override fun cachedRegistrations(): Flow<CachedRegistrations> = cache
    override suspend fun cachedRegistration(rsvpId: String): RegistrationDto? =
        (cache.value.upcoming + cache.value.past).firstOrNull { it.rsvpId == rsvpId }
    override suspend fun cancelRegistration(rsvpId: String): AppResult<Unit> {
        cancelled += rsvpId
        return cancelResult
    }
}

class FakeReminderWork : ReminderWork {
    val scheduled = linkedMapOf<String, Pair<PlannedReminder, Long>>()
    val cancelledNames = mutableListOf<String>()
    var scheduleCalls = 0
    override suspend fun scheduledNames(): Set<String> = scheduled.keys.toSet()
    override fun schedule(reminder: PlannedReminder, delayMillis: Long) {
        scheduleCalls++
        scheduled[reminder.workName] = reminder to delayMillis
    }
    override fun cancel(workName: String) {
        cancelledNames += workName
        scheduled.remove(workName)
    }
}

class FakeReminderPreferences(enabled: Boolean = true, prompted: Boolean = false) : ReminderPreferences {
    private val enabledState = MutableStateFlow(enabled)
    private val promptedState = MutableStateFlow(prompted)
    override val remindersEnabled: Flow<Boolean> = enabledState
    override val permissionPromptShown: Flow<Boolean> = promptedState
    override suspend fun setRemindersEnabled(enabled: Boolean) { enabledState.value = enabled }
    override suspend fun setPermissionPromptShown() { promptedState.value = true }
    val prompted: Boolean get() = promptedState.value
}

class FakeNotificationPermission(var missing: Boolean) : NotificationPermission {
    override fun needsRequest(): Boolean = missing
}

class FakeCertificateStore : CertificateStore {
    val saved = mutableListOf<String>()
    override suspend fun saveToGallery(url: String, title: String): AppResult<Uri> {
        saved += url
        return AppResult.Failure(AppError.Unknown("not in tests"))
    }
    override suspend fun prepareShare(url: String, title: String): AppResult<Uri> = AppResult.Failure(AppError.Unknown("not in tests"))
}
