package com.example.recordvoice.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val mimeType: String,
    val duration: Int,
    val timestamp: Long,
    val dateStr: String,
    val synced: Boolean = false
)
