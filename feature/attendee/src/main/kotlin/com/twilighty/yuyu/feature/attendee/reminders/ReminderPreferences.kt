package com.twilighty.yuyu.feature.attendee.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.twilighty.yuyu.feature.attendee.di.AttendeePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** "Remind me before events" and whether the notification permission rationale was shown. */
interface ReminderPreferences {
    val remindersEnabled: Flow<Boolean>
    val permissionPromptShown: Flow<Boolean>
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setPermissionPromptShown()
}

@Singleton
class DataStoreReminderPreferences @Inject constructor(
    @param:AttendeePrefs private val dataStore: DataStore<Preferences>,
) : ReminderPreferences {
    override val remindersEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLED] ?: true }
    override val permissionPromptShown: Flow<Boolean> = dataStore.data.map { it[KEY_PROMPTED] ?: false }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    override suspend fun setPermissionPromptShown() {
        dataStore.edit { it[KEY_PROMPTED] = true }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("reminders_enabled")
        val KEY_PROMPTED = booleanPreferencesKey("notification_prompt_shown")
    }
}

/** Whether posting notifications needs a runtime grant that is missing (API 33+). */
interface NotificationPermission {
    fun needsRequest(): Boolean
}

class AndroidNotificationPermission @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationPermission {
    override fun needsRequest(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
