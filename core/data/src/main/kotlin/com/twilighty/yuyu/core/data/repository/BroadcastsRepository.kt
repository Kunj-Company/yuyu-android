package com.twilighty.yuyu.core.data.repository

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.map
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.model.BroadcastContentRequest
import com.twilighty.yuyu.core.network.model.BroadcastCountRequest
import com.twilighty.yuyu.core.network.model.BroadcastMetaDto
import com.twilighty.yuyu.core.network.model.BroadcastPreviewDto
import com.twilighty.yuyu.core.network.model.SendBroadcastRequest
import com.twilighty.yuyu.core.network.model.SendBroadcastResultDto
import javax.inject.Inject
import javax.inject.Singleton

interface BroadcastsRepository {
    suspend fun meta(orgSlug: String, eventId: String): AppResult<BroadcastMetaDto>
    suspend fun count(orgSlug: String, eventId: String, request: BroadcastCountRequest): AppResult<Int>
    suspend fun preview(orgSlug: String, eventId: String, content: BroadcastContentRequest): AppResult<BroadcastPreviewDto>

    /** Sends a test to the signed-in user; returns the address it went to. */
    suspend fun sendTest(orgSlug: String, eventId: String, content: BroadcastContentRequest): AppResult<String>

    /**
     * Sends for real. Call only after the 5-second client-side undo window has
     * passed (docs/MOBILE_API.md "Emails").
     */
    suspend fun send(orgSlug: String, eventId: String, request: SendBroadcastRequest): AppResult<SendBroadcastResultDto>
}

@Singleton
class BroadcastsRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
) : BroadcastsRepository {
    override suspend fun meta(orgSlug: String, eventId: String) = apiCall { api.broadcastMeta(orgSlug, eventId) }
    override suspend fun count(orgSlug: String, eventId: String, request: BroadcastCountRequest) =
        apiCall { api.broadcastCount(orgSlug, eventId, request) }.map { it.count }
    override suspend fun preview(orgSlug: String, eventId: String, content: BroadcastContentRequest) =
        apiCall { api.broadcastPreview(orgSlug, eventId, content) }
    override suspend fun sendTest(orgSlug: String, eventId: String, content: BroadcastContentRequest) =
        apiCall { api.broadcastTest(orgSlug, eventId, content) }.map { it.to }
    override suspend fun send(orgSlug: String, eventId: String, request: SendBroadcastRequest) =
        apiCall { api.sendBroadcast(orgSlug, eventId, request) }
}
