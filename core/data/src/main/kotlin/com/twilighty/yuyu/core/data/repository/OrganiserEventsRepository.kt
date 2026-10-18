package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.model.OrgEventDetailResponseDto
import com.twilighty.yuyu.core.network.model.OrgEventsDto
import com.twilighty.yuyu.core.network.model.OrgEventsFilter
import javax.inject.Inject
import javax.inject.Singleton

interface OrganiserEventsRepository {
    /** [filter]: [OrgEventsFilter] value. */
    suspend fun events(orgSlug: String, filter: String = OrgEventsFilter.UPCOMING): AppResult<OrgEventsDto>
    suspend fun event(orgSlug: String, eventId: String): AppResult<OrgEventDetailResponseDto>
}

@Singleton
class OrganiserEventsRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
) : OrganiserEventsRepository {
    override suspend fun events(orgSlug: String, filter: String) = apiCall { api.orgEvents(orgSlug, filter) }
    override suspend fun event(orgSlug: String, eventId: String) = apiCall { api.orgEvent(orgSlug, eventId) }
}
