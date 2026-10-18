package com.twilighty.yuyu.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingCheckInDao {
    @Query("SELECT * FROM pending_check_ins WHERE eventId = :eventId ORDER BY createdAt")
    fun observeForEvent(eventId: String): Flow<List<PendingCheckInEntity>>

    @Query("SELECT COUNT(*) FROM pending_check_ins")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM pending_check_ins ORDER BY createdAt")
    suspend fun all(): List<PendingCheckInEntity>

    @Query("SELECT * FROM pending_check_ins WHERE eventId = :eventId ORDER BY createdAt")
    suspend fun forEvent(eventId: String): List<PendingCheckInEntity>

    /** Keeps the first offline scan if the same guest is scanned twice. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(checkIn: PendingCheckInEntity): Long

    @Query("DELETE FROM pending_check_ins WHERE eventId = :eventId AND rsvpId IN (:rsvpIds)")
    suspend fun remove(eventId: String, rsvpIds: List<String>)

    @Query(
        "UPDATE pending_check_ins SET attempts = attempts + 1, lastError = :error " +
            "WHERE eventId = :eventId AND rsvpId = :rsvpId",
    )
    suspend fun markFailed(eventId: String, rsvpId: String, error: String?)

    @Query("DELETE FROM pending_check_ins")
    suspend fun clear()
}
