package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Every mobile response: `{ "ok": true, "data": ... }` or
 * `{ "ok": false, "error": { "code", "message", "fieldErrors"? } }`.
 *
 * [error] stays a raw [JsonElement] because the reused web endpoints
 * (`/api/feedback`, `/api/check-in/station`, `/api/rsvp`) send a plain string
 * instead of an object; [com.twilighty.yuyu.core.network.error.ApiErrorParser]
 * understands both shapes.
 */
@Serializable
data class ApiEnvelope<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: JsonElement? = null,
    /** Only on `/api/check-in/station` failures: a status override is possible. */
    val needsForce: Boolean? = null,
)

@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val fieldErrors: Map<String, List<String>>? = null,
)

/** Mobile API error codes (docs/MOBILE_API.md "Conventions"). */
object ApiErrorCode {
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val UNAUTHENTICATED = "UNAUTHENTICATED"
    const val MFA_REQUIRED = "MFA_REQUIRED"
    const val EMAIL_VERIFICATION_REQUIRED = "EMAIL_VERIFICATION_REQUIRED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
    const val NEEDS_FORCE = "NEEDS_FORCE"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}

/** RSVP statuses as sent by the server. Kept as strings so new values never crash parsing. */
object RsvpStatus {
    const val CONFIRMED = "CONFIRMED"
    const val WAITLISTED = "WAITLISTED"
    const val PENDING_APPROVAL = "PENDING_APPROVAL"
    const val REJECTED = "REJECTED"
    const val CANCELLED = "CANCELLED"
}

/** Organisation roles. */
object OrgRole {
    const val OWNER = "OWNER"
    const val ADMIN = "ADMIN"
    const val MEMBER = "MEMBER"
}

/** `{ "label", "value" }` pairs used by check-in details and guest answers. */
@Serializable
data class LabelValueDto(val label: String, val value: String)
