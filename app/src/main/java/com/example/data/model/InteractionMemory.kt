package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interaction_memories")
data class InteractionMemory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawText: String,
    val summary: String,
    val category: String, // "NOTE", "TASK", "REMINDER", "INSIGHT", "GENERAL"
    val embeddingCsv: String, // serialized vector floats for local vector db search
    val timestamp: Long = System.currentTimeMillis(),
    val extractedDueDate: Long? = null,
    val recurringPattern: String? = null, // "DAILY", "WEEKLY", "MONTHLY", null
    val syncedToGoogleCalendar: Boolean = false,
    val syncedToGoogleTasks: Boolean = false,
    val isCompleted: Boolean = false,
    val tagsCsv: String = ""
)
