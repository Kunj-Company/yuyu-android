package com.twilighty.yuyu.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twilighty.yuyu.core.data.crypto.SecretCipher
import com.twilighty.yuyu.core.data.crypto.decryptFromBase64
import com.twilighty.yuyu.core.data.crypto.encryptToBase64
import com.twilighty.yuyu.core.data.di.SessionPrefs
import com.twilighty.yuyu.core.network.NetworkJson
import com.twilighty.yuyu.core.network.auth.SessionTokenStore
import com.twilighty.yuyu.core.network.model.SessionDto
import com.twilighty.yuyu.core.network.model.UserDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** The persisted session. Tokens are secrets: never log this object. */
@Serializable
data class StoredSession(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: UserDto,
) {
    override fun toString(): String = "StoredSession(user=${user.id})"

    companion object {
        fun from(dto: SessionDto) = StoredSession(
            accessToken = dto.accessToken,
            accessTokenExpiresAt = dto.accessTokenExpiresAt,
            refreshToken = dto.refreshToken,
            refreshTokenExpiresAt = dto.refreshTokenExpiresAt,
            user = dto.user,
        )
    }
}

/**
 * Encrypted session persistence: the whole session JSON is sealed with
 * AES-GCM ([SecretCipher], Android Keystore key) and the ciphertext is kept in
 * a Preferences DataStore that is excluded from backups. Tokens are mirrored
 * in memory so OkHttp interceptors can read them without blocking.
 */
@Singleton
class SessionStore @Inject constructor(
    @param:SessionPrefs private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : SessionTokenStore {
    private val state = MutableStateFlow<StoredSession?>(null)
    private val loadMutex = Mutex()

    @Volatile
    private var loaded = false

    private val signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val session: StateFlow<StoredSession?> = state.asStateFlow()

    /** Emits when the server rejected the session (refresh failed) and it was cleared. */
    val signedOutEvents: SharedFlow<Unit> = signedOut.asSharedFlow()

    /** Reads the encrypted session from disk once. Call before the first API request. */
    suspend fun load(): StoredSession? = loadMutex.withLock {
        if (!loaded) {
            // An unreadable store means "signed out", never a crash at launch.
            val encoded = runCatching { dataStore.data.first()[KEY_SESSION] }.getOrNull()
            state.value = encoded
                ?.let { cipher.decryptFromBase64(it) }
                ?.let { runCatching { NetworkJson.decodeFromString(StoredSession.serializer(), it) }.getOrNull() }
            loaded = true
        }
        state.value
    }

    /**
     * Keeps [session] in memory, then persists it. If the Keystore or disk fails
     * the session still works until the app closes (the user signs in again
     * next time) instead of crashing, e.g. inside a token refresh.
     */
    suspend fun save(session: StoredSession) {
        state.value = session
        loaded = true
        runCatching {
            val encrypted = cipher.encryptToBase64(NetworkJson.encodeToString(StoredSession.serializer(), session))
            dataStore.edit { it[KEY_SESSION] = encrypted }
        }
    }

    suspend fun updateUser(user: UserDto) {
        state.value?.let { save(it.copy(user = user)) }
    }

    suspend fun clear() {
        runCatching { dataStore.edit { it.remove(KEY_SESSION) } }
        state.value = null
        loaded = true
    }

    override fun accessToken(): String? = state.value?.accessToken

    override fun refreshToken(): String? = state.value?.refreshToken

    override suspend fun onRefreshed(session: SessionDto) = save(StoredSession.from(session))

    override suspend fun onSessionInvalid() {
        clear()
        signedOut.tryEmit(Unit)
    }

    private companion object {
        val KEY_SESSION = stringPreferencesKey("session_v1")
    }
}
