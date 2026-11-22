package com.example.data.repository

import com.example.data.local.HabitCompletionEntity
import com.example.data.local.HabitDao
import com.example.data.local.HabitEntity
import com.example.data.model.Habit
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
     * Imports from universal JSON string with schema and ISO date validation.
     */
    suspend fun importFromJson(jsonString: String, replaceAll: Boolean = false): Result<ImportResult> {
        return runCatching {
            val root = JSONObject(jsonString)
            if (!root.has("habits")) {
                throw IllegalArgumentException("Invalid backup file: missing 'habits' field.")
            }

            val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
            val todayStr = LocalDate.now().format(isoFormatter)
            val habitsArray = root.getJSONArray("habits")
            val newHabitEntities = mutableListOf<HabitEntity>()
            val newCompletions = mutableListOf<HabitCompletionEntity>()

            for (i in 0 until habitsArray.length()) {
                val obj = habitsArray.getJSONObject(i)
                val id = obj.optString("id").trim().ifEmpty { UUID.randomUUID().toString() }
                val title = obj.optString("title").trim()
                if (title.isEmpty()) {
                    throw IllegalArgumentException("Habit at index $i has an empty title.")
                }
                val category = obj.optString("category", "General").trim().ifEmpty { "General" }
                val rawCreatedAt = obj.optString("created_at", todayStr).trim()
                val createdAt = try {
                    LocalDate.parse(rawCreatedAt, isoFormatter).format(isoFormatter)
                } catch (_: Exception) {
                    todayStr
                }

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
                        val d = compArr.optString(c).trim()
                        if (d.isNotEmpty()) {
                            val validDate = try {
                                LocalDate.parse(d, isoFormatter).format(isoFormatter)
                            } catch (_: Exception) {
                                throw IllegalArgumentException("Invalid completion date '$d' in habit '$title'. Expected YYYY-MM-DD.")
                            }
                            newCompletions.add(HabitCompletionEntity(habitId = id, date = validDate))
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
