package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deskterm_prefs")

data class UserSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val fontSizeSp: Int = 13,
    val autoConnectEnabled: Boolean = true,
    val keepAliveSeconds: Int = 30,
    val hapticEnabled: Boolean = true,
    val autoScroll: Boolean = true
)

class AppPreferences(private val context: Context) {
    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size")
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val KEY_KEEP_ALIVE = intPreferencesKey("keep_alive")
        private val KEY_HAPTIC = booleanPreferencesKey("haptic")
        private val KEY_AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val themeModeStr = prefs[KEY_THEME_MODE] ?: AppThemeMode.DARK.name
        val themeMode = try {
            AppThemeMode.valueOf(themeModeStr)
        } catch (_: Exception) {
            AppThemeMode.DARK
        }

        UserSettings(
            themeMode = themeMode,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            fontSizeSp = prefs[KEY_FONT_SIZE] ?: 13,
            autoConnectEnabled = prefs[KEY_AUTO_CONNECT] ?: true,
            keepAliveSeconds = prefs[KEY_KEEP_ALIVE] ?: 30,
            hapticEnabled = prefs[KEY_HAPTIC] ?: true,
            autoScroll = prefs[KEY_AUTO_SCROLL] ?: true
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setFontSize(sizeSp: Int) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = sizeSp }
    }

    suspend fun setAutoConnectEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CONNECT] = enabled }
    }

    suspend fun setKeepAliveSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_KEEP_ALIVE] = seconds }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC] = enabled }
    }

    suspend fun setAutoScroll(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SCROLL] = enabled }
    }
}
