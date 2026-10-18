package com.twilighty.yuyu.core.network.auth

import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.di.BareClient
import com.twilighty.yuyu.core.network.interceptor.BaseUrlInterceptor
import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.RefreshRequest
import com.twilighty.yuyu.core.network.model.SessionDto
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Calls `POST /auth/refresh` on a client without the authenticator (so a
 * failing refresh can never recurse). Blocking: only called from OkHttp's
 * authenticator thread.
 */
class TokenRefresher @Inject constructor(
    @param:BareClient private val client: OkHttpClient,
) {
    sealed interface Outcome {
        data class Success(val session: SessionDto) : Outcome

        /** 4xx: the refresh token is unknown, expired, revoked, reused or stale. Sign out. */
        data object Rejected : Outcome

        /** Offline / 5xx: keep the session and let the caller fail softly. */
        data object Unavailable : Outcome
    }

    fun refresh(refreshToken: String): Outcome {
        val body = NetworkJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(BaseUrlInterceptor.PLACEHOLDER_BASE_URL + YuyuMobileApi.PREFIX + "/auth/refresh")
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val text = response.body.string()
                        val envelope = NetworkJson.decodeFromString(
                            ApiEnvelope.serializer(SessionDto.serializer()),
                            text,
                        )
                        envelope.data?.takeIf { envelope.ok }?.let { Outcome.Success(it) } ?: Outcome.Rejected
                    }
                    response.code in 400..499 && response.code != 429 && response.code != 408 -> Outcome.Rejected
                    else -> Outcome.Unavailable
                }
            }
        } catch (e: IOException) {
            Outcome.Unavailable
        } catch (e: SerializationException) {
            Outcome.Unavailable
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
