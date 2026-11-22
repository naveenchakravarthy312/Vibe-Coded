package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stage_time_records")
data class StageTimeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val rallyId: String,
    val rallyName: String,
    val stageName: String,
    val stageDistanceKm: Double = 0.0,
    val timeMillis: Long,
    val weather: String,
    val camera: String,
    val carName: String = "Rally Spec 4WD",
    val carClass: String = "Class 1",
    val transmission: String = "Sequential",
    val notes: String = "",
    val recordedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val splitsJson: String = ""
)
