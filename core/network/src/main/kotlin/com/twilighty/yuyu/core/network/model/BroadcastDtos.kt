package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class BroadcastMetaDto(
    val templates: List<BroadcastTemplateDto> = emptyList(),
    val designs: List<BroadcastDesignDto> = emptyList(),
    val placeholders: List<String> = emptyList(),
    val history: List<BroadcastHistoryDto> = emptyList(),
)

@Serializable
data class BroadcastTemplateDto(
    val id: String,
    val group: String? = null,
    val name: String,
    val description: String? = null,
    val subject: String,
    val bodyHtml: String,
    val audience: BroadcastAudienceDto = BroadcastAudienceDto(),
)

@Serializable
data class BroadcastAudienceDto(
    val statuses: List<String> = emptyList(),
    /** ANY | CHECKED_IN | NOT_CHECKED_IN, see [BroadcastAttendance]. */
    val attendance: String = BroadcastAttendance.ANY,
)

object BroadcastAttendance {
    const val ANY = "ANY"
    const val CHECKED_IN = "CHECKED_IN"
    const val NOT_CHECKED_IN = "NOT_CHECKED_IN"
}

@Serializable
data class BroadcastDesignDto(
    val id: String,
    val label: String,
    val description: String? = null,
)

@Serializable
data class BroadcastHistoryDto(
    val id: String,
    val subject: String,
    val design: String? = null,
    val audience: BroadcastAudienceDto = BroadcastAudienceDto(),
    val recipientCount: Int = 0,
    val sentAt: String,
    val bodyHtml: String? = null,
)

@Serializable
data class BroadcastCountRequest(
    val statuses: List<String>,
    val attendance: String = BroadcastAttendance.ANY,
)

@Serializable
data class CountDto(val count: Int)

/** [design]: one of [BroadcastDesign] ids (default classic). */
@Serializable
data class BroadcastContentRequest(
    val subject: String,
    val body: String,
    val design: String = BroadcastDesign.CLASSIC,
)

object BroadcastDesign {
    const val CLASSIC = "classic"
    const val MINIMAL = "minimal"
    const val ACCENT = "accent"
    const val PHOTO = "photo"
    const val LETTER = "letter"
}

@Serializable
data class BroadcastPreviewDto(
    val subject: String,
    val html: String,
    val text: String = "",
)

@Serializable
data class BroadcastTestResultDto(val to: String)

@Serializable
data class SendBroadcastRequest(
    val statuses: List<String>,
    val attendance: String,
    val subject: String,
    val body: String,
    val design: String = BroadcastDesign.CLASSIC,
)

/** `POST …/broadcasts` returns 201. */
@Serializable
data class SendBroadcastResultDto(
    val broadcastId: String,
    val recipientCount: Int,
)
