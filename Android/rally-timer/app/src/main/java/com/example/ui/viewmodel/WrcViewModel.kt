package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.StageTimeRecord
import com.example.data.local.WrcDatabase
import com.example.data.model.Rally
import com.example.data.model.RallyPresets
import com.example.data.model.StagePreset
import com.example.data.model.TimeFormatter
import com.example.data.preferences.SessionPreferences
import com.example.data.preferences.StageSessionConfig
import com.example.data.preferences.ThemeConfig
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.WrcRepository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class RallyFilterState(
    val selectedRallyId: String? = null,
    val selectedWeather: String? = null,
    val selectedCamera: String? = null,
    val searchQuery: String = "",
    val personalBestsOnly: Boolean = false,
    val favoritesOnly: Boolean = false
)

class WrcViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WrcDatabase.getDatabase(application)
    private val repository = WrcRepository(database.stageTimeDao())
    private val themePreferences = ThemePreferences(application)
    private val sessionPreferences = SessionPreferences(application)

    val themeConfig: StateFlow<ThemeConfig> = themePreferences.themeConfigFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeConfig()
        )

    val activeSession: StateFlow<StageSessionConfig> = sessionPreferences.sessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StageSessionConfig()
        )

    val allRecords: StateFlow<List<StageTimeRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _filterState = MutableStateFlow(RallyFilterState())
    val filterState: StateFlow<RallyFilterState> = _filterState.asStateFlow()

    // UI Sheets State
    private val _isRecordSheetOpen = MutableStateFlow(false)
    val isRecordSheetOpen: StateFlow<Boolean> = _isRecordSheetOpen.asStateFlow()

    private val _editingRecord = MutableStateFlow<StageTimeRecord?>(null)
    val editingRecord: StateFlow<StageTimeRecord?> = _editingRecord.asStateFlow()

    private val _prefillTimeMillis = MutableStateFlow<Long?>(null)
    val prefillTimeMillis: StateFlow<Long?> = _prefillTimeMillis.asStateFlow()

    private val _isThemeSheetOpen = MutableStateFlow(false)
    val isThemeSheetOpen: StateFlow<Boolean> = _isThemeSheetOpen.asStateFlow()

    private val _isExportImportSheetOpen = MutableStateFlow(false)
    val isExportImportSheetOpen: StateFlow<Boolean> = _isExportImportSheetOpen.asStateFlow()

    private val _leaderboardTarget = MutableStateFlow<Pair<Rally, StagePreset>?>(null)
    val leaderboardTarget: StateFlow<Pair<Rally, StagePreset>?> = _leaderboardTarget.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Derived filtered records flow
    val filteredRecords: StateFlow<List<StageTimeRecord>> = combine(
        allRecords,
        filterState
    ) { records, filters ->
        var list = records

        if (filters.selectedRallyId != null) {
            list = list.filter { it.rallyId == filters.selectedRallyId }
        }
        if (filters.selectedWeather != null) {
            list = list.filter { it.weather.contains(filters.selectedWeather, ignoreCase = true) }
        }
        if (filters.selectedCamera != null) {
            list = list.filter { it.camera.contains(filters.selectedCamera, ignoreCase = true) }
        }
        if (filters.favoritesOnly) {
            list = list.filter { it.isFavorite }
        }
        if (filters.searchQuery.isNotBlank()) {
            val q = filters.searchQuery.trim().lowercase()
            list = list.filter {
                it.stageName.lowercase().contains(q) ||
                        it.rallyName.lowercase().contains(q) ||
                        it.carName.lowercase().contains(q) ||
                        it.notes.lowercase().contains(q)
            }
        }
        if (filters.personalBestsOnly) {
            val pbs = list.groupBy { "${it.rallyId}__${it.stageName}" }
                .mapValues { entry -> entry.value.minByOrNull { it.timeMillis } }
                .values.filterNotNull()
                .sortedByDescending { it.recordedAt }
            list = pbs
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Best times by stage across all records
    val bestTimesMap: StateFlow<Map<String, Long>> = allRecords.map { records ->
        records.groupBy { "${it.rallyId}__${it.stageName}" }
            .mapValues { entry -> entry.value.minOfOrNull { it.timeMillis } ?: 0L }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // Filter controls
    fun setRallyFilter(rallyId: String?) {
        _filterState.update { it.copy(selectedRallyId = rallyId) }
    }

    fun setWeatherFilter(weather: String?) {
        _filterState.update { it.copy(selectedWeather = weather) }
    }

    fun setCameraFilter(camera: String?) {
        _filterState.update { it.copy(selectedCamera = camera) }
    }

    fun setSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun togglePersonalBestsOnly() {
        _filterState.update { it.copy(personalBestsOnly = !it.personalBestsOnly) }
    }

    fun toggleFavoritesOnly() {
        _filterState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun clearAllFilters() {
        _filterState.value = RallyFilterState()
    }

    // Active Stage Session controls
    fun updateActiveSession(config: StageSessionConfig) {
        viewModelScope.launch {
            sessionPreferences.saveSession(config)
        }
    }

    fun setSessionRally(rallyId: String) {
        val rally = RallyPresets.RALLIES.firstOrNull { it.id == rallyId } ?: return
        val stage = rally.stages.first()
        val current = activeSession.value
        val updated = current.copy(
            rallyId = rally.id,
            rallyName = rally.name,
            stageName = stage.name,
            stageDistanceKm = stage.distanceKm
        )
        updateActiveSession(updated)
    }

    fun setSessionStage(stageName: String) {
        val rally = RallyPresets.RALLIES.firstOrNull { it.id == activeSession.value.rallyId }
            ?: RallyPresets.RALLIES.first()
        val stage = rally.stages.firstOrNull { it.name == stageName } ?: return
        val current = activeSession.value
        val updated = current.copy(
            stageName = stage.name,
            stageDistanceKm = stage.distanceKm
        )
        updateActiveSession(updated)
    }

    fun advanceToNextStage() {
        val rally = RallyPresets.RALLIES.firstOrNull { it.id == activeSession.value.rallyId }
            ?: RallyPresets.RALLIES.first()
        val currentIndex = rally.stages.indexOfFirst { it.name == activeSession.value.stageName }
        val nextIndex = if (currentIndex != -1 && currentIndex + 1 < rally.stages.size) {
            currentIndex + 1
        } else {
            0
        }
        val nextStage = rally.stages[nextIndex]
        updateActiveSession(
            activeSession.value.copy(
                stageName = nextStage.name,
                stageDistanceKm = nextStage.distanceKm
            )
        )
        showToast("Stage: ${nextStage.name}")
    }

    fun advanceToPreviousStage() {
        val rally = RallyPresets.RALLIES.firstOrNull { it.id == activeSession.value.rallyId }
            ?: RallyPresets.RALLIES.first()
        val currentIndex = rally.stages.indexOfFirst { it.name == activeSession.value.stageName }
        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            rally.stages.size - 1
        }
        val prevStage = rally.stages[prevIndex]
        updateActiveSession(
            activeSession.value.copy(
                stageName = prevStage.name,
                stageDistanceKm = prevStage.distanceKm
            )
        )
        showToast("Stage: ${prevStage.name}")
    }

    fun setSessionWeather(weather: String) {
        updateActiveSession(activeSession.value.copy(weather = weather))
    }

    fun setSessionCamera(camera: String) {
        updateActiveSession(activeSession.value.copy(camera = camera))
    }

    fun setSessionCar(carName: String, carClass: String = activeSession.value.carClass) {
        updateActiveSession(activeSession.value.copy(carName = carName, carClass = carClass))
    }

    fun setSessionTransmission(trans: String) {
        updateActiveSession(activeSession.value.copy(transmission = trans))
    }

    fun toggleStickySession(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setStickyEnabled(enabled)
            showToast(if (enabled) "Sticky Stage Settings ON" else "Sticky Stage Settings OFF")
        }
    }

    fun repeatStageRun(record: StageTimeRecord) {
        val config = StageSessionConfig(
            rallyId = record.rallyId,
            rallyName = record.rallyName,
            stageName = record.stageName,
            stageDistanceKm = record.stageDistanceKm,
            weather = record.weather,
            camera = record.camera,
            carName = record.carName,
            carClass = record.carClass,
            transmission = record.transmission,
            notes = record.notes,
            stickyEnabled = activeSession.value.stickyEnabled
        )
        openNewRecordSheet(stageConfig = config)
    }

    fun openLeaderboardStageRecord(rally: Rally, stage: StagePreset) {
        val current = activeSession.value
        val config = current.copy(
            rallyId = rally.id,
            rallyName = rally.name,
            stageName = stage.name,
            stageDistanceKm = stage.distanceKm
        )
        openNewRecordSheet(stageConfig = config)
    }

    // Recording Sheet controls (Manual time entry)
    fun openNewRecordSheet(prefillMillis: Long? = null, stageConfig: StageSessionConfig? = null) {
        _editingRecord.value = null
        _prefillTimeMillis.value = prefillMillis
        if (stageConfig != null) {
            updateActiveSession(stageConfig)
        }
        _isRecordSheetOpen.value = true
    }

    fun openEditRecordSheet(record: StageTimeRecord) {
        _editingRecord.value = record
        _prefillTimeMillis.value = record.timeMillis
        _isRecordSheetOpen.value = true
    }

    fun closeRecordSheet() {
        _isRecordSheetOpen.value = false
        _editingRecord.value = null
        _prefillTimeMillis.value = null
    }

    fun saveOrUpdateRecord(
        rallyId: String,
        rallyName: String,
        stageName: String,
        distanceKm: Double,
        timeMillis: Long,
        weather: String,
        camera: String,
        carName: String,
        carClass: String,
        transmission: String,
        notes: String
    ) {
        viewModelScope.launch {
            val currentEdit = _editingRecord.value
            if (currentEdit != null) {
                val updated = currentEdit.copy(
                    rallyId = rallyId,
                    rallyName = rallyName,
                    stageName = stageName,
                    stageDistanceKm = distanceKm,
                    timeMillis = timeMillis,
                    weather = weather,
                    camera = camera,
                    carName = carName,
                    carClass = carClass,
                    transmission = transmission,
                    notes = notes
                )
                repository.updateRecord(updated)
                showToast("Stage record updated")
            } else {
                val newRecord = StageTimeRecord(
                    rallyId = rallyId,
                    rallyName = rallyName,
                    stageName = stageName,
                    stageDistanceKm = distanceKm,
                    timeMillis = timeMillis,
                    weather = weather,
                    camera = camera,
                    carName = carName,
                    carClass = carClass,
                    transmission = transmission,
                    notes = notes,
                    recordedAt = System.currentTimeMillis()
                )
                repository.insertRecord(newRecord)
                showToast("Logged stage time: ${TimeFormatter.formatMillis(timeMillis)}")
            }

            // Keep settings locked and remembered for subsequent runs
            if (activeSession.value.stickyEnabled) {
                sessionPreferences.saveSession(
                    activeSession.value.copy(
                        rallyId = rallyId,
                        rallyName = rallyName,
                        stageName = stageName,
                        stageDistanceKm = distanceKm,
                        weather = weather,
                        camera = camera,
                        carName = carName,
                        carClass = carClass,
                        transmission = transmission,
                        notes = notes
                    )
                )
            }
            closeRecordSheet()
        }
    }

    fun toggleFavorite(record: StageTimeRecord) {
        viewModelScope.launch {
            repository.updateRecord(record.copy(isFavorite = !record.isFavorite))
        }
    }

    fun deleteRecord(record: StageTimeRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
            showToast("Record deleted")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            showToast("All stage records cleared")
        }
    }

    // Leaderboard
    fun openLeaderboard(rally: Rally, stage: StagePreset) {
        _leaderboardTarget.value = rally to stage
    }

    fun closeLeaderboard() {
        _leaderboardTarget.value = null
    }

    // Theme controls
    fun openThemeSheet() {
        _isThemeSheetOpen.value = true
    }

    fun closeThemeSheet() {
        _isThemeSheetOpen.value = false
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun toggleAmoled() {
        val current = themeConfig.value.themeMode
        val next = if (current == AppThemeMode.AMOLED) AppThemeMode.DARK else AppThemeMode.AMOLED
        setThemeMode(next)
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setDynamicColor(enabled)
        }
    }

    // Export / Import
    fun openExportImportSheet() {
        _isExportImportSheetOpen.value = true
    }

    fun closeExportImportSheet() {
        _isExportImportSheetOpen.value = false
    }

    fun exportRecordsToJson(): String {
        val records = allRecords.value
        val array = JSONArray()
        for (r in records) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("rallyId", r.rallyId)
                put("rallyName", r.rallyName)
                put("stageName", r.stageName)
                put("stageDistanceKm", r.stageDistanceKm)
                put("timeMillis", r.timeMillis)
                put("weather", r.weather)
                put("camera", r.camera)
                put("carName", r.carName)
                put("carClass", r.carClass)
                put("transmission", r.transmission)
                put("notes", r.notes)
                put("recordedAt", r.recordedAt)
                put("isFavorite", r.isFavorite)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun exportRecordsToCsv(): String {
        val sb = StringBuilder()
        sb.append("Rally,Stage,Distance_km,Time,Time_ms,Weather,Camera,Car,Class,Transmission,Date,Notes\n")
        for (r in allRecords.value) {
            val cleanNotes = r.notes.replace("\"", "\"\"")
            sb.append("\"${r.rallyName}\",\"${r.stageName}\",${r.stageDistanceKm},\"${TimeFormatter.formatMillis(r.timeMillis)}\",${r.timeMillis},\"${r.weather}\",\"${r.camera}\",\"${r.carName}\",\"${r.carClass}\",\"${r.transmission}\",${r.recordedAt},\"$cleanNotes\"\n")
        }
        return sb.toString()
    }

    fun importRecordsFromJson(jsonStr: String): Pair<Boolean, String> {
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<StageTimeRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    StageTimeRecord(
                        rallyId = obj.optString("rallyId", "monte_carlo"),
                        rallyName = obj.optString("rallyName", "Rallye Monte-Carlo"),
                        stageName = obj.optString("stageName", "Stage"),
                        stageDistanceKm = obj.optDouble("stageDistanceKm", 5.0),
                        timeMillis = obj.optLong("timeMillis", 0L),
                        weather = obj.optString("weather", "Clear / Sunny"),
                        camera = obj.optString("camera", "Cockpit / Dash"),
                        carName = obj.optString("carName", "Rally Spec 4WD"),
                        carClass = obj.optString("carClass", "Class 1"),
                        transmission = obj.optString("transmission", "Sequential"),
                        notes = obj.optString("notes", ""),
                        recordedAt = obj.optLong("recordedAt", System.currentTimeMillis()),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
            }
            if (list.isNotEmpty()) {
                viewModelScope.launch {
                    repository.insertAll(list)
                }
                Pair(true, "Successfully imported ${list.size} stage records.")
            } else {
                Pair(false, "No valid records found in JSON.")
            }
        } catch (e: Exception) {
            Pair(false, "Import failed: ${e.message}")
        }
    }
}
