package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val sourcePath: String, // "sample_1.pdf", etc.
    val fileSizeBytes: Long,
    val lastReadPage: Int = 1,
    val totalPages: Int = 8,
    val isSynced: Boolean = true,
    val lastSyncTime: Long = System.currentTimeMillis(),
    val aiSummary: String = "",
    val keyTakeaways: String = "", // Comma-separated or markdown list
    val tags: String = "Research" // e.g. "AI, Neural Networks"
)

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val textHighlighted: String,
    val colorHex: String, // e.g., "#FFEB3B"
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "" // Optional user scribblings
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val deviceType: String, // "Desktop Editor", "Web Reader", "Mobile Slate", "Tablet Viewer"
    val lastSyncTime: Long = System.currentTimeMillis(),
    val status: String = "Connected", // "Connected", "Offline", "Syncing"
    val isMainDevice: Boolean = false
)
