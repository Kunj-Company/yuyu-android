package com.twilighty.yuyu.feature.broadcasts.draft

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.twilighty.yuyu.core.data.session.LocalDataCleaner
import com.twilighty.yuyu.core.network.model.BroadcastAttendance
import com.twilighty.yuyu.core.network.model.BroadcastDesign
import com.twilighty.yuyu.feature.broadcasts.editor.EmailHtml
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An unsent email for one event. [bodyHtml] is the editor's serialised HTML subset. */
@Serializable
data class BroadcastDraft(
    val subject: String = "",
    val bodyHtml: String = "",
    val statuses: List<String> = listOf("CONFIRMED"),
    val attendance: String = BroadcastAttendance.ANY,
    val design: String = BroadcastDesign.CLASSIC,
    val templateId: String? = null,
) {
    /** No subject and no visible body text. */
    fun isBlank(): Boolean = subject.isBlank() && EmailHtml.isBlank(bodyHtml)

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(value: String?): BroadcastDraft? =
            value?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** Per-event draft storage, so a backgrounded or killed app doesn't lose an email. */
interface BroadcastDraftStore {
    suspend fun load(orgSlug: String, eventId: String): BroadcastDraft?
    suspend fun save(orgSlug: String, eventId: String, draft: BroadcastDraft)
    suspend fun clear(orgSlug: String, eventId: String)
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BroadcastDraftPrefs

class DataStoreBroadcastDraftStore @Inject constructor(
    @param:BroadcastDraftPrefs private val dataStore: DataStore<Preferences>,
) : BroadcastDraftStore {
    private fun key(orgSlug: String, eventId: String) = stringPreferencesKey("draft:$orgSlug/$eventId")

    override suspend fun load(orgSlug: String, eventId: String): BroadcastDraft? =
        try {
            BroadcastDraft.decode(dataStore.data.first()[key(orgSlug, eventId)])
        } catch (e: IOException) {
            null
        }

    override suspend fun save(orgSlug: String, eventId: String, draft: BroadcastDraft) {
        try {
            dataStore.edit { it[key(orgSlug, eventId)] = draft.encode() }
        } catch (_: IOException) {
            // Autosave is best effort; the in-memory draft and SavedStateHandle still hold it.
        }
    }

    override suspend fun clear(orgSlug: String, eventId: String) {
        try {
            dataStore.edit { it.remove(key(orgSlug, eventId)) }
        } catch (_: IOException) {
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object BroadcastDraftModule {
    @Provides
    @Singleton
    @BroadcastDraftPrefs
    fun providesDraftPrefs(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("yuyu_broadcast_drafts") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BroadcastDraftBindsModule {
    @Binds
    abstract fun bindsDraftStore(impl: DataStoreBroadcastDraftStore): BroadcastDraftStore

    @Binds
    @IntoSet
    abstract fun bindsDraftCleaner(impl: BroadcastDraftCleaner): LocalDataCleaner
}

/** Drafts can name attendees and event details, so they never survive a sign-out on a shared phone. */
class BroadcastDraftCleaner @Inject constructor(
    @param:BroadcastDraftPrefs private val dataStore: DataStore<Preferences>,
) : LocalDataCleaner {
    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
