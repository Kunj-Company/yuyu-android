package com.twilighty.yuyu.core.data.repository

import android.os.Build
import com.twilighty.yuyu.core.common.result.AppError
import com.twilighty.yuyu.core.common.result.AppResult
import com.twilighty.yuyu.core.common.result.map
import com.twilighty.yuyu.core.common.result.onSuccess
import com.twilighty.yuyu.core.data.server.ServerStore
import com.twilighty.yuyu.core.data.session.SessionStore
import com.twilighty.yuyu.core.data.session.StoredSession
import com.twilighty.yuyu.core.database.YuyuDatabase
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.api.YuyuMobileApi
import com.twilighty.yuyu.core.network.di.BareClient
import com.twilighty.yuyu.core.network.error.ApiErrorParser
import com.twilighty.yuyu.core.network.error.apiCall
import com.twilighty.yuyu.core.network.error.apiCallUnit
import com.twilighty.yuyu.core.network.model.ApiEnvelope
import com.twilighty.yuyu.core.network.model.AuthConfigDto
import com.twilighty.yuyu.core.network.model.GoogleLoginRequest
import com.twilighty.yuyu.core.network.model.HealthDto
import com.twilighty.yuyu.core.network.model.LoginRequest
import com.twilighty.yuyu.core.network.model.UserDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where the app is in the server → sign-in flow. */
sealed interface SessionState {
    data object Loading : SessionState
    data object NoServer : SessionState
    data class SignedOut(val server: HttpUrl) : SessionState
    data class SignedIn(val server: HttpUrl, val user: UserDto) : SessionState
}

/** A verified Yuyu server and its public sign-in configuration. */
data class ServerInfo(val url: HttpUrl, val config: AuthConfigDto)

interface AuthRepository {
    val sessionState: Flow<SessionState>

    /** Server rejected the session (refresh failed); the session is already cleared. */
    val forcedSignOuts: SharedFlow<Unit>

    /** Loads the stored server and session from disk. Call once at startup. */
    suspend fun initialize()

    /** Checks `GET /api/health` and `GET /api/mobile/v1/auth/config` on [url] without saving it. */
    suspend fun probeServer(url: HttpUrl): AppResult<ServerInfo>

    /** Saves a verified server. Switching to a different server signs out and wipes local data. */
    suspend fun selectServer(url: HttpUrl)

    /** Forget the server (and the session). */
    suspend fun clearServer()

    suspend fun authConfig(): AppResult<AuthConfigDto>

    /** [totp] is an authenticator or recovery code; send it only after MFA_REQUIRED. */
    suspend fun signIn(email: String, password: String, totp: String? = null): AppResult<UserDto>

    suspend fun signInWithGoogle(idToken: String): AppResult<UserDto>

    /** Revokes this device's session (best effort) and clears local data. */
    suspend fun signOut()

    /** Revokes every web and mobile session, then clears local data. */
    suspend fun signOutEverywhere(): AppResult<Unit>

    /** Absolute website URL on the current server, e.g. `webUrl("account/security")`. */
    fun webUrl(path: String): String?
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: YuyuMobileApi,
    private val sessionStore: SessionStore,
    private val serverStore: ServerStore,
    private val database: YuyuDatabase,
    @param:BareClient private val bareClient: OkHttpClient,
) : AuthRepository {

    private val initialized = MutableStateFlow(false)

    override val sessionState: Flow<SessionState> =
        combine(initialized, serverStore.server, sessionStore.session) { ready, server, session ->
            when {
                !ready -> SessionState.Loading
                server == null -> SessionState.NoServer
                session == null -> SessionState.SignedOut(server)
                else -> SessionState.SignedIn(server, session.user)
            }
        }.distinctUntilChanged()

    override val forcedSignOuts: SharedFlow<Unit> = sessionStore.signedOutEvents

    override suspend fun initialize() {
        serverStore.load()
        sessionStore.load()
        initialized.value = true
    }

    override suspend fun probeServer(url: HttpUrl): AppResult<ServerInfo> = withContext(Dispatchers.IO) {
        try {
            val health = getJson(url, "api/health")
            if (health.second !in 200..299) {
                return@withContext AppResult.Failure(AppError.NotFound("We reached that address, but it isn't a Yuyu server."))
            }
            val healthy = runCatching { NetworkJson.decodeFromString(HealthDto.serializer(), health.first).ok }.getOrDefault(false)
            if (!healthy) {
                return@withContext AppResult.Failure(AppError.NotFound("We reached that address, but it isn't a Yuyu server."))
            }
            val config = getJson(url, YuyuMobileApi.PREFIX + "/auth/config")
            if (config.second == 404) {
                return@withContext AppResult.Failure(
                    AppError.NotFound("This Yuyu server is too old for the app. Ask its admin to update it."),
                )
            }
            if (config.second !in 200..299) {
                return@withContext AppResult.Failure(ApiErrorParser.parse(config.second, config.first))
            }
            val envelope = NetworkJson.decodeFromString(ApiEnvelope.serializer(AuthConfigDto.serializer()), config.first)
            val data = envelope.data ?: return@withContext AppResult.Failure(AppError.Unknown())
            AppResult.Success(ServerInfo(url, data))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network("We couldn't reach that server. Check the address and your connection."))
        } catch (e: SerializationException) {
            AppResult.Failure(AppError.NotFound("We reached that address, but it isn't a Yuyu server."))
        }
    }

    private fun getJson(server: HttpUrl, path: String): Pair<String, Int> {
        val url = server.newBuilder().encodedPath(server.encodedPath.trimEnd('/') + "/" + path).build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return bareClient.newCall(request).execute().use { it.body.string() to it.code }
    }

    override suspend fun selectServer(url: HttpUrl) {
        if (serverStore.server.value != url) {
            clearLocalData()
            serverStore.setServer(url)
        }
    }

    override suspend fun clearServer() {
        clearLocalData()
        serverStore.clear()
    }

    override suspend fun authConfig(): AppResult<AuthConfigDto> = apiCall { api.authConfig() }

    override suspend fun signIn(email: String, password: String, totp: String?): AppResult<UserDto> =
        apiCall { api.login(LoginRequest(email.trim(), password, totp?.trim()?.ifEmpty { null }, deviceName())) }
            .onSuccess { sessionStore.save(StoredSession.from(it)) }
            .map { it.user }

    override suspend fun signInWithGoogle(idToken: String): AppResult<UserDto> =
        apiCall { api.loginWithGoogle(GoogleLoginRequest(idToken, deviceName())) }
            .onSuccess { sessionStore.save(StoredSession.from(it)) }
            .map { it.user }

    override suspend fun signOut() {
        if (sessionStore.accessToken() != null) apiCallUnit { api.logout() }
        clearLocalData()
    }

    override suspend fun signOutEverywhere(): AppResult<Unit> =
        apiCallUnit { api.logoutAll() }.onSuccess { clearLocalData() }

    override fun webUrl(path: String): String? = serverStore.webUrl(path)

    private suspend fun clearLocalData() {
        sessionStore.clear()
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.distinct()
            .joinToString(" ").take(80).ifBlank { "Android" }
}
