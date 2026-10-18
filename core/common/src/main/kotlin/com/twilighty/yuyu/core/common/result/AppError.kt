package com.twilighty.yuyu.core.common.result

/**
 * Typed failures, mirroring the mobile API error codes (docs/MOBILE_API.md
 * "Conventions") plus client-side transport failures. [message] is always
 * UI-safe: either the server's message or a plain-language default.
 */
sealed interface AppError {
    val message: String

    /** 400 INVALID_REQUEST. [fieldErrors] maps request field names to messages. */
    data class InvalidRequest(
        override val message: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
    ) : AppError

    /** 401 UNAUTHENTICATED: the session is gone (after one refresh attempt). */
    data class Unauthenticated(override val message: String = "Please sign in again.") : AppError

    /** 401 MFA_REQUIRED (sign-in only): ask for an authenticator or recovery code. */
    data class MfaRequired(override val message: String = "Enter the code from your authenticator app.") : AppError

    /** 403 EMAIL_VERIFICATION_REQUIRED (sign-in only). */
    data class EmailVerificationRequired(override val message: String = "Verify your email address, then sign in.") : AppError

    /** 403 FORBIDDEN. */
    data class Forbidden(override val message: String = "You don't have permission to do that.") : AppError

    /** 404 NOT_FOUND (also cross-organisation ids). */
    data class NotFound(override val message: String = "We couldn't find that.") : AppError

    /** 409 CONFLICT. */
    data class Conflict(override val message: String) : AppError

    /** 409 NEEDS_FORCE: a check-in status override is available. */
    data class NeedsForce(override val message: String) : AppError

    /** 429 RATE_LIMITED. [retryAfterSeconds] comes from the Retry-After header when present. */
    data class RateLimited(
        override val message: String = "Too many attempts. Try again shortly.",
        val retryAfterSeconds: Long? = null,
    ) : AppError

    /** 5xx / INTERNAL_ERROR. */
    data class Server(override val message: String = "Something went wrong on the server. Try again.") : AppError

    /** No connection, DNS, TLS or timeout. */
    data class Network(override val message: String = "You're offline or the server can't be reached.") : AppError

    /** No server has been chosen yet. */
    data class NoServer(override val message: String = "Choose a Yuyu server first.") : AppError

    /** Anything unexpected (malformed response, unknown code). */
    data class Unknown(override val message: String = "Something went wrong. Try again.") : AppError
}
