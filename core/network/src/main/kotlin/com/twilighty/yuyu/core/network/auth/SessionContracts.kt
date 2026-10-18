package com.twilighty.yuyu.core.network.auth

import com.twilighty.yuyu.core.network.model.SessionDto
import okhttp3.HttpUrl

/** The Yuyu server the user picked at runtime (implemented in `:core:data`). */
interface ServerUrlProvider {
    /** Normalised base URL ending in `/`, or null before setup. Must be fast (in-memory). */
    fun currentServer(): HttpUrl?
}

/**
 * Token access for the network layer (implemented in `:core:data` by the
 * encrypted session store). Reads must be fast and non-blocking (in-memory).
 */
interface SessionTokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?

    /** Persist a rotated session from `/auth/refresh`. */
    suspend fun onRefreshed(session: SessionDto)

    /** The refresh token was rejected: clear the session and broadcast sign-out. */
    suspend fun onSessionInvalid()
}
