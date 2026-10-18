package com.twilighty.yuyu.core.network.interceptor

import com.twilighty.yuyu.core.network.auth.ServerUrlProvider
import com.twilighty.yuyu.core.network.auth.SessionTokenStore
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <accessToken>` — but only to the chosen Yuyu
 * server's origin (scheme, host and port). Image URLs on other hosts (S3/CDN)
 * never receive the token. Sign-in and refresh calls are sent without it.
 */
class AuthInterceptor @Inject constructor(
    private val tokenStore: SessionTokenStore,
    private val serverUrlProvider: ServerUrlProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER) != null || !isServerRequest(request) || isUnauthenticatedRoute(request)) {
            return chain.proceed(request)
        }
        val token = tokenStore.accessToken() ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, "Bearer $token").build())
    }

    private fun isServerRequest(request: Request): Boolean {
        val server = serverUrlProvider.currentServer() ?: return false
        val url = request.url
        return url.scheme == server.scheme && url.host == server.host && url.port == server.port
    }

    companion object {
        const val HEADER = "Authorization"
        private val UNAUTHENTICATED_SUFFIXES = listOf("/auth/login", "/auth/google", "/auth/refresh", "/auth/config")

        fun isUnauthenticatedRoute(request: Request): Boolean {
            val path = request.url.encodedPath
            return path.contains("/api/mobile/v1/") && UNAUTHENTICATED_SUFFIXES.any { path.endsWith(it) }
        }
    }
}
