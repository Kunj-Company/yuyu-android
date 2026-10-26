package com.twilighty.yuyu.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.twilighty.yuyu.core.database.entity.CachedScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedScheduleDao {
    @Query("SELECT * FROM cached_schedules WHERE orgSlug = :orgSlug AND eventId = :eventId")
    fun observe(orgSlug: String, eventId: String): Flow<CachedScheduleEntity?>

    @Query("SELECT * FROM cached_schedules WHERE orgSlug = :orgSlug AND eventId = :eventId")
    suspend fun get(orgSlug: String, eventId: String): CachedScheduleEntity?

    /** Every saved programme, most recently synced first. */
    @Query("SELECT * FROM cached_schedules ORDER BY syncedAt DESC")
    fun observeAll(): Flow<List<CachedScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: CachedScheduleEntity)

    @Query("DELETE FROM cached_schedules WHERE orgSlug = :orgSlug AND eventId = :eventId")
    suspend fun delete(orgSlug: String, eventId: String)
}
