package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

data class ThemeConfig(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dynamicColor: Boolean = true
)

class ThemePreferences(private val context: Context) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")

    val themeConfigFlow: Flow<ThemeConfig> = context.themeDataStore.data.map { preferences ->
        val modeStr = preferences[themeModeKey] ?: AppThemeMode.DARK.name
        val mode = try {
            AppThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            AppThemeMode.DARK
        }
        val dynamic = preferences[dynamicColorKey] ?: true
        ThemeConfig(themeMode = mode, dynamicColor = dynamic)
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[dynamicColorKey] = enabled
        }
    }
}
