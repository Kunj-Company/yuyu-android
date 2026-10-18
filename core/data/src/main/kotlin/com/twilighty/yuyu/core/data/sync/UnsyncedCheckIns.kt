package com.twilighty.yuyu.core.data.sync

import com.twilighty.yuyu.core.data.repository.CheckInRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Warning hook for anything that wipes local data (sign-out, switching server):
 * offline check-ins still in the queue would be lost, because both clear the
 * Room database. Callers should check [current] first and, when it is above zero,
 * offer "Sync now" ([trySyncNow]) before letting the person continue anyway.
 */
interface UnsyncedCheckIns {
    /** Number of offline check-ins waiting to sync, across all events. */
    val count: Flow<Int>

    suspend fun current(): Int

    /** Tries to push every queued check-in now. Returns how many are still waiting. */
    suspend fun trySyncNow(): Int
}

@Singleton
class QueueUnsyncedCheckIns @Inject constructor(
    private val checkIns: CheckInRepository,
) : UnsyncedCheckIns {
    override val count: Flow<Int> = checkIns.observePendingCount()

    override suspend fun current(): Int = count.first()

    override suspend fun trySyncNow(): Int {
        checkIns.syncAllPending()
        return current()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UnsyncedCheckInsModule {
    @Binds
    abstract fun bindsUnsyncedCheckIns(impl: QueueUnsyncedCheckIns): UnsyncedCheckIns
}
