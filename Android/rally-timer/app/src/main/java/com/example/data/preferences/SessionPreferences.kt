package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.Rally
import com.example.data.model.RallyPresets
import com.example.data.model.StagePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "stage_session_settings")

data class StageSessionConfig(
    val rallyId: String = "monte_carlo",
    val rallyName: String = "Rallye Monte-Carlo",
    val stageName: String = "Agnières en Devoluy - Le Motty",
    val stageDistanceKm: Double = 10.3,
    val weather: String = "Clear / Sunny",
    val camera: String = "Cockpit / Dash",
    val carName: String = "Rally Spec 4WD",
    val carClass: String = "Class 1 (Premier)",
    val transmission: String = "Sequential",
    val notes: String = "",
    val stickyEnabled: Boolean = true
) {
    val rally: Rally
        get() = RallyPresets.RALLIES.firstOrNull { it.id == rallyId } ?: RallyPresets.RALLIES.first()

    val stage: StagePreset
        get() = rally.stages.firstOrNull { it.name == stageName } ?: rally.stages.first()

    val flag: String get() = rally.flag
    val isEpicStage: Boolean get() = stage.isEpicStage
    val isSuperSpecialStage: Boolean get() = stage.isSuperSpecialStage
}

class SessionPreferences(private val context: Context) {
    companion object {
        private val RALLY_ID = stringPreferencesKey("session_rally_id")
        private val RALLY_NAME = stringPreferencesKey("session_rally_name")
        private val STAGE_NAME = stringPreferencesKey("session_stage_name")
        private val STAGE_DISTANCE = doublePreferencesKey("session_stage_distance")
        private val WEATHER = stringPreferencesKey("session_weather")
        private val CAMERA = stringPreferencesKey("session_camera")
        private val CAR_NAME = stringPreferencesKey("session_car_name")
        private val CAR_CLASS = stringPreferencesKey("session_car_class")
        private val TRANSMISSION = stringPreferencesKey("session_transmission")
        private val NOTES = stringPreferencesKey("session_notes")
        private val STICKY_ENABLED = booleanPreferencesKey("session_sticky_enabled")
    }

    val sessionFlow: Flow<StageSessionConfig> = context.sessionDataStore.data.map { prefs ->
        val defaultRally = RallyPresets.RALLIES.first()
        val defaultStage = defaultRally.stages.first()

        val rId = prefs[RALLY_ID] ?: defaultRally.id
        val rName = prefs[RALLY_NAME] ?: defaultRally.name
        val sName = prefs[STAGE_NAME] ?: defaultStage.name
        val sDist = prefs[STAGE_DISTANCE] ?: defaultStage.distanceKm
        val weather = prefs[WEATHER] ?: "Clear / Sunny"
        val camera = prefs[CAMERA] ?: "Cockpit / Dash"
        val car = prefs[CAR_NAME] ?: "Rally Spec 4WD"
        val carClass = prefs[CAR_CLASS] ?: "Class 1 (Premier)"
        val trans = prefs[TRANSMISSION] ?: "Sequential"
        val notes = prefs[NOTES] ?: ""
        val sticky = prefs[STICKY_ENABLED] ?: true

        StageSessionConfig(
            rallyId = rId,
            rallyName = rName,
            stageName = sName,
            stageDistanceKm = sDist,
            weather = weather,
            camera = camera,
            carName = car,
            carClass = carClass,
            transmission = trans,
            notes = notes,
            stickyEnabled = sticky
        )
    }

    suspend fun saveSession(config: StageSessionConfig) {
        context.sessionDataStore.edit { prefs ->
            prefs[RALLY_ID] = config.rallyId
            prefs[RALLY_NAME] = config.rallyName
            prefs[STAGE_NAME] = config.stageName
            prefs[STAGE_DISTANCE] = config.stageDistanceKm
            prefs[WEATHER] = config.weather
            prefs[CAMERA] = config.camera
            prefs[CAR_NAME] = config.carName
            prefs[CAR_CLASS] = config.carClass
            prefs[TRANSMISSION] = config.transmission
            prefs[NOTES] = config.notes
            prefs[STICKY_ENABLED] = config.stickyEnabled
        }
    }

    suspend fun setStickyEnabled(enabled: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[STICKY_ENABLED] = enabled
        }
    }
}
