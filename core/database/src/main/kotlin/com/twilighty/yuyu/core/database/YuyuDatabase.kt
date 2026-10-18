package com.twilighty.yuyu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.twilighty.yuyu.core.database.dao.CachedTicketDao
import com.twilighty.yuyu.core.database.dao.PendingCheckInDao
import com.twilighty.yuyu.core.database.dao.RosterDao
import com.twilighty.yuyu.core.database.entity.CachedTicketEntity
import com.twilighty.yuyu.core.database.entity.PendingCheckInEntity
import com.twilighty.yuyu.core.database.entity.RosterRowEntity

/**
 * Local cache only: the server stays the source of truth. Schemas are exported
 * to `core/database/schemas/`; bump [version] with an explicit migration (or
 * `@AutoMigration`) whenever an entity changes. The whole database is wiped on
 * sign-out and when switching servers.
 */
@Database(
    entities = [CachedTicketEntity::class, RosterRowEntity::class, PendingCheckInEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class YuyuDatabase : RoomDatabase() {
    abstract fun cachedTicketDao(): CachedTicketDao
    abstract fun rosterDao(): RosterDao
    abstract fun pendingCheckInDao(): PendingCheckInDao

    companion object {
        const val NAME = "yuyu.db"
    }
}
