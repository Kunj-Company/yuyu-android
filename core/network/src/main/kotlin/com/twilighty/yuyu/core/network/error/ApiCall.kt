package com.twilighty.yuyu.core.network.error

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.network.interceptor.NoServerConfiguredException
import com.twilighty.yuyu.core.network.model.ApiEnvelope
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * Runs an enveloped API call and maps every outcome to [AppResult]. Success
 * requires `ok:true` with non-null `data`; use [apiCallUnit] for `data: null` routes.
 */
suspend fun <T : Any> apiCall(block: suspend () -> ApiEnvelope<T>): AppResult<T> = guard {
    val envelope = block()
    val data = envelope.data
    when {
        envelope.ok && data != null -> AppResult.Success(data)
        envelope.ok -> AppResult.Failure(AppError.Unknown("The server sent an empty response."))
        else -> AppResult.Failure(ApiErrorParser.fromEnvelope(envelope))
    }
}

/** For routes whose success is `{ ok:true, data:null }`. */
suspend fun apiCallUnit(block: suspend () -> ApiEnvelope<*>): AppResult<Unit> = guard {
    val envelope = block()
    if (envelope.ok) AppResult.Success(Unit) else AppResult.Failure(ApiErrorParser.fromEnvelope(envelope))
}

/** For bare (non-enveloped) endpoints such as `/api/health` and ticket status. */
suspend fun <T> rawCall(block: suspend () -> T): AppResult<T> = guard { AppResult.Success(block()) }

@PublishedApi
internal suspend fun <T> guard(block: suspend () -> AppResult<T>): AppResult<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    val response = e.response()
    val body = runCatching { response?.errorBody()?.string() }.getOrNull()
    AppResult.Failure(ApiErrorParser.parse(e.code(), body, response?.headers()?.get("Retry-After")))
} catch (e: NoServerConfiguredException) {
    AppResult.Failure(AppError.NoServer())
} catch (e: IOException) {
    AppResult.Failure(AppError.Network())
} catch (e: SerializationException) {
    AppResult.Failure(AppError.Unknown("The server sent a response this app doesn't understand. Is it a Yuyu server?"))
} catch (e: IllegalArgumentException) {
    AppResult.Failure(AppError.Unknown())
}
