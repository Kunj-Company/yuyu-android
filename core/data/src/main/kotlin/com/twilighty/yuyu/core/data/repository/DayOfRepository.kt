package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.map
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.error.apiCallUnit
import com.twilighty.yuyu.core.network.model.OpenStateRequest
import com.twilighty.yuyu.core.network.model.OrgScheduleDto
import com.twilighty.yuyu.core.network.model.RegistrationStateDto
import com.twilighty.yuyu.core.network.model.SessionDelayRequest
import javax.inject.Inject
import javax.inject.Singleton

interface DayOfRepository {
    suspend fun schedule(orgSlug: String, eventId: String): AppResult<OrgScheduleDto>

    /** [delayMinutes] in 0..1440. */
    suspend fun setSessionDelay(orgSlug: String, eventId: String, sessionId: String, delayMinutes: Int): AppResult<Unit>

    /** Returns the new open state. */
    suspend fun setFeedbackOpen(orgSlug: String, eventId: String, open: Boolean): AppResult<Boolean>
    suspend fun setRegistrationOpen(orgSlug: String, eventId: String, open: Boolean): AppResult<RegistrationStateDto>
}

@Singleton
class DayOfRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
) : DayOfRepository {
    override suspend fun schedule(orgSlug: String, eventId: String) = apiCall { api.schedule(orgSlug, eventId) }
    override suspend fun setSessionDelay(orgSlug: String, eventId: String, sessionId: String, delayMinutes: Int) =
        apiCallUnit { api.setSessionDelay(orgSlug, eventId, sessionId, SessionDelayRequest(delayMinutes.coerceIn(0, 1440))) }
    override suspend fun setFeedbackOpen(orgSlug: String, eventId: String, open: Boolean) =
        apiCall { api.setFeedbackState(orgSlug, eventId, OpenStateRequest(open)) }.map { it.open }
    override suspend fun setRegistrationOpen(orgSlug: String, eventId: String, open: Boolean) =
        apiCall { api.setRegistrationState(orgSlug, eventId, OpenStateRequest(open)) }
}
