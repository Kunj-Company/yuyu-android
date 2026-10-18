package com.twilighty.yuyu.core.network

import com.twilighty.yuyu.core.network.auth.ServerUrlProvider
import com.twilighty.yuyu.core.network.auth.SessionTokenStore
import com.twilighty.yuyu.core.network.auth.TokenAuthenticator
import com.twilighty.yuyu.core.network.auth.TokenRefresher
import com.twilighty.yuyu.core.network.interceptor.AuthInterceptor
import com.twilighty.yuyu.core.network.interceptor.BaseUrlInterceptor
import com.twilighty.yuyu.core.network.model.SessionDto
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {
    private val server = MockWebServer()
    private lateinit var store: FakeTokenStore
    private lateinit var client: OkHttpClient

    private class FakeTokenStore(var access: String?, var refresh: String?) : SessionTokenStore {
        var invalidated = 0
        var refreshedWith: SessionDto? = null
        override fun accessToken() = access
        override fun refreshToken() = refresh
        override suspend fun onRefreshed(session: SessionDto) {
            refreshedWith = session
            access = session.accessToken
            refresh = session.refreshToken
        }
        override suspend fun onSessionInvalid() {
            invalidated++
            access = null
            refresh = null
        }
    }

    @Before
    fun setUp() {
        server.start()
        val base: HttpUrl = server.url("/")
        val provider = object : ServerUrlProvider {
            override fun currentServer() = base
        }
        store = FakeTokenStore("yma_old", "ymr_old")
        val baseUrl = BaseUrlInterceptor(provider)
        val bare = OkHttpClient.Builder().addInterceptor(baseUrl).readTimeout(5, TimeUnit.SECONDS).build()
        client = bare.newBuilder()
            .addInterceptor(AuthInterceptor(store, provider))
            .authenticator(TokenAuthenticator(store, TokenRefresher(bare)))
            .build()
    }

    @After
    fun tearDown() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body)
        .addHeader("Content-Type", "application/json").build()

    private val unauthenticated = """{"ok":false,"error":{"code":"UNAUTHENTICATED","message":"Sign in again."}}"""
    private val session = """{"ok":true,"data":{"accessToken":"yma_new","accessTokenExpiresAt":"2030-01-01T00:15:00Z",
        "refreshToken":"ymr_new","refreshTokenExpiresAt":"2030-03-01T00:00:00Z",
        "user":{"id":"u1","name":"Ada","email":"a@b.c","imageUrl":null,"emailVerified":true,"mfaEnabled":false}}}"""

    private fun get(path: String) = client.newCall(
        Request.Builder().url(BaseUrlInterceptor.PLACEHOLDER_BASE_URL + path).build(),
    ).execute()

    @Test
    fun refreshesOnceAndRetriesWithTheNewToken() {
        server.enqueue(json(401, unauthenticated))
        server.enqueue(json(200, session))
        server.enqueue(json(200, """{"ok":true,"data":null}"""))

        get("api/mobile/v1/me").use { assertEquals(200, it.code) }

        val first = server.takeRequest()
        assertEquals("Bearer yma_old", first.headers["Authorization"])
        val refresh = server.takeRequest()
        assertEquals("/api/mobile/v1/auth/refresh", refresh.url.encodedPath)
        assertNull(refresh.headers["Authorization"])
        val retry = server.takeRequest()
        assertEquals("Bearer yma_new", retry.headers["Authorization"])
        assertEquals("ymr_new", store.refreshedWith?.refreshToken)
        assertEquals(0, store.invalidated)
    }

    @Test
    fun rejectedRefreshSignsOutAndSurfacesThe401() {
        server.enqueue(json(401, unauthenticated))
        server.enqueue(json(401, unauthenticated))

        get("api/mobile/v1/me").use { assertEquals(401, it.code) }

        assertEquals(1, store.invalidated)
        assertNull(store.accessToken())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun publicRouteRetriesAnonymouslyAfterRejectedRefresh() {
        server.enqueue(json(401, unauthenticated))
        server.enqueue(json(401, unauthenticated))
        server.enqueue(json(200, """{"ok":true,"data":{"events":[],"nextCursor":null}}"""))

        get("api/mobile/v1/public/discover").use { assertEquals(200, it.code) }

        server.takeRequest()
        server.takeRequest()
        val anonymous = server.takeRequest()
        assertNull(anonymous.headers["Authorization"])
        assertEquals(1, store.invalidated)
    }

    @Test
    fun networkFailureDuringRefreshKeepsTheSession() {
        server.enqueue(json(401, unauthenticated))
        server.enqueue(json(503, ""))

        get("api/mobile/v1/me").use { assertEquals(401, it.code) }

        assertEquals(0, store.invalidated)
        assertEquals("yma_old", store.accessToken())
    }

    @Test
    fun signInRoutesNeverTriggerRefresh() {
        server.enqueue(json(401, """{"ok":false,"error":{"code":"MFA_REQUIRED","message":"Code?"}}"""))

        get("api/mobile/v1/auth/login").use { assertEquals(401, it.code) }

        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun bearerIsNeverSentToOtherHosts() {
        server.enqueue(json(200, "{}"))
        // Same socket, different host name: a foreign origin such as an S3 image URL.
        val base = server.url("/")
        val foreignHost = if (base.host == "127.0.0.1") "localhost" else "127.0.0.1"
        val foreign = server.url("/image.png").newBuilder().host(foreignHost).build()
        client.newCall(Request.Builder().url(foreign).build()).execute().close()
        assertNull(server.takeRequest().headers["Authorization"])
    }
}
