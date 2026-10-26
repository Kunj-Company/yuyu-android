package com.twilighty.yuyu.core.data.server

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twilighty.yuyu.core.data.di.SettingsPrefs
import com.twilighty.yuyu.core.network.auth.ServerUrlProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The chosen Yuyu server (one at a time; switching signs out). */
@Singleton
class ServerStore @Inject constructor(
    @param:SettingsPrefs private val dataStore: DataStore<Preferences>,
) : ServerUrlProvider {
    private val state = MutableStateFlow<HttpUrl?>(null)

    val server: StateFlow<HttpUrl?> = state.asStateFlow()

    suspend fun load(): HttpUrl? {
        state.value = runCatching { dataStore.data.first()[KEY_SERVER] }.getOrNull()?.toHttpUrlOrNull()
        return state.value
    }

    suspend fun setServer(url: HttpUrl) {
        dataStore.edit { it[KEY_SERVER] = url.toString() }
        state.value = url
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_SERVER) }
        state.value = null
    }

    override fun currentServer(): HttpUrl? = state.value

    /** Absolute website URL for a path on the current server (sign-up, reset, security settings). */
    fun webUrl(path: String): String? = state.value?.newBuilder()
        ?.encodedPath(state.value!!.encodedPath.trimEnd('/') + "/" + path.trimStart('/'))
        ?.build()?.toString()

    private companion object {
        val KEY_SERVER = stringPreferencesKey("server_base_url")
    }
}
