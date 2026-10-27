package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.data.model.Habit

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val category: String,
    val createdAt: String, // YYYY-MM-DD
    val archived: Boolean = false
)

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("habitId"),
        Index("date")
    ]
)
data class HabitCompletionEntity(
    val habitId: String,
    val date: String // YYYY-MM-DD
)

data class HabitWithCompletions(
    @Embedded
    val habit: HabitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "habitId"
    )
    val completions: List<HabitCompletionEntity>
) {
    fun toDomainModel(): Habit {
        return Habit(
            id = habit.id,
            title = habit.title,
            category = habit.category,
            createdAt = habit.createdAt,
            completionDates = completions.map { it.date }.toSet()
        )
    }
}
