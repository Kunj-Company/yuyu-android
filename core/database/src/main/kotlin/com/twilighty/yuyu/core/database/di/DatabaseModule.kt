package com.twilighty.yuyu.core.database.di

import android.content.Context
import androidx.room.Room
import com.twilighty.yuyu.core.database.YuyuDatabase
import com.twilighty.yuyu.core.database.dao.CachedScheduleDao
import com.twilighty.yuyu.core.database.dao.CachedTicketDao
import com.twilighty.yuyu.core.database.dao.PendingCheckInDao
import com.twilighty.yuyu.core.database.dao.RosterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): YuyuDatabase =
        Room.databaseBuilder(context, YuyuDatabase::class.java, YuyuDatabase.NAME).build()

    @Provides
    fun providesCachedTicketDao(db: YuyuDatabase): CachedTicketDao = db.cachedTicketDao()

    @Provides
    fun providesRosterDao(db: YuyuDatabase): RosterDao = db.rosterDao()

    @Provides
    fun providesPendingCheckInDao(db: YuyuDatabase): PendingCheckInDao = db.pendingCheckInDao()

    @Provides
    fun providesCachedScheduleDao(db: YuyuDatabase): CachedScheduleDao = db.cachedScheduleDao()
}
