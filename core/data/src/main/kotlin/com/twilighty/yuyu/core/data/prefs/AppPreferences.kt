package com.twilighty.yuyu.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twilighty.yuyu.core.common.AppearanceMode
import com.twilighty.yuyu.core.data.di.SettingsPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Device-level, non-secret preferences. */
@Singleton
class AppPreferences @Inject constructor(
    @param:SettingsPrefs private val dataStore: DataStore<Preferences>,
) {
    val appearance: Flow<AppearanceMode> = dataStore.data.map { prefs ->
        prefs[KEY_APPEARANCE]?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() } ?: AppearanceMode.System
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        dataStore.edit { it[KEY_APPEARANCE] = mode.name }
    }

    private companion object {
        val KEY_APPEARANCE = stringPreferencesKey("appearance")
    }
}
