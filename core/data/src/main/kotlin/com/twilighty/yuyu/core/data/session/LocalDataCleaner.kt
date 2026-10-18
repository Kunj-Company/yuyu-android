package com.twilighty.yuyu.core.data.session

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Feature-owned local data that must not outlive a sign-out or a server switch
 * (drafts, remembered selections, scheduled reminders). Features contribute one
 * with `@Binds @IntoSet`; [com.twilighty.yuyu.core.data.repository.AuthRepository]
 * runs every cleaner whenever it clears local data.
 */
interface LocalDataCleaner {
    suspend fun clear()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalDataCleanerModule {
    /** Declares the set so it can be injected even when no feature contributes. */
    @Multibinds
    abstract fun localDataCleaners(): Set<LocalDataCleaner>
}
