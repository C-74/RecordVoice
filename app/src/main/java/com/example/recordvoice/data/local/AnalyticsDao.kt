package com.example.recordvoice.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalyticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AnalyticsEventEntity): Long

    @Query("SELECT * FROM analytics_events WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedEvents(): List<AnalyticsEventEntity>

    @Query("UPDATE analytics_events SET synced = 1 WHERE id = :id")
    suspend fun markEventSynced(id: Long)

    @Query("DELETE FROM analytics_events WHERE synced = 1")
    suspend fun deleteSyncedEvents()
}
