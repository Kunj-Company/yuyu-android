package com.twilighty.yuyu.core.data.sync

import com.twilighty.yuyu.core.data.repository.CheckInRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Warning hook for anything that wipes local data (sign-out, switching server):
 * offline check-ins still in the queue would be lost, because both clear the
 * Room database. Callers should check [count] first and, when it is above zero,
 * offer "Sync now" ([trySyncNow]) before letting the person continue anyway.
 */
@Singleton
class UnsyncedCheckIns @Inject constructor(
    private val checkIns: CheckInRepository,
) {
    /** Number of offline check-ins waiting to sync, across all events. */
    val count: Flow<Int> = checkIns.observePendingCount()

    suspend fun current(): Int = count.first()

    /** Tries to push every queued check-in now. Returns how many are still waiting. */
    suspend fun trySyncNow(): Int {
        checkIns.syncAllPending()
        return current()
    }
}
