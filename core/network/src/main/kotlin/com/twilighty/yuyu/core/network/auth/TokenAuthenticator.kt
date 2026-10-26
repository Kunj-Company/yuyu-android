package com.twilighty.yuyu.core.network.auth

import com.twilighty.yuyu.core.network.interceptor.AuthInterceptor
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On a 401 for a request that carried our bearer, refresh the session once
 * (serialised by a [Mutex] so parallel 401s share one refresh) and retry with
 * the new token. If the refresh token is rejected, the session is cleared and
 * sign-out is broadcast via [SessionTokenStore.onSessionInvalid]; requests to
 * public routes (where the bearer is optional) are then retried anonymously. Network
 * failures during refresh keep the session (the original 401 surfaces).
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: SessionTokenStore,
    private val refresher: TokenRefresher,
) : Authenticator {
    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val sentHeader = request.header(AuthInterceptor.HEADER) ?: return null
        if (!sentHeader.startsWith("Bearer ")) return null
        if (AuthInterceptor.isUnauthenticatedRoute(request)) return null
        if (priorResponseCount(response) >= 2) return null
        val sentToken = sentHeader.removePrefix("Bearer ")

        // OkHttp rethrows an authenticator's exception on its dispatcher thread, which kills
        // the app: any unexpected failure here just lets the original 401 surface instead.
        return try {
            refreshAndRetry(request, sentToken)
        } catch (e: Exception) {
            null
        }
    }

    private fun refreshAndRetry(request: Request, sentToken: String): Request? =
        runBlocking {
            mutex.withLock {
                // Another request already refreshed while we waited: just retry.
                val current = tokenStore.accessToken()
                if (current != null && current != sentToken) {
                    return@withLock request.withBearer(current)
                }
                val refreshToken = tokenStore.refreshToken()
                if (refreshToken == null) {
                    tokenStore.onSessionInvalid()
                    return@withLock if (isPublicRoute(request)) request.newBuilder().removeHeader(AuthInterceptor.HEADER).build() else null
                }
                when (val outcome = refresher.refresh(refreshToken)) {
                    is TokenRefresher.Outcome.Success -> {
                        tokenStore.onRefreshed(outcome.session)
                        request.withBearer(outcome.session.accessToken)
                    }
                    TokenRefresher.Outcome.Rejected -> {
                        // A replayed refresh token within 30 s of a rotation is a plain 401. If a
                        // newer session is stored by now, use it instead of signing out.
                        val newerAccess = tokenStore.accessToken()
                        if (tokenStore.refreshToken() != refreshToken && newerAccess != null && newerAccess != sentToken) {
                            return@withLock request.withBearer(newerAccess)
                        }
                        tokenStore.onSessionInvalid()
                        // Public routes reject an invalid bearer with 401: retry them anonymously.
                        if (isPublicRoute(request)) request.newBuilder().removeHeader(AuthInterceptor.HEADER).build() else null
                    }
                    TokenRefresher.Outcome.Unavailable -> null
                }
            }
        }

    /** Routes where a bearer is optional (it only identifies the attendee). */
    private fun isPublicRoute(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.contains("/api/mobile/v1/public/") || path.endsWith("/api/mobile/v1/rsvp")
    }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header(AuthInterceptor.HEADER, "Bearer $token").build()

    private fun priorResponseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
