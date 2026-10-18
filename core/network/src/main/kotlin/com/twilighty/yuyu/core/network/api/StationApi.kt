package com.twilighty.yuyu.core.network.api

import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.StationRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * `POST /api/check-in/station`. Every action shares one URL and the response
 * `data` shape depends on `action`, so it is returned raw: decode with
 * `NetworkJson.decodeFromJsonElement<StationBootstrapDto>(...)` etc.
 * Use only through a [com.twilighty.yuyu.core.network.station.StationClientFactory]
 * client, which holds the station's proof cookie in memory.
 */
interface StationApi {
    @POST("api/check-in/station")
    suspend fun call(@Body body: StationRequest): ApiEnvelope<JsonElement>
}
