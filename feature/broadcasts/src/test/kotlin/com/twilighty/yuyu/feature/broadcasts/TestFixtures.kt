package com.twilighty.yuyu.feature.broadcasts

import com.twilighty.yuyu.core.network.model.BroadcastTemplateDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object TestFixtures {
    /** Every starter template from the web app's `lib/broadcastTemplate.ts`, copied verbatim. */
    fun templates(): List<BroadcastTemplateDto> {
        val text = checkNotNull(javaClass.classLoader?.getResource("fixtures/broadcast-templates.json")).readText()
        return Json.decodeFromString(ListSerializer(BroadcastTemplateDto.serializer()), text)
    }
}
