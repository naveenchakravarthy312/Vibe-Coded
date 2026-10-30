package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationDao {
    @Query("SELECT * FROM calculation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CalculationEntity>>

    @Query("SELECT * FROM calculation_history WHERE category = :category ORDER BY timestamp DESC")
    fun getHistoryByCategory(category: String): Flow<List<CalculationEntity>>

    @Query("SELECT * FROM calculation_history WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteHistory(): Flow<List<CalculationEntity>>

    @Query("""
        SELECT * FROM calculation_history 
        WHERE expression LIKE '%' || :query || '%' 
           OR result LIKE '%' || :query || '%' 
           OR note LIKE '%' || :query || '%' 
           OR details LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchHistory(query: String): Flow<List<CalculationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calculation: CalculationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calculations: List<CalculationEntity>)

    @Update
    suspend fun update(calculation: CalculationEntity)

    @Query("DELETE FROM calculation_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM calculation_history")
    suspend fun clearAll()

    @Query("DELETE FROM calculation_history WHERE category = :category")
    suspend fun clearByCategory(category: String)

    @Query("DELETE FROM calculation_history WHERE isFavorite = 1")
    suspend fun clearFavorites()

    @Query("UPDATE calculation_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE calculation_history SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)
}
