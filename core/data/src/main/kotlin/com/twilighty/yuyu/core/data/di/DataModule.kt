package com.twilighty.yuyu.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.twilighty.yuyu.core.data.crypto.KeystoreSecretCipher
import com.twilighty.yuyu.core.data.crypto.SecretCipher
import com.twilighty.yuyu.core.data.repository.AuthRepository
import com.twilighty.yuyu.core.data.repository.AuthRepositoryImpl
import com.twilighty.yuyu.core.data.repository.BroadcastsRepository
import com.twilighty.yuyu.core.data.repository.BroadcastsRepositoryImpl
import com.twilighty.yuyu.core.data.repository.CheckInRepository
import com.twilighty.yuyu.core.data.repository.CheckInRepositoryImpl
import com.twilighty.yuyu.core.data.repository.DayOfRepository
import com.twilighty.yuyu.core.data.repository.DayOfRepositoryImpl
import com.twilighty.yuyu.core.data.repository.GuestsRepository
import com.twilighty.yuyu.core.data.repository.GuestsRepositoryImpl
import com.twilighty.yuyu.core.data.repository.MeRepository
import com.twilighty.yuyu.core.data.repository.MeRepositoryImpl
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepository
import com.twilighty.yuyu.core.data.repository.OrganiserEventsRepositoryImpl
import com.twilighty.yuyu.core.data.repository.PublicEventsRepository
import com.twilighty.yuyu.core.data.repository.PublicEventsRepositoryImpl
import com.twilighty.yuyu.core.data.repository.StationRepository
import com.twilighty.yuyu.core.data.repository.StationRepositoryImpl
import com.twilighty.yuyu.core.data.server.ServerStore
import com.twilighty.yuyu.core.data.session.SessionStore
import com.twilighty.yuyu.core.network.auth.ServerUrlProvider
import com.twilighty.yuyu.core.network.auth.SessionTokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Encrypted-session DataStore (ciphertext only). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SessionPrefs

/** Server URL and appearance. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SettingsPrefs

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    @SessionPrefs
    fun providesSessionPrefs(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("yuyu_session") }

    @Provides
    @Singleton
    @SettingsPrefs
    fun providesSettingsPrefs(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("yuyu_settings") }

    @Provides
    fun providesWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds abstract fun bindsSecretCipher(impl: KeystoreSecretCipher): SecretCipher
    @Binds abstract fun bindsSessionTokenStore(impl: SessionStore): SessionTokenStore
    @Binds abstract fun bindsServerUrlProvider(impl: ServerStore): ServerUrlProvider

    @Binds abstract fun bindsAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindsMeRepository(impl: MeRepositoryImpl): MeRepository
    @Binds abstract fun bindsPublicEventsRepository(impl: PublicEventsRepositoryImpl): PublicEventsRepository
    @Binds abstract fun bindsOrganiserEventsRepository(impl: OrganiserEventsRepositoryImpl): OrganiserEventsRepository
    @Binds abstract fun bindsGuestsRepository(impl: GuestsRepositoryImpl): GuestsRepository
    @Binds abstract fun bindsCheckInRepository(impl: CheckInRepositoryImpl): CheckInRepository
    @Binds abstract fun bindsStationRepository(impl: StationRepositoryImpl): StationRepository
    @Binds abstract fun bindsBroadcastsRepository(impl: BroadcastsRepositoryImpl): BroadcastsRepository
    @Binds abstract fun bindsDayOfRepository(impl: DayOfRepositoryImpl): DayOfRepository
}
