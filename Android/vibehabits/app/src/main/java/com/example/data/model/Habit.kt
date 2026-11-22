package com.example.data.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Habit(
    val id: String,
    val title: String,
    val category: String = "General",
    val createdAt: String, // YYYY-MM-DD
    val completionDates: Set<String> = emptySet() // Set of YYYY-MM-DD
) {
    val totalCompletions: Int
        get() = completionDates.size

    fun isCompletedOn(date: String): Boolean = completionDates.contains(date)

    fun isCompletedToday(today: LocalDate = LocalDate.now()): Boolean =
        isCompletedOn(today.format(DateTimeFormatter.ISO_LOCAL_DATE))

    /**
     * Calculates the current active streak.
     * If completed today, counts backward consecutively from today.
     * If not completed today, but completed yesterday, streak is maintained from yesterday.
     * If neither today nor yesterday is completed, streak is 0.
     */
    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val yesterdayStr = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

        val startsFromToday = completionDates.contains(todayStr)
        val startsFromYesterday = completionDates.contains(yesterdayStr)

        if (!startsFromToday && !startsFromYesterday) {
            return 0
        }

        var streak = 0
        var checkDate = if (startsFromToday) today else today.minusDays(1)

        while (true) {
            val dateStr = checkDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            if (completionDates.contains(dateStr)) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    /**
     * Calculates the best / longest historical streak of consecutive days.
     */
    fun longestStreak(): Int {
        if (completionDates.isEmpty()) return 0
        val sortedDates = completionDates.mapNotNull {
            try {
                LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                null
            }
        }.sorted()

        if (sortedDates.isEmpty()) return 0

        var maxStreak = 1
        var currentRunningStreak = 1

        for (i in 1 until sortedDates.size) {
            val prev = sortedDates[i - 1]
            val curr = sortedDates[i]
            if (curr == prev.plusDays(1)) {
                currentRunningStreak++
                if (currentRunningStreak > maxStreak) {
                    maxStreak = currentRunningStreak
                }
            } else if (curr != prev) {
                currentRunningStreak = 1
            }
        }
        return maxStreak
    }

    /**
     * Trailing 7-day completion status: returns 7 items from (today - 6 days) to today.
     */
    fun trailing7Days(today: LocalDate = LocalDate.now()): List<DayStatus> {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        return (6 downTo 0).map { daysAgo ->
            val d = today.minusDays(daysAgo.toLong())
            val dateStr = d.format(formatter)
            DayStatus(
                date = d,
                dateStr = dateStr,
                dayOfWeekLetter = d.dayOfWeek.name.take(1),
                isCompleted = completionDates.contains(dateStr),
                isToday = daysAgo == 0
            )
        }
    }
}

data class DayStatus(
    val date: LocalDate,
    val dateStr: String,
    val dayOfWeekLetter: String,
    val isCompleted: Boolean,
    val isToday: Boolean
)
