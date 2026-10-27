package com.example.data.repository

import com.example.data.local.HabitCompletionEntity
import com.example.data.local.HabitDao
import com.example.data.local.HabitEntity
import com.example.data.model.Habit
import com.example.data.model.HabitExportData
import com.example.data.model.HabitExportItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ImportResult(
    val habitsCount: Int,
    val completionsCount: Int,
    val isReplaced: Boolean
)

class HabitRepository(
    private val habitDao: HabitDao
) {

    val habitsFlow: Flow<List<Habit>> = habitDao.getAllHabitsWithCompletions().map { list ->
        list.map { it.toDomainModel() }
    }

    suspend fun ensureInitialSeed(today: LocalDate = LocalDate.now()) {
        if (habitDao.getHabitsCount() > 0) return

        val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
        val todayStr = today.format(isoDate)
        val yestStr = today.minusDays(1).format(isoDate)
        val d2Str = today.minusDays(2).format(isoDate)
        val d3Str = today.minusDays(3).format(isoDate)
        val d4Str = today.minusDays(4).format(isoDate)
        val d5Str = today.minusDays(5).format(isoDate)

        val starterHabits = listOf(
            HabitEntity(
                id = UUID.randomUUID().toString(),
                title = "Morning Hydration (1L)",
                category = "Health",
                createdAt = today.minusDays(14).format(isoDate)
            ),
            HabitEntity(
                id = UUID.randomUUID().toString(),
                title = "Deep Focus Work (45m)",
                category = "Focus",
                createdAt = today.minusDays(20).format(isoDate)
            ),
            HabitEntity(
                id = UUID.randomUUID().toString(),
                title = "Evening Wind Down (No Screens)",
                category = "Mind",
                createdAt = today.minusDays(10).format(isoDate)
            )
        )

        habitDao.insertHabits(starterHabits)

        // Seed some realistic completions for a welcoming feel
        val completions = listOf(
            // Morning Hydration: 5-day active streak up to today
            HabitCompletionEntity(starterHabits[0].id, todayStr),
            HabitCompletionEntity(starterHabits[0].id, yestStr),
            HabitCompletionEntity(starterHabits[0].id, d2Str),
            HabitCompletionEntity(starterHabits[0].id, d3Str),
            HabitCompletionEntity(starterHabits[0].id, d4Str),

            // Deep Focus: 4-day streak ending yesterday (ready to complete today!)
            HabitCompletionEntity(starterHabits[1].id, yestStr),
            HabitCompletionEntity(starterHabits[1].id, d2Str),
            HabitCompletionEntity(starterHabits[1].id, d3Str),
            HabitCompletionEntity(starterHabits[1].id, d5Str),

            // Evening Wind Down: 3-day streak ending yesterday
            HabitCompletionEntity(starterHabits[2].id, yestStr),
            HabitCompletionEntity(starterHabits[2].id, d2Str),
            HabitCompletionEntity(starterHabits[2].id, d3Str)
        )
        habitDao.insertCompletions(completions)
    }

    suspend fun addHabit(title: String, category: String, today: LocalDate = LocalDate.now()): Habit {
        val newId = UUID.randomUUID().toString()
        val createdAt = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val entity = HabitEntity(
            id = newId,
            title = title.trim(),
            category = category.trim().ifEmpty { "General" },
            createdAt = createdAt
        )
        habitDao.insertHabit(entity)
        return Habit(
            id = newId,
            title = entity.title,
            category = entity.category,
            createdAt = createdAt,
            completionDates = emptySet()
        )
    }

    suspend fun updateHabit(id: String, title: String, category: String) {
        val existing = habitDao.getHabitById(id) ?: return
        val updated = existing.habit.copy(
            title = title.trim(),
            category = category.trim().ifEmpty { "General" }
        )
        habitDao.updateHabit(updated)
    }

    suspend fun deleteHabit(id: String) {
        habitDao.deleteHabitById(id)
    }

    suspend fun toggleCompletion(habitId: String, dateStr: String, shouldComplete: Boolean) {
        if (shouldComplete) {
            habitDao.insertCompletion(HabitCompletionEntity(habitId, dateStr))
        } else {
            habitDao.deleteCompletion(habitId, dateStr)
        }
    }

    /**
     * Generates standard universal human-readable JSON with 2-space indentation.
     */
    fun exportToJson(habits: List<Habit>): String {
        val root = JSONObject()
        root.put("app", "VibeHabits")
        root.put("version", 1)
        root.put("exported_at", Instant.now().toString())

        val habitsArray = JSONArray()
        for (habit in habits) {
            val habitObj = JSONObject()
            habitObj.put("id", habit.id)
            habitObj.put("title", habit.title)
            habitObj.put("category", habit.category)
            habitObj.put("created_at", habit.createdAt)

            val completionsArray = JSONArray()
            habit.completionDates.sorted().forEach { date ->
                completionsArray.put(date)
            }
            habitObj.put("completion_dates", completionsArray)
            habitsArray.put(habitObj)
        }
        root.put("habits", habitsArray)

        return root.toString(2)
    }

    /**
     * Imports from universal JSON string with schema validation.
     */
    suspend fun importFromJson(jsonString: String, replaceAll: Boolean = false): Result<ImportResult> {
        return runCatching {
            val root = JSONObject(jsonString)
            if (!root.has("habits")) {
                throw IllegalArgumentException("Invalid backup file: missing 'habits' field.")
            }

            val habitsArray = root.getJSONArray("habits")
            val newHabitEntities = mutableListOf<HabitEntity>()
            val newCompletions = mutableListOf<HabitCompletionEntity>()

            for (i in 0 until habitsArray.length()) {
                val obj = habitsArray.getJSONObject(i)
                val id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() }
                val title = obj.getString("title")
                val category = obj.optString("category", "General")
                val createdAt = obj.optString("created_at", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))

                newHabitEntities.add(
                    HabitEntity(
                        id = id,
                        title = title,
                        category = category,
                        createdAt = createdAt
                    )
                )

                if (obj.has("completion_dates")) {
                    val compArr = obj.getJSONArray("completion_dates")
                    for (c in 0 until compArr.length()) {
                        val d = compArr.getString(c)
                        if (d.isNotBlank()) {
                            newCompletions.add(HabitCompletionEntity(habitId = id, date = d))
                        }
                    }
                }
            }

            habitDao.importData(
                habits = newHabitEntities,
                completions = newCompletions,
                replaceAll = replaceAll
            )

            ImportResult(
                habitsCount = newHabitEntities.size,
                completionsCount = newCompletions.size,
                isReplaced = replaceAll
            )
        }
    }
}
