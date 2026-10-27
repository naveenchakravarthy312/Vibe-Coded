package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Transaction
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY rowid ASC")
    fun getAllHabitsWithCompletions(): Flow<List<HabitWithCompletions>>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getHabitById(id: String): HabitWithCompletions?

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun getHabitsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabitsIgnore(habits: List<HabitEntity>): List<Long>

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: HabitCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletions(completions: List<HabitCompletionEntity>)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCompletion(habitId: String, date: String)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId")
    suspend fun deleteAllCompletionsForHabit(habitId: String)

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("DELETE FROM habit_completions")
    suspend fun clearCompletions()

    @Transaction
    suspend fun clearAllData() {
        clearCompletions()
        clearHabits()
    }

    @Transaction
    suspend fun importData(
        habits: List<HabitEntity>,
        completions: List<HabitCompletionEntity>,
        replaceAll: Boolean
    ) {
        if (replaceAll) {
            clearAllData()
            insertHabits(habits)
        } else {
            val rowIds = insertHabitsIgnore(habits)
            for (i in habits.indices) {
                if (rowIds[i] == -1L) {
                    updateHabit(habits[i])
                }
            }
        }
        insertCompletions(completions)
    }
}
