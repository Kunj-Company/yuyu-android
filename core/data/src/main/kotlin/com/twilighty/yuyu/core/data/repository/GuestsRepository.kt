package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.map
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.error.apiCallUnit
import com.twilighty.yuyu.core.network.model.AddGuestRequest
import com.twilighty.yuyu.core.network.model.GuestDetailDto
import com.twilighty.yuyu.core.network.model.GuestDto
import com.twilighty.yuyu.core.network.model.GuestFilter
import com.twilighty.yuyu.core.network.model.GuestPageDto
import com.twilighty.yuyu.core.network.model.RestoreGuestRequest
import javax.inject.Inject
import javax.inject.Singleton

data class GuestQuery(
    val status: String = GuestFilter.STATUS_ALL,
    val checkedIn: String = GuestFilter.CHECKED_IN_ANY,
    val search: String? = null,
    val cursor: String? = null,
    val limit: Int? = null,
)

interface GuestsRepository {
    suspend fun guests(orgSlug: String, eventId: String, query: GuestQuery = GuestQuery()): AppResult<GuestPageDto>
    suspend fun guest(orgSlug: String, eventId: String, rsvpId: String): AppResult<GuestDetailDto>
    suspend fun approve(orgSlug: String, eventId: String, rsvpId: String): AppResult<GuestDto>
    suspend fun reject(orgSlug: String, eventId: String, rsvpId: String): AppResult<GuestDto>
    suspend fun promote(orgSlug: String, eventId: String, rsvpId: String): AppResult<GuestDto>
    suspend fun add(orgSlug: String, eventId: String, name: String, email: String): AppResult<GuestDto>

    /** Returns the undo id for [restore] (offer it in an Undo snackbar). */
    suspend fun delete(orgSlug: String, eventId: String, rsvpId: String): AppResult<String>
    suspend fun restore(orgSlug: String, eventId: String, undoId: String): AppResult<Unit>
}

@Singleton
class GuestsRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
) : GuestsRepository {
    override suspend fun guests(orgSlug: String, eventId: String, query: GuestQuery) = apiCall {
        api.guests(orgSlug, eventId, query.status, query.checkedIn, query.search?.trim()?.ifEmpty { null }, query.cursor, query.limit)
    }
    override suspend fun guest(orgSlug: String, eventId: String, rsvpId: String) = apiCall { api.guest(orgSlug, eventId, rsvpId) }
    override suspend fun approve(orgSlug: String, eventId: String, rsvpId: String) = apiCall { api.approveGuest(orgSlug, eventId, rsvpId) }
    override suspend fun reject(orgSlug: String, eventId: String, rsvpId: String) = apiCall { api.rejectGuest(orgSlug, eventId, rsvpId) }
    override suspend fun promote(orgSlug: String, eventId: String, rsvpId: String) = apiCall { api.promoteGuest(orgSlug, eventId, rsvpId) }
    override suspend fun add(orgSlug: String, eventId: String, name: String, email: String) =
        apiCall { api.addGuest(orgSlug, eventId, AddGuestRequest(name.trim(), email.trim())) }
    override suspend fun delete(orgSlug: String, eventId: String, rsvpId: String): AppResult<String> =
        apiCall { api.deleteGuest(orgSlug, eventId, rsvpId) }.map { it.undoId }
    override suspend fun restore(orgSlug: String, eventId: String, undoId: String) =
        apiCallUnit { api.restoreGuest(orgSlug, eventId, RestoreGuestRequest(undoId)) }
}
