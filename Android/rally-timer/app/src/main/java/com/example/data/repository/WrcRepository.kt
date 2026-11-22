package com.example.data.repository

import com.example.data.local.StageTimeDao
import com.example.data.local.StageTimeRecord
import kotlinx.coroutines.flow.Flow

class WrcRepository(private val dao: StageTimeDao) {
    val allRecords: Flow<List<StageTimeRecord>> = dao.getAllRecords()

    fun getRecordsByRally(rallyId: String): Flow<List<StageTimeRecord>> =
        dao.getRecordsByRally(rallyId)

    fun getRecordsForStage(rallyId: String, stageName: String): Flow<List<StageTimeRecord>> =
        dao.getRecordsForStage(rallyId, stageName)

    suspend fun insertRecord(record: StageTimeRecord): Long = dao.insertRecord(record)

    suspend fun insertAll(records: List<StageTimeRecord>) = dao.insertAll(records)

    suspend fun updateRecord(record: StageTimeRecord) = dao.updateRecord(record)

    suspend fun deleteRecord(record: StageTimeRecord) = dao.deleteRecord(record)

    suspend fun deleteRecordById(id: Long) = dao.deleteRecordById(id)

    suspend fun clearAll() = dao.deleteAll()
}
