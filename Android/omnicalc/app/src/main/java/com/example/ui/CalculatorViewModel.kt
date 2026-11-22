package com.example.ui

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CalculationEntity
import com.example.data.local.CalculationRepository
import com.example.engine.CalculatorEngine
import com.example.engine.UnitCategory
import com.example.engine.UnitConverter
import com.example.engine.UnitDefinition
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnicalc_settings")

enum class ActiveTab {
    CALCULATOR, CONVERTER, HISTORY
}

enum class HistoryFilter {
    ALL, CALCULATOR, CONVERTER, FAVORITES
}

data class QuickConversionItem(
    val unit: UnitDefinition,
    val valueFormatted: String
)

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CalculationRepository
    private val appContext = application.applicationContext

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    // App Navigation & Theme State
    private val _activeTab = MutableStateFlow(ActiveTab.CALCULATOR)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    val themeConfig: StateFlow<ThemeConfig> = combine(_themeMode, _dynamicColor) { mode, dynamic ->
        ThemeConfig(themeMode = mode, dynamicColor = dynamic)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeConfig())

    fun setActiveTab(tab: ActiveTab) {
        _activeTab.value = tab
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            appContext.dataStore.edit { prefs ->
                prefs[KEY_THEME_MODE] = mode.name
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            appContext.dataStore.edit { prefs ->
                prefs[KEY_DYNAMIC_COLOR] = enabled
            }
        }
    }

    fun toggleAmoledQuick() {
        val next = if (_themeMode.value == AppThemeMode.AMOLED) AppThemeMode.DARK else AppThemeMode.AMOLED
        setThemeMode(next)
    }

    // ----------------------------------------------------
    // CALCULATOR STATE
    // ----------------------------------------------------
    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    private val _previewResult = MutableStateFlow("")
    val previewResult: StateFlow<String> = _previewResult.asStateFlow()

    private val _angleMode = MutableStateFlow(CalculatorEngine.AngleMode.DEG)
    val angleMode: StateFlow<CalculatorEngine.AngleMode> = _angleMode.asStateFlow()

    private val _isScientificExpanded = MutableStateFlow(false)
    val isScientificExpanded: StateFlow<Boolean> = _isScientificExpanded.asStateFlow()

    private val _memoryValue = MutableStateFlow(0.0)
    val memoryValue: StateFlow<Double> = _memoryValue.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun toggleAngleMode() {
        _angleMode.value = if (_angleMode.value == CalculatorEngine.AngleMode.DEG) {
            CalculatorEngine.AngleMode.RAD
        } else {
            CalculatorEngine.AngleMode.DEG
        }
        updatePreview()
    }

    fun toggleScientific() {
        _isScientificExpanded.value = !_isScientificExpanded.value
    }

    fun onDigit(d: String) {
        _errorMessage.value = null
        val current = _expression.value
        val trimmed = current.trimEnd()
        val needsMultiply = trimmed.isNotEmpty() && trimmed.last() in listOf(')', 'π', 'e', '!', '%')
        _expression.value = if (needsMultiply) "$trimmed × $d" else current + d
        updatePreview()
    }

    fun onDecimal() {
        _errorMessage.value = null
        val current = _expression.value
        if (current.isEmpty()) {
            _expression.value = "0."
        } else {
            val trimmed = current.trimEnd()
            if (trimmed.isNotEmpty() && trimmed.last() in listOf(')', 'π', 'e', '!', '%')) {
                _expression.value = "$trimmed × 0."
            } else {
                // Find last token to avoid multiple decimals in same number
                val lastToken = current.takeLastWhile { it.isDigit() || it == '.' }
                if (!lastToken.contains('.')) {
                    val lastChar = current.last()
                    if (lastChar.isDigit()) {
                        _expression.value = "$current."
                    } else if (lastChar == ' ') {
                        _expression.value = "${current}0."
                    } else {
                        _expression.value = "$current 0."
                    }
                }
            }
        }
        updatePreview()
    }

    fun onOperator(op: String) {
        _errorMessage.value = null
        val current = _expression.value
        val trimmed = current.trimEnd()

        if (op == "!" || op == "%") {
            if (trimmed.isEmpty()) return
            val last = trimmed.last()
            if (last.isDigit() || last in listOf(')', 'π', 'e', '!', '%')) {
                _expression.value = "$trimmed$op"
                updatePreview()
            }
            return
        }

        if (trimmed.isEmpty()) {
            if (op == "-") {
                _expression.value = "-"
            }
            return
        }

        if (trimmed == "-") return

        val last = trimmed.last()
        if (last in listOf('+', '-', '×', '÷', '^')) {
            val prefix = trimmed.dropLast(1).trimEnd()
            _expression.value = if (prefix.isEmpty()) {
                if (op == "-") "-" else ""
            } else {
                "$prefix $op "
            }
        } else if (last == '(') {
            if (op == "-") {
                _expression.value = "$trimmed-"
            }
        } else {
            _expression.value = "$trimmed $op "
        }
        updatePreview()
    }

    fun onFunction(fn: String) {
        _errorMessage.value = null
        val current = _expression.value
        val trimmed = current.trimEnd()
        val needsMultiply = trimmed.isNotEmpty() && (trimmed.last().isDigit() || trimmed.last() in listOf(')', 'π', 'e', '!', '%'))
        val base = if (needsMultiply) "$trimmed × " else current
        _expression.value = if (fn == "1/") {
            "${base}1 ÷ ("
        } else {
            "$base$fn("
        }
        updatePreview()
    }

    fun onConstant(c: String) {
        _errorMessage.value = null
        val current = _expression.value
        val trimmed = current.trimEnd()
        val needsMultiply = trimmed.isNotEmpty() && (trimmed.last().isDigit() || trimmed.last() in listOf('.', ')', 'π', 'e', '!', '%'))
        _expression.value = if (needsMultiply) "$trimmed × $c" else current + c
        updatePreview()
    }

    fun onParenthesis() {
        _errorMessage.value = null
        val current = _expression.value
        val openCount = current.count { it == '(' }
        val closeCount = current.count { it == ')' }

        if (current.isEmpty() || current.last() in listOf('+', '-', '×', '÷', '(', '^', ' ')) {
            _expression.value = "$current("
        } else if (openCount > closeCount) {
            _expression.value = "$current)"
        } else {
            _expression.value = "$current × ("
        }
        updatePreview()
    }

    fun onBackspace() {
        _errorMessage.value = null
        val current = _expression.value
        if (current.isNotEmpty()) {
            // Clean multi-character functions or spaced operators if removing from the end
            val toRemove = when {
                current.endsWith("asin(") || current.endsWith("acos(") || current.endsWith("atan(") -> 5
                current.endsWith("sqrt(") || current.endsWith("cbrt(") || current.endsWith("1 ÷ (") -> 5
                current.endsWith("sin(") || current.endsWith("cos(") || current.endsWith("tan(") -> 4
                current.endsWith("log(") || current.endsWith("abs(") -> 4
                current.endsWith("ln(") -> 3
                current.length >= 3 && current.endsWith(" ") && current[current.length - 2] in listOf('+', '-', '×', '÷', '%', '^') && current[current.length - 3] == ' ' -> 3
                current.endsWith(" ") -> 1
                else -> 1
            }
            _expression.value = current.dropLast(toRemove).trimEnd()
            updatePreview()
        }
    }

    fun onClear() {
        _expression.value = ""
        _previewResult.value = ""
        _errorMessage.value = null
    }

    fun onEquals() {
        val current = _expression.value.trim()
        if (current.isEmpty()) return

        when (val result = CalculatorEngine.evaluate(current, _angleMode.value)) {
            is CalculatorEngine.EvalResult.Success -> {
                val formatted = result.formatted
                _errorMessage.value = null

                // Save to Room DB history asynchronously
                viewModelScope.launch(Dispatchers.IO) {
                    repository.insert(
                        CalculationEntity(
                            expression = current,
                            result = formatted,
                            category = CalculationEntity.CATEGORY_CALCULATOR,
                            details = "Result: $formatted"
                        )
                    )
                }

                _expression.value = formatted
                _previewResult.value = ""
            }
            is CalculatorEngine.EvalResult.Error -> {
                _errorMessage.value = result.message
            }
        }
    }

    // Memory operations
    fun memoryAdd() {
        val current = _expression.value
        when (val res = CalculatorEngine.evaluate(current, _angleMode.value)) {
            is CalculatorEngine.EvalResult.Success -> {
                _memoryValue.value += res.value
            }
            else -> {}
        }
    }

    fun memorySub() {
        val current = _expression.value
        when (val res = CalculatorEngine.evaluate(current, _angleMode.value)) {
            is CalculatorEngine.EvalResult.Success -> {
                _memoryValue.value -= res.value
            }
            else -> {}
        }
    }

    fun memoryRecall() {
        val mem = CalculatorEngine.formatResult(_memoryValue.value)
        val current = _expression.value
        val trimmed = current.trimEnd()
        val needsMultiply = trimmed.isNotEmpty() && (trimmed.last().isDigit() || trimmed.last() in listOf(')', 'π', 'e', '!'))
        _expression.value = if (needsMultiply) "$trimmed × $mem" else current + mem
        updatePreview()
    }

    fun memoryClear() {
        _memoryValue.value = 0.0
    }

    private fun updatePreview() {
        val current = _expression.value.trim()
        if (current.isEmpty() || current.all { it.isDigit() || it == '.' }) {
            _previewResult.value = ""
            return
        }

        when (val res = CalculatorEngine.evaluate(current, _angleMode.value)) {
            is CalculatorEngine.EvalResult.Success -> {
                _previewResult.value = "= ${res.formatted}"
            }
            is CalculatorEngine.EvalResult.Error -> {
                // If it's a transient typing error (e.g., waiting for close paren), keep preview quiet
                _previewResult.value = ""
            }
        }
    }

    // ----------------------------------------------------
    // MEASUREMENT CONVERTER STATE
    // ----------------------------------------------------
    private val _selectedCategory = MutableStateFlow(UnitCategory.LENGTH)
    val selectedCategory: StateFlow<UnitCategory> = _selectedCategory.asStateFlow()

    private val _fromUnit = MutableStateFlow(UnitConverter.getUnitsForCategory(UnitCategory.LENGTH)[1]) // km
    val fromUnit: StateFlow<UnitDefinition> = _fromUnit.asStateFlow()

    private val _toUnit = MutableStateFlow(UnitConverter.getUnitsForCategory(UnitCategory.LENGTH)[6]) // mi
    val toUnit: StateFlow<UnitDefinition> = _toUnit.asStateFlow()

    private val _converterInput = MutableStateFlow("1")
    val converterInput: StateFlow<String> = _converterInput.asStateFlow()

    private val _convertedOutput = MutableStateFlow("0.621371")
    val convertedOutput: StateFlow<String> = _convertedOutput.asStateFlow()

    private val _quickComparisons = MutableStateFlow<List<QuickConversionItem>>(emptyList())
    val quickComparisons: StateFlow<List<QuickConversionItem>> = _quickComparisons.asStateFlow()

    init {
        val database = AppDatabase.getInstance(application)
        repository = CalculationRepository(database.calculationDao())

        // Populate initial unit comparisons immediately
        recalculateConversion()

        // Observe persisted theme preferences from Jetpack DataStore
        viewModelScope.launch(Dispatchers.IO) {
            appContext.dataStore.data.collect { prefs ->
                val savedModeStr = prefs[KEY_THEME_MODE]
                val savedMode = savedModeStr?.let {
                    runCatching { AppThemeMode.valueOf(it) }.getOrNull()
                } ?: AppThemeMode.SYSTEM
                val savedDynamic = prefs[KEY_DYNAMIC_COLOR] ?: true
                _themeMode.value = savedMode
                _dynamicColor.value = savedDynamic
            }
        }
    }

    fun selectCategory(category: UnitCategory) {
        _selectedCategory.value = category
        val units = UnitConverter.getUnitsForCategory(category)
        _fromUnit.value = units.firstOrNull() ?: units[0]
        _toUnit.value = if (units.size > 1) units[1] else units[0]
        recalculateConversion()
    }

    fun setFromUnit(unit: UnitDefinition) {
        _fromUnit.value = unit
        recalculateConversion()
    }

    fun setToUnit(unit: UnitDefinition) {
        _toUnit.value = unit
        recalculateConversion()
    }

    fun swapUnits() {
        val temp = _fromUnit.value
        _fromUnit.value = _toUnit.value
        _toUnit.value = temp
        recalculateConversion()
    }

    fun onConverterKey(key: String) {
        val current = _converterInput.value
        when (key) {
            "CLEAR" -> _converterInput.value = "0"
            "BACKSPACE" -> {
                if (current.length <= 1 || (current.length == 2 && current.startsWith("-"))) {
                    _converterInput.value = "0"
                } else {
                    _converterInput.value = current.dropLast(1)
                }
            }
            "." -> {
                if (!current.contains(".")) {
                    _converterInput.value = "$current."
                }
            }
            "+/-" -> {
                if (current.startsWith("-")) {
                    _converterInput.value = current.removePrefix("-")
                } else if (current != "0") {
                    _converterInput.value = "-$current"
                }
            }
            else -> {
                // Digit 0-9
                if (current == "0") {
                    _converterInput.value = key
                } else {
                    if (current.length < 15) {
                        _converterInput.value = current + key
                    }
                }
            }
        }
        recalculateConversion()
    }

    private fun recalculateConversion() {
        val inputDouble = _converterInput.value.toDoubleOrNull() ?: 0.0
        val converted = UnitConverter.convert(inputDouble, _fromUnit.value, _toUnit.value)
        _convertedOutput.value = UnitConverter.formatUnitValue(converted)

        // Compute quick comparisons for all units in category
        val allUnits = UnitConverter.getUnitsForCategory(_selectedCategory.value)
        _quickComparisons.value = allUnits.map { unit ->
            val valForUnit = UnitConverter.convert(inputDouble, _fromUnit.value, unit)
            QuickConversionItem(
                unit = unit,
                valueFormatted = UnitConverter.formatUnitValue(valForUnit)
            )
        }
    }

    fun saveConversionToHistory() {
        val inputStr = _converterInput.value
        val from = _fromUnit.value
        val to = _toUnit.value
        val outStr = _convertedOutput.value
        val expressionText = "$inputStr ${from.symbol} → ${to.name}"
        val resultText = "$outStr ${to.symbol}"

        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(
                CalculationEntity(
                    expression = expressionText,
                    result = resultText,
                    category = CalculationEntity.CATEGORY_CONVERTER,
                    details = "${_selectedCategory.value.title}: $inputStr ${from.name} = $outStr ${to.name}"
                )
            )
        }
    }

    fun pushConvertedToCalculator(value: String) {
        _expression.value = value
        _activeTab.value = ActiveTab.CALCULATOR
        updatePreview()
    }

    // ----------------------------------------------------
    // HISTORY LOG STATE & OPERATIONS
    // ----------------------------------------------------
    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val historyList: StateFlow<List<CalculationEntity>> = combine(
        repository.allHistory,
        _historyFilter,
        _searchQuery
    ) { all, filter, query ->
        var filtered = when (filter) {
            HistoryFilter.ALL -> all
            HistoryFilter.CALCULATOR -> all.filter { it.category == CalculationEntity.CATEGORY_CALCULATOR }
            HistoryFilter.CONVERTER -> all.filter { it.category == CalculationEntity.CATEGORY_CONVERTER }
            HistoryFilter.FAVORITES -> all.filter { it.isFavorite }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter {
                it.expression.lowercase().contains(q) ||
                it.result.lowercase().contains(q) ||
                it.note.lowercase().contains(q) ||
                it.details.lowercase().contains(q)
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(item: CalculationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(item.id, item.isFavorite)
        }
    }

    fun updateItemNote(item: CalculationEntity, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateNote(item.id, note.trim())
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    fun clearFilteredHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            when (_historyFilter.value) {
                HistoryFilter.ALL -> repository.clearAll()
                HistoryFilter.CALCULATOR -> repository.clearByCategory(CalculationEntity.CATEGORY_CALCULATOR)
                HistoryFilter.CONVERTER -> repository.clearByCategory(CalculationEntity.CATEGORY_CONVERTER)
                HistoryFilter.FAVORITES -> repository.clearFavorites()
            }
        }
    }

    fun exportHistoryToJson(): String {
        val items = historyList.value
        val jsonArray = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("expression", item.expression)
                put("result", item.result)
                put("category", item.category)
                put("details", item.details)
                put("note", item.note)
                put("isFavorite", item.isFavorite)
                put("timestamp", item.timestamp)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    fun importHistoryFromJson(jsonString: String, onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val importedItems = mutableListOf<CalculationEntity>()
            runCatching {
                val trimmed = jsonString.trim()
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val expr = obj.optString("expression", "")
                        val res = obj.optString("result", "")
                        if (expr.isNotBlank() && res.isNotBlank()) {
                            importedItems.add(
                                CalculationEntity(
                                    expression = expr,
                                    result = res,
                                    category = obj.optString("category", CalculationEntity.CATEGORY_CALCULATOR),
                                    details = obj.optString("details", ""),
                                    note = obj.optString("note", ""),
                                    isFavorite = obj.optBoolean("isFavorite", false),
                                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                                )
                            )
                        }
                    }
                } else {
                    // Fallback: line-by-line text import ("expression = result")
                    trimmed.lines().forEach { line ->
                        val parts = line.split("=")
                        if (parts.size == 2) {
                            val expr = parts[0].replace(Regex("^\\[.*?]\\s*"), "").trim()
                            val res = parts[1].trim()
                            if (expr.isNotBlank() && res.isNotBlank()) {
                                importedItems.add(
                                    CalculationEntity(
                                        expression = expr,
                                        result = res,
                                        category = CalculationEntity.CATEGORY_CALCULATOR
                                    )
                                )
                            }
                        }
                    }
                }
                if (importedItems.isNotEmpty()) {
                    repository.insertAll(importedItems)
                }
            }
            withContext(Dispatchers.Main) {
                onResult(importedItems.size)
            }
        }
    }

    fun recallExpression(expr: String) {
        _expression.value = expr
        _activeTab.value = ActiveTab.CALCULATOR
        updatePreview()
    }

    fun recallResult(res: String) {
        // Extract numeric part if recalling a unit conversion result like "0.621371 mi"
        val numericRes = res.trim().takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' || it == 'E' }
            .ifEmpty { res.trim() }
        val current = _expression.value
        val trimmed = current.trimEnd()
        val needsMultiply = trimmed.isNotEmpty() && (trimmed.last().isDigit() || trimmed.last() in listOf(')', 'π', 'e', '!', '%'))
        _expression.value = if (needsMultiply) "$trimmed × $numericRes" else current + numericRes
        _activeTab.value = ActiveTab.CALCULATOR
        updatePreview()
    }
}
