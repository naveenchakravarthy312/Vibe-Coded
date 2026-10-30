package com.example.data.local

import kotlinx.coroutines.flow.Flow

class CalculationRepository(private val dao: CalculationDao) {
    val allHistory: Flow<List<CalculationEntity>> = dao.getAllHistory()
    val favorites: Flow<List<CalculationEntity>> = dao.getFavoriteHistory()

    fun getByCategory(category: String): Flow<List<CalculationEntity>> =
        dao.getHistoryByCategory(category)

    fun search(query: String): Flow<List<CalculationEntity>> =
        dao.searchHistory(query)

    suspend fun insert(calculation: CalculationEntity): Long =
        dao.insert(calculation)

    suspend fun insertAll(calculations: List<CalculationEntity>) =
        dao.insertAll(calculations)

    suspend fun update(calculation: CalculationEntity) =
        dao.update(calculation)

    suspend fun deleteById(id: Long) =
        dao.deleteById(id)

    suspend fun clearAll() =
        dao.clearAll()

    suspend fun clearByCategory(category: String) =
        dao.clearByCategory(category)

    suspend fun clearFavorites() =
        dao.clearFavorites()

    suspend fun toggleFavorite(id: Long, current: Boolean) =
        dao.updateFavorite(id, !current)

    suspend fun updateNote(id: Long, note: String) =
        dao.updateNote(id, note)
}
