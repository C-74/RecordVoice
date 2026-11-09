package com.example.recordvoice.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordingsFlow(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    suspend fun getAllRecordingsList(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedRecordings(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Query("UPDATE recordings SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: Long, newTitle: String)

    @Query("UPDATE recordings SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
