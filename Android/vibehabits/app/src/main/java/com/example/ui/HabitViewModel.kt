package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Habit
import com.example.data.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("vibe_habits_prefs", Context.MODE_PRIVATE)

    private val repository: HabitRepository = HabitRepository(
        AppDatabase.getDatabase(application).habitDao()
    )

    val habits: StateFlow<List<Habit>> = repository.habitsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isAmoledMode = MutableStateFlow(prefs.getBoolean("amoled_mode", false))
    val isAmoledMode: StateFlow<Boolean> = _isAmoledMode.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    val filteredHabits: StateFlow<List<Habit>> = combine(
        habits,
        _selectedCategoryFilter
    ) { allHabits, categoryFilter ->
        if (categoryFilter == null) {
            allHabits
        } else {
            val matching = allHabits.filter { habit ->
                habit.category.equals(categoryFilter, ignoreCase = true)
            }
            if (matching.isEmpty() && allHabits.isNotEmpty()) {
                _selectedCategoryFilter.value = null
                allHabits
            } else {
                matching
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val exportJsonString: StateFlow<String> = habits.map { allHabits ->
        repository.exportToJson(allHabits)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "{}"
    )

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun toggleToday(habitId: String) {
        viewModelScope.launch {
            val habit = habits.value.find { it.id == habitId } ?: return@launch
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val isCompleted = habit.completionDates.contains(todayStr)
            repository.toggleCompletion(habitId, todayStr, !isCompleted)
        }
    }

    fun toggleDay(habitId: String, dateStr: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.toggleCompletion(habitId, dateStr, !currentStatus)
        }
    }

    fun addHabit(title: String, category: String) {
        viewModelScope.launch {
            repository.addHabit(title, category)
            _uiMessage.value = "Habit \"$title\" created"
        }
    }

    fun updateHabit(id: String, title: String, category: String) {
        viewModelScope.launch {
            repository.updateHabit(id, title, category)
            _uiMessage.value = "Habit updated"
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch {
            repository.deleteHabit(id)
            _uiMessage.value = "Habit deleted"
        }
    }

    fun importBackup(jsonString: String, replaceAll: Boolean, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.importFromJson(jsonString, replaceAll)
            result.onSuccess { info ->
                val msg = if (info.isReplaced) {
                    "Restored ${info.habitsCount} habits (${info.completionsCount} completions)"
                } else {
                    "Imported ${info.habitsCount} habits (${info.completionsCount} completions)"
                }
                _uiMessage.value = msg
                onComplete(true, msg)
            }.onFailure { err ->
                val errorMsg = "Import failed: ${err.message}"
                _uiMessage.value = errorMsg
                onComplete(false, errorMsg)
            }
        }
    }

    fun toggleAmoledMode() {
        val next = !_isAmoledMode.value
        _isAmoledMode.value = next
        prefs.edit().putBoolean("amoled_mode", next).apply()
        _uiMessage.value = if (next) "AMOLED mode enabled" else "AMOLED mode disabled"
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }
}
