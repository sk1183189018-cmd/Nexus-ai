package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "model", etc.
    val text: String,
    val hasCode: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String = "gemini-3.5-flash"
)

@Entity(tableName = "memory_entries")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // "preference", "habit", "note", "frequent_app"
    val contentKey: String,
    val contentValue: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_workflows")
data class AutomationWorkflow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerType: String, // "TIME", "APP_LAUNCH", "HEADPHONES", "STARTUP"
    val triggerValue: String, // e.g. "08:00 AM", "youtube", "connected"
    val actionType: String, // "SAY", "TOGGLE_WIFI", "LAUNCH_APP", "OPEN_CAM"
    val actionValue: String, // e.g. "Good morning master", "OFF", "com.google.android.youtube", ""
    val isActive: Boolean = true,
    val lastTriggered: Long = 0
)
