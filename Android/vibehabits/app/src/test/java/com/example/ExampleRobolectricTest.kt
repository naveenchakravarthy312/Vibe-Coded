package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VibeHabits", appName)
    }

    @Test
    fun `test habit streak calculation`() {
        val today = LocalDate.of(2026, 10, 27)
        val habit = Habit(
            id = "test-1",
            title = "Test Habit",
            createdAt = "2026-10-01",
            completionDates = setOf("2026-10-25", "2026-10-26", "2026-10-27")
        )
        assertEquals(3, habit.currentStreak(today))
        assertEquals(3, habit.longestStreak())
        assertTrue(habit.isCompletedToday(today))
    }
}
