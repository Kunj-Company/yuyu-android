package com.twilighty.yuyu.feature.broadcasts

import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.data.repository.BroadcastsRepository
import com.twilighty.yuyu.core.network.model.BroadcastAudienceDto
import com.twilighty.yuyu.core.network.model.BroadcastContentRequest
import com.twilighty.yuyu.core.network.model.BroadcastCountRequest
import com.twilighty.yuyu.core.network.model.BroadcastDesignDto
import com.twilighty.yuyu.core.network.model.BroadcastHistoryDto
import com.twilighty.yuyu.core.network.model.BroadcastMetaDto
import com.twilighty.yuyu.core.network.model.BroadcastPreviewDto
import com.twilighty.yuyu.core.network.model.SendBroadcastRequest
import com.twilighty.yuyu.core.network.model.SendBroadcastResultDto
import com.twilighty.yuyu.feature.broadcasts.draft.BroadcastDraft
import com.twilighty.yuyu.feature.broadcasts.draft.BroadcastDraftStore

val HISTORY_ROW = BroadcastHistoryDto(
    id = "b1",
    subject = "Doors open at 6",
    design = "letter",
    audience = BroadcastAudienceDto(listOf("CONFIRMED", "WAITLISTED"), "CHECKED_IN"),
    recipientCount = 57,
    sentAt = "2030-05-09T12:00:00.000Z",
    bodyHtml = "<p>See you <strong>tomorrow</strong>!</p>",
)

class FakeBroadcastsRepository : BroadcastsRepository {
    var meta: AppResult<BroadcastMetaDto> = AppResult.Success(
        BroadcastMetaDto(
            templates = TestFixtures.templates(),
            designs = listOf(
                BroadcastDesignDto("classic", "Classic", "Dark header with your organisation's name."),
                BroadcastDesignDto("letter", "Letter", "Warm paper and a serif heading."),
            ),
            placeholders = listOf("{{name}}", "{{event}}", "{{eventDate}}", "{{organisation}}", "{{ticketLink}}"),
            history = listOf(HISTORY_ROW),
        ),
    )
    var countResult: (BroadcastCountRequest) -> AppResult<Int> = { AppResult.Success(42) }
    var sendResult: AppResult<SendBroadcastResultDto> = AppResult.Success(SendBroadcastResultDto("bc1", 42))
    var testResult: AppResult<String> = AppResult.Success("you@example.test")

    val countCalls = mutableListOf<BroadcastCountRequest>()
    val sendCalls = mutableListOf<SendBroadcastRequest>()
    val testCalls = mutableListOf<BroadcastContentRequest>()
    var metaCalls = 0

    override suspend fun meta(orgSlug: String, eventId: String): AppResult<BroadcastMetaDto> {
        metaCalls++
        return meta
    }

    override suspend fun count(orgSlug: String, eventId: String, request: BroadcastCountRequest): AppResult<Int> {
        countCalls += request
        return countResult(request)
    }

    override suspend fun preview(orgSlug: String, eventId: String, content: BroadcastContentRequest): AppResult<BroadcastPreviewDto> =
        AppResult.Success(BroadcastPreviewDto(content.subject, "<p>preview</p>", "preview"))

    override suspend fun sendTest(orgSlug: String, eventId: String, content: BroadcastContentRequest): AppResult<String> {
        testCalls += content
        return testResult
    }

    override suspend fun send(orgSlug: String, eventId: String, request: SendBroadcastRequest): AppResult<SendBroadcastResultDto> {
        sendCalls += request
        return sendResult
    }
}

class FakeDraftStore : BroadcastDraftStore {
    val drafts = mutableMapOf<String, BroadcastDraft>()
    var saves = 0

    override suspend fun load(orgSlug: String, eventId: String): BroadcastDraft? = drafts["$orgSlug/$eventId"]

    override suspend fun save(orgSlug: String, eventId: String, draft: BroadcastDraft) {
        saves++
        drafts["$orgSlug/$eventId"] = draft
    }

    override suspend fun clear(orgSlug: String, eventId: String) {
        drafts.remove("$orgSlug/$eventId")
    }
}
