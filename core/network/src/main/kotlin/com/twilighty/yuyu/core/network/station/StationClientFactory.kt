package com.twilighty.yuyu.core.network.station

import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.api.StationApi
import com.twilighty.yuyu.core.network.di.BareClient
import com.twilighty.yuyu.core.network.interceptor.BaseUrlInterceptor
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds an isolated client per door station. The station's signed proof
 * cookie (set by `action: "unlock"`) lives only in that client's in-memory
 * [StationCookieJar]: never on disk, never shared with the user's session,
 * gone when the station screen is closed or [StationClient.lock] is called.
 * No bearer token is attached.
 */
@Singleton
class StationClientFactory @Inject constructor(
    @param:BareClient private val bareClient: OkHttpClient,
) {
    fun create(): StationClient {
        val jar = StationCookieJar()
        val client = bareClient.newBuilder().cookieJar(jar).build()
        val api = Retrofit.Builder()
            .baseUrl(BaseUrlInterceptor.PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(StationApi::class.java)
        return StationClient(api, jar)
    }
}

class StationClient internal constructor(
    val api: StationApi,
    private val cookieJar: StationCookieJar,
) {
    val isUnlocked: Boolean get() = cookieJar.hasCookies()

    /** Forget the station proof (Lock station). */
    fun lock() = cookieJar.clear()
}

/** Thread-safe, memory-only cookie jar honouring cookie expiry and domain/path matching. */
class StationCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies += incoming
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun hasCookies(): Boolean = cookies.any { it.expiresAt > System.currentTimeMillis() }

    @Synchronized
    fun clear() = cookies.clear()
}
