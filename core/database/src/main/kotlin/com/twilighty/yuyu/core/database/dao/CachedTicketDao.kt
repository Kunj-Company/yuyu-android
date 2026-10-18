package com.twilighty.yuyu.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.twilighty.yuyu.core.database.entity.CachedTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTicketDao {
    @Query("SELECT * FROM cached_tickets ORDER BY startDateTime ASC")
    fun observeAll(): Flow<List<CachedTicketEntity>>

    @Query("SELECT * FROM cached_tickets WHERE rsvpId = :rsvpId")
    suspend fun get(rsvpId: String): CachedTicketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tickets: List<CachedTicketEntity>)

    @Query("DELETE FROM cached_tickets")
    suspend fun clear()

    /** Replace the whole cache with the server's latest list. */
    @Transaction
    suspend fun replaceAll(tickets: List<CachedTicketEntity>) {
        clear()
        upsertAll(tickets)
    }
}
