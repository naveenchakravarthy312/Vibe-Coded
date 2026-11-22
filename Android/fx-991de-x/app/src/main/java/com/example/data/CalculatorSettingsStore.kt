package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.naveen.fx991dex.engine.AngleUnit
import com.naveen.fx991dex.engine.ComplexFormat
import com.naveen.fx991dex.engine.FractionFormat
import com.naveen.fx991dex.engine.NumberFormatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class CalculatorThemeMode(val germanName: String) {
    MATTE_DARK("Matte Dunkel"),
    CLASSIC_LCD("Klassisch LCD")
}

data class PersistedSettings(
    val themeMode: CalculatorThemeMode = CalculatorThemeMode.MATTE_DARK,
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val numberFormat: NumberFormatMode = NumberFormatMode.NORM1,
    val formatDigits: Int = 4,
    val fractionFormat: FractionFormat = FractionFormat.IMPROPER,
    val complexFormat: ComplexFormat = ComplexFormat.RECTANGULAR
)

private val Context.calcDataStore: DataStore<Preferences> by preferencesDataStore(name = "fx991dex_prefs")

class CalculatorSettingsStore(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ANGLE = stringPreferencesKey("angle_unit")
        val NUM_FMT = stringPreferencesKey("number_format")
        val DIGITS = intPreferencesKey("format_digits")
        val FRAC_FMT = stringPreferencesKey("fraction_format")
        val COMPLEX_FMT = stringPreferencesKey("complex_format")
    }

    val settingsFlow: Flow<PersistedSettings> = context.calcDataStore.data.map { prefs ->
        PersistedSettings(
            themeMode = prefs[Keys.THEME]?.let { runCatching { CalculatorThemeMode.valueOf(it) }.getOrNull() }
                ?: CalculatorThemeMode.MATTE_DARK,
            angleUnit = prefs[Keys.ANGLE]?.let { runCatching { AngleUnit.valueOf(it) }.getOrNull() }
                ?: AngleUnit.DEG,
            numberFormat = prefs[Keys.NUM_FMT]?.let { runCatching { NumberFormatMode.valueOf(it) }.getOrNull() }
                ?: NumberFormatMode.NORM1,
            formatDigits = prefs[Keys.DIGITS] ?: 4,
            fractionFormat = prefs[Keys.FRAC_FMT]?.let { runCatching { FractionFormat.valueOf(it) }.getOrNull() }
                ?: FractionFormat.IMPROPER,
            complexFormat = prefs[Keys.COMPLEX_FMT]?.let { runCatching { ComplexFormat.valueOf(it) }.getOrNull() }
                ?: ComplexFormat.RECTANGULAR
        )
    }

    suspend fun save(settings: PersistedSettings) {
        context.calcDataStore.edit { prefs ->
            prefs[Keys.THEME] = settings.themeMode.name
            prefs[Keys.ANGLE] = settings.angleUnit.name
            prefs[Keys.NUM_FMT] = settings.numberFormat.name
            prefs[Keys.DIGITS] = settings.formatDigits
            prefs[Keys.FRAC_FMT] = settings.fractionFormat.name
            prefs[Keys.COMPLEX_FMT] = settings.complexFormat.name
        }
    }
}
