package com.twilighty.yuyu.feature.organiser.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twilighty.yuyu.core.data.di.SettingsPrefs
import com.twilighty.yuyu.core.data.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Device preference: the organisation last opened in the Organise tab (a slug, not a secret). */
interface OrganiserPreferences {
    suspend fun lastOrgSlug(): String?
    suspend fun setLastOrgSlug(slug: String)
}

@Singleton
class DataStoreOrganiserPreferences @Inject constructor(
    @param:SettingsPrefs private val dataStore: DataStore<Preferences>,
) : OrganiserPreferences {
    private val lastOrg: Flow<String?> = dataStore.data.map { it[KEY_LAST_ORG] }

    override suspend fun lastOrgSlug(): String? = lastOrg.first()

    override suspend fun setLastOrgSlug(slug: String) {
        dataStore.edit { it[KEY_LAST_ORG] = slug }
    }

    private companion object {
        val KEY_LAST_ORG = stringPreferencesKey("organiser_last_org")
    }
}

/** Absolute website links on the signed-in server (null when no server is chosen). */
interface WebLinks {
    fun url(path: String): String?

    fun orgDashboard(orgSlug: String): String? = url("dashboard/$orgSlug")
    fun eventDashboard(orgSlug: String, eventId: String): String? = url("dashboard/$orgSlug/event/$eventId")
}

class AuthWebLinks @Inject constructor(private val auth: AuthRepository) : WebLinks {
    override fun url(path: String): String? = auth.webUrl(path)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OrganiserModule {
    @Binds abstract fun bindsOrganiserPreferences(impl: DataStoreOrganiserPreferences): OrganiserPreferences
    @Binds abstract fun bindsWebLinks(impl: AuthWebLinks): WebLinks
}
