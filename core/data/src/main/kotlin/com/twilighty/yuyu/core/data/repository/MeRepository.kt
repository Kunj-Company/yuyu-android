package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.onSuccess
import com.twilighty.yuyu.core.data.crypto.SecretCipher
import com.twilighty.yuyu.core.data.crypto.decryptFromBase64
import com.twilighty.yuyu.core.data.crypto.encryptToBase64
import com.twilighty.yuyu.core.data.session.SessionStore
import com.twilighty.yuyu.core.database.dao.CachedTicketDao
import com.twilighty.yuyu.core.database.entity.CachedTicketEntity
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.error.apiCallUnit
import com.twilighty.yuyu.core.network.model.EventSummaryDto
import com.twilighty.yuyu.core.network.model.MeDto
import com.twilighty.yuyu.core.network.model.OrgRefDto
import com.twilighty.yuyu.core.network.model.RegistrationDto
import com.twilighty.yuyu.core.network.model.RegistrationsDto
import com.twilighty.yuyu.core.network.model.UpdateMeRequest
import com.twilighty.yuyu.core.network.model.UserDto
import com.twilighty.yuyu.core.common.dispatchers.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** Cached registrations for offline display, split like the API. */
data class CachedRegistrations(val upcoming: List<RegistrationDto>, val past: List<RegistrationDto>)

interface MeRepository {
    /** Last loaded `GET /me` (null until [refreshMe] succeeds). Drives the Organise tab visibility. */
    val me: StateFlow<MeDto?>

    suspend fun refreshMe(): AppResult<MeDto>

    suspend fun updateName(name: String): AppResult<UserDto>

    /** `GET /me/registrations`; on success the ticket cache is replaced. */
    suspend fun registrations(): AppResult<RegistrationsDto>

    /** Offline copy of the user's registrations (ticket tokens decrypted). */
    fun cachedRegistrations(): Flow<CachedRegistrations>

    /** One cached registration, e.g. to show a ticket with no connection. */
    suspend fun cachedRegistration(rsvpId: String): RegistrationDto?

    suspend fun cancelRegistration(rsvpId: String): AppResult<Unit>
}

@Singleton
class MeRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
    private val sessionStore: SessionStore,
    private val ticketDao: CachedTicketDao,
    private val cipher: SecretCipher,
    @param:ApplicationScope private val scope: CoroutineScope,
) : MeRepository {
    private val meState = MutableStateFlow<MeDto?>(null)
    override val me: StateFlow<MeDto?> = meState.asStateFlow()

    init {
        // Never show one account's organisations to the next: forget /me on sign-out.
        scope.launch {
            sessionStore.session.collect { if (it == null) meState.value = null }
        }
    }

    override suspend fun refreshMe(): AppResult<MeDto> = apiCall { api.me() }.onSuccess {
        meState.value = it
        sessionStore.updateUser(it.user)
    }

    override suspend fun updateName(name: String): AppResult<UserDto> =
        apiCall { api.updateMe(UpdateMeRequest(name.trim())) }.onSuccess { user ->
            sessionStore.updateUser(user)
            meState.value = meState.value?.copy(user = user)
        }

    override suspend fun registrations(): AppResult<RegistrationsDto> = apiCall { api.myRegistrations() }.onSuccess { data ->
        val now = System.currentTimeMillis()
        ticketDao.replaceAll(
            data.upcoming.map { it.toEntity(isUpcoming = true, now) } + data.past.map { it.toEntity(isUpcoming = false, now) },
        )
    }

    override fun cachedRegistrations(): Flow<CachedRegistrations> = ticketDao.observeAll().map { rows ->
        val (upcoming, past) = rows.partition { it.isUpcoming }
        CachedRegistrations(upcoming.map { it.toDto() }, past.map { it.toDto() }.reversed())
    }

    override suspend fun cachedRegistration(rsvpId: String): RegistrationDto? = ticketDao.get(rsvpId)?.toDto()

    override suspend fun cancelRegistration(rsvpId: String): AppResult<Unit> =
        apiCallUnit { api.cancelRegistration(rsvpId) }

    private fun RegistrationDto.toEntity(isUpcoming: Boolean, now: Long) = CachedTicketEntity(
        rsvpId = rsvpId,
        status = status,
        checkedInAt = checkedInAt,
        registeredAt = registeredAt,
        canCancel = canCancel,
        encryptedTicketToken = ticketToken?.let { cipher.encryptToBase64(it) },
        isUpcoming = isUpcoming,
        eventId = event.id,
        eventSlug = event.slug,
        eventTitle = event.title,
        coverImageUrl = event.coverImageUrl,
        startDateTime = event.startDateTime,
        endDateTime = event.endDateTime,
        timezone = event.timezone,
        location = event.location,
        mapLinkUrl = event.mapLinkUrl,
        isOnline = event.isOnline,
        accentColor = event.accentColor,
        eventStatus = event.status,
        orgSlug = event.organisation.slug,
        orgName = event.organisation.name,
        orgLogoUrl = event.organisation.logoUrl,
        instanceId = instanceId,
        cachedAt = now,
    )

    private fun CachedTicketEntity.toDto() = RegistrationDto(
        rsvpId = rsvpId,
        status = status,
        checkedInAt = checkedInAt,
        registeredAt = registeredAt,
        canCancel = canCancel,
        ticketToken = encryptedTicketToken?.let { cipher.decryptFromBase64(it) },
        event = EventSummaryDto(
            id = eventId,
            slug = eventSlug,
            title = eventTitle,
            coverImageUrl = coverImageUrl,
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            timezone = timezone,
            location = location,
            mapLinkUrl = mapLinkUrl,
            isOnline = isOnline,
            accentColor = accentColor,
            status = eventStatus,
            organisation = OrgRefDto(orgSlug, orgName, orgLogoUrl),
        ),
        instanceId = instanceId,
    )
}
