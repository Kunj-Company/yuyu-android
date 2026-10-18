package com.twilighty.yuyu.core.network.error

import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.ApiErrorBody
import com.twilighty.yuyu.core.network.model.ApiErrorCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Turns the error envelope into a typed [AppError]. Understands both the mobile
 * shape (`error: { code, message, fieldErrors }`) and the reused web endpoints'
 * shape (`error: "text"`, optional `needsForce`), falling back to the HTTP status.
 */
object ApiErrorParser {

    fun parse(httpStatus: Int, body: String?, retryAfter: String? = null): AppError {
        val root: JsonObject? = body?.takeIf { it.isNotBlank() }?.let {
            runCatching { NetworkJson.parseToJsonElement(it).jsonObject }.getOrNull()
        }
        val needsForce = (root?.get("needsForce") as? JsonPrimitive)?.booleanOrNull
        return fromError(root?.get("error"), httpStatus, retryAfter, needsForce)
    }

    /** For `ok:false` envelopes that arrive with a 2xx status (station gate failures). */
    fun fromEnvelope(envelope: ApiEnvelope<*>, httpStatus: Int = 200): AppError =
        fromError(envelope.error, httpStatus, null, envelope.needsForce)

    fun fromError(error: JsonElement?, httpStatus: Int, retryAfter: String?, needsForce: Boolean?): AppError {
        val parsed: ApiErrorBody = when (error) {
            is JsonObject -> runCatching { NetworkJson.decodeFromJsonElement<ApiErrorBody>(error) }.getOrDefault(ApiErrorBody())
            is JsonPrimitive -> ApiErrorBody(message = error.content.takeIf { error.isString })
            else -> ApiErrorBody()
        }
        val message = parsed.message?.takeIf { it.isNotBlank() }
        val retrySeconds = retryAfter?.trim()?.toLongOrNull()
        val code = parsed.code ?: when {
            needsForce == true -> ApiErrorCode.NEEDS_FORCE
            else -> codeForStatus(httpStatus)
        }
        return when (code) {
            ApiErrorCode.INVALID_REQUEST -> AppError.InvalidRequest(
                message ?: "Check the highlighted fields and try again.",
                parsed.fieldErrors.orEmpty(),
            )
            ApiErrorCode.UNAUTHENTICATED -> message?.let { AppError.Unauthenticated(it) } ?: AppError.Unauthenticated()
            ApiErrorCode.MFA_REQUIRED -> message?.let { AppError.MfaRequired(it) } ?: AppError.MfaRequired()
            ApiErrorCode.EMAIL_VERIFICATION_REQUIRED ->
                message?.let { AppError.EmailVerificationRequired(it) } ?: AppError.EmailVerificationRequired()
            ApiErrorCode.FORBIDDEN -> message?.let { AppError.Forbidden(it) } ?: AppError.Forbidden()
            ApiErrorCode.NOT_FOUND -> message?.let { AppError.NotFound(it) } ?: AppError.NotFound()
            ApiErrorCode.CONFLICT -> AppError.Conflict(message ?: "That changed in the meantime. Refresh and try again.")
            ApiErrorCode.NEEDS_FORCE -> AppError.NeedsForce(message ?: "This guest isn't confirmed. Check them in anyway?")
            ApiErrorCode.RATE_LIMITED -> AppError.RateLimited(message ?: "Too many attempts. Try again shortly.", retrySeconds)
            ApiErrorCode.INTERNAL_ERROR -> message?.let { AppError.Server(it) } ?: AppError.Server()
            else -> message?.let { AppError.Unknown(it) } ?: AppError.Unknown()
        }
    }

    private fun codeForStatus(status: Int): String? = when (status) {
        400, 413, 422 -> ApiErrorCode.INVALID_REQUEST
        401 -> ApiErrorCode.UNAUTHENTICATED
        403 -> ApiErrorCode.FORBIDDEN
        404 -> ApiErrorCode.NOT_FOUND
        409 -> ApiErrorCode.CONFLICT
        429 -> ApiErrorCode.RATE_LIMITED
        in 500..599 -> ApiErrorCode.INTERNAL_ERROR
        else -> null
    }
}
