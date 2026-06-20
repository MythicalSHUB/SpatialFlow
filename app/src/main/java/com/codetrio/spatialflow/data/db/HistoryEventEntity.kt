package com.codetrio.spatialflow.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_events")
data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val songId: String,          // videoId or id.toString()
    val title: String,
    val artist: String,
    val duration: Long,
    val thumbnailUrl: String?,   // local path or web URL
    val timestamp: Long,         // epoch milliseconds
    val hourOfDay: Int           // 0..23 (for analytics charts!)
)
