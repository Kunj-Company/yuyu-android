package com.twilighty.yuyu.core.network.model

import kotlinx.serialization.Serializable

/** Sent on sign-in so the server can label the session (contract addition: optional `platform`). */
const val MOBILE_PLATFORM = "android"

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    /** Authenticator code or recovery code; omitted until the server asks with MFA_REQUIRED. */
    val totp: String? = null,
    val deviceName: String? = null,
    val platform: String = MOBILE_PLATFORM,
)

@Serializable
data class GoogleLoginRequest(
    val idToken: String,
    val deviceName: String? = null,
    val platform: String = MOBILE_PLATFORM,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** Tokens are opaque (`yma_…` access, `ymr_…` refresh): never parse or log them. */
@Serializable
data class SessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: UserDto,
) {
    // Never print tokens, even in debug logs or crash reports.
    override fun toString(): String = "SessionDto(user=${user.id}, accessTokenExpiresAt=$accessTokenExpiresAt)"
}

@Serializable
data class UserDto(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val imageUrl: String? = null,
    val emailVerified: Boolean = false,
    val mfaEnabled: Boolean = false,
)

@Serializable
data class AuthConfigDto(
    val instanceName: String = "Yuyu",
    val googleSignIn: GoogleSignInConfigDto = GoogleSignInConfigDto(),
    val signupEnabled: Boolean = true,
    val baseUrl: String? = null,
)

@Serializable
data class GoogleSignInConfigDto(
    val enabled: Boolean = false,
    val webClientId: String? = null,
)

/** `GET /api/health` (not wrapped in the mobile envelope's `data`). */
@Serializable
data class HealthDto(
    val ok: Boolean = false,
    val uptimeMs: Long? = null,
    val time: String? = null,
)
