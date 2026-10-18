package com.twilighty.yuyu.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.twilighty.yuyu.core.database.entity.RosterRowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RosterDao {
    @Query("SELECT * FROM roster_rows WHERE eventId = :eventId ORDER BY displayName COLLATE NOCASE")
    fun observeRoster(eventId: String): Flow<List<RosterRowEntity>>

    @Query("SELECT COUNT(*) FROM roster_rows WHERE eventId = :eventId")
    suspend fun count(eventId: String): Int

    @Query("SELECT MAX(generatedAt) FROM roster_rows WHERE eventId = :eventId")
    suspend fun generatedAt(eventId: String): String?

    @Query("SELECT * FROM roster_rows WHERE eventId = :eventId AND ticketTokenSha256 = :tokenHash LIMIT 1")
    suspend fun findByTokenHash(eventId: String, tokenHash: String): RosterRowEntity?

    @Query("SELECT * FROM roster_rows WHERE eventId = :eventId AND rsvpId = :rsvpId")
    suspend fun find(eventId: String, rsvpId: String): RosterRowEntity?

    @Query(
        "SELECT * FROM roster_rows WHERE eventId = :eventId AND (displayName LIKE '%' || :query || '%' " +
            "OR email LIKE '%' || :query || '%') ORDER BY displayName COLLATE NOCASE LIMIT :limit",
    )
    suspend fun search(eventId: String, query: String, limit: Int = 20): List<RosterRowEntity>

    @Query("UPDATE roster_rows SET checkedInAt = :checkedInAt WHERE eventId = :eventId AND rsvpId = :rsvpId")
    suspend fun markCheckedIn(eventId: String, rsvpId: String, checkedInAt: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<RosterRowEntity>)

    @Query("DELETE FROM roster_rows WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)

    @Query("DELETE FROM roster_rows")
    suspend fun clear()

    @Transaction
    suspend fun replaceRoster(eventId: String, rows: List<RosterRowEntity>) {
        deleteForEvent(eventId)
        insertAll(rows)
    }
}
