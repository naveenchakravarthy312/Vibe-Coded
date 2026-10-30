package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val command: String,
    val category: String = "General",
    val isPreset: Boolean = false
)

@Entity(tableName = "command_history")
data class CommandHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val timestamp: Long = System.currentTimeMillis()
)
