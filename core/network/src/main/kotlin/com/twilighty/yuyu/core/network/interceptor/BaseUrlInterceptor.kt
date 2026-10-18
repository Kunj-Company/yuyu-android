package com.twilighty.yuyu.core.network.interceptor

import com.twilighty.yuyu.core.network.auth.ServerUrlProvider
import java.io.IOException
import javax.inject.Inject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Thrown when an API call is made before a server is chosen. */
class NoServerConfiguredException : IOException("No Yuyu server configured")

/**
 * Retrofit is built against [PLACEHOLDER_BASE_URL]; this interceptor rewrites
 * those requests to the server chosen at runtime (keeping any base path, e.g.
 * `https://example.org/yuyu/`). Absolute URLs on other hosts (images on S3)
 * pass through untouched.
 */
class BaseUrlInterceptor @Inject constructor(
    private val serverUrlProvider: ServerUrlProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PLACEHOLDER_HOST) return chain.proceed(request)
        val server = serverUrlProvider.currentServer() ?: throw NoServerConfiguredException()
        return chain.proceed(request.newBuilder().url(rewrite(server, request.url)).build())
    }

    companion object {
        const val PLACEHOLDER_HOST = "yuyu.invalid"
        const val PLACEHOLDER_BASE_URL = "https://$PLACEHOLDER_HOST/"

        fun rewrite(server: HttpUrl, url: HttpUrl): HttpUrl {
            val basePath = server.encodedPath.trimEnd('/')
            return server.newBuilder()
                .encodedPath(basePath + url.encodedPath)
                .encodedQuery(url.encodedQuery)
                .build()
        }

        /** Resolves a relative API path (e.g. a certificate path) against a server. */
        fun resolve(server: HttpUrl, relativePath: String): HttpUrl =
            rewrite(server, (PLACEHOLDER_BASE_URL + relativePath.trimStart('/')).toHttpUrl())
    }
}
