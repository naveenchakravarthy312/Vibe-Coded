package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StageTimeDao {
    @Query("SELECT * FROM stage_time_records ORDER BY recordedAt DESC")
    fun getAllRecords(): Flow<List<StageTimeRecord>>

    @Query("SELECT * FROM stage_time_records WHERE rallyId = :rallyId ORDER BY timeMillis ASC")
    fun getRecordsByRally(rallyId: String): Flow<List<StageTimeRecord>>

    @Query("SELECT * FROM stage_time_records WHERE rallyId = :rallyId AND stageName = :stageName ORDER BY timeMillis ASC")
    fun getRecordsForStage(rallyId: String, stageName: String): Flow<List<StageTimeRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: StageTimeRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<StageTimeRecord>)

    @Update
    suspend fun updateRecord(record: StageTimeRecord)

    @Delete
    suspend fun deleteRecord(record: StageTimeRecord)

    @Query("DELETE FROM stage_time_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM stage_time_records")
    suspend fun deleteAll()
}
