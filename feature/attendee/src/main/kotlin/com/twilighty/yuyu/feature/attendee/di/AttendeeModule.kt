package com.twilighty.yuyu.feature.attendee.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.twilighty.yuyu.feature.attendee.common.AndroidCertificateStore
import com.twilighty.yuyu.feature.attendee.common.CertificateStore
import com.twilighty.yuyu.feature.attendee.reminders.AndroidNotificationPermission
import com.twilighty.yuyu.feature.attendee.reminders.DataStoreReminderPreferences
import com.twilighty.yuyu.feature.attendee.reminders.NotificationPermission
import com.twilighty.yuyu.feature.attendee.reminders.ReminderPreferences
import com.twilighty.yuyu.feature.attendee.reminders.ReminderWork
import com.twilighty.yuyu.feature.attendee.reminders.WorkManagerReminderWork
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** The attendee module's own (non-secret) preferences. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AttendeePrefs

@Module
@InstallIn(SingletonComponent::class)
object AttendeeProvidesModule {
    @Provides
    @Singleton
    @AttendeePrefs
    fun providesAttendeePrefs(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("yuyu_attendee") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AttendeeBindsModule {
    @Binds abstract fun bindsReminderWork(impl: WorkManagerReminderWork): ReminderWork
    @Binds abstract fun bindsReminderPreferences(impl: DataStoreReminderPreferences): ReminderPreferences
    @Binds abstract fun bindsNotificationPermission(impl: AndroidNotificationPermission): NotificationPermission
    @Binds abstract fun bindsCertificateStore(impl: AndroidCertificateStore): CertificateStore
}
