package com.example.viewmodel

import com.example.data.CalculatorThemeMode
import com.naveen.fx991dex.engine.*

/**
 * All 13 authentic German CASIO fx-991DE X ClassWiz MENU modes (1–9, A–D).
 */
enum class CalcMode(val keyCode: String, val germanTitle: String, val shortBadge: String) {
    CALCULATE("1", "Berechnungen", ""),
    COMPLEX("2", "Komplexe Zahlen", "CMPLX"),
    BASE_N("3", "Basis-N", "BASE-N"),
    MATRIX("4", "Matrizen", "MAT"),
    VECTOR("5", "Vektorrechnung", "VCT"),
    STATISTICS("6", "Statistik", "STAT"),
    DISTRIBUTION("7", "Verteilungsfkt.", "DIST"),
    SPREADSHEET("8", "Tabellenkalk.", "TABK"),
    TABLE("9", "Tabellen", "TAB"),
    EQUATION("A", "Gleichung/Funkt.", "GL"),
    INEQUALITY("B", "Ungleichungen", "UNGL"),
    VERIFY("C", "Berechn. prüfen", "PRÜF"),
    RATIO("D", "Verhältnisse", "VERH")
}

sealed class ActiveOverlay {
    data object None : ActiveOverlay()
    data class MenuGrid(val selectedIndex: Int = 0) : ActiveOverlay()
    data class SetupMenu(val selectedIndex: Int = 0, val subPage: SetupSubPage = SetupSubPage.ROOT) : ActiveOverlay()
    data class OptnMenu(val title: String, val items: List<OptnItem>, val selectedIndex: Int = 0) : ActiveOverlay()
    data class ConstMenu(val categoryIndex: Int? = null, val selectedIndex: Int = 0) : ActiveOverlay()
    data class ConvMenu(val categoryIndex: Int? = null, val selectedIndex: Int = 0) : ActiveOverlay()
    data object RecallMemory : ActiveOverlay()
    data class ResetConfirm(val selectedIndex: Int = 0) : ActiveOverlay()
    data class CalcPrompt(
        val variables: List<String>,
        val currentIndex: Int,
        val inputBuffer: String,
        val collectedValues: Map<String, Double>
    ) : ActiveOverlay()
    data class SolvePrompt(
        val inputBuffer: String,
        val resultText: String? = null
    ) : ActiveOverlay()
    data class QrSummary(val expressionText: String, val resultText: String) : ActiveOverlay()
}

enum class SetupSubPage {
    ROOT, ANGLE_UNIT, NUMBER_FORMAT, FRACTION_FORMAT, COMPLEX_FORMAT, THEME
}

data class OptnItem(
    val id: String,
    val label: String
)

data class HistoryEntry(
    val input: MathSequence,
    val result: SymbolicValue,
    val forceDecimal: Boolean
)

// --- Specialized Mode States (Modes 3–13) ---

data class BaseNState(
    val currentBase: NumberBase = NumberBase.DEC,
    val input: String = "",
    val resultValue: Int = 0,
    val error: String? = null
)

enum class MatrixSubView { CALC, DEFINE_SELECT, DEFINE_DIM, DEFINE_EDIT }

data class MatrixModeState(
    val subView: MatrixSubView = MatrixSubView.DEFINE_SELECT,
    val activeSlot: String = "MatA",
    val matrices: Map<String, Array<DoubleArray>> = mapOf(
        "MatA" to arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0)),
        "MatB" to arrayOf(doubleArrayOf(5.0, 6.0), doubleArrayOf(7.0, 8.0))
    ),
    val editRows: Int = 2,
    val editCols: Int = 2,
    val selectedRow: Int = 0,
    val selectedCol: Int = 0,
    val cellInput: String = ""
)

enum class VectorSubView { CALC, DEFINE_SELECT, DEFINE_DIM, DEFINE_EDIT }

data class VectorModeState(
    val subView: VectorSubView = VectorSubView.DEFINE_SELECT,
    val activeSlot: String = "VctA",
    val vectors: Map<String, DoubleArray> = mapOf(
        "VctA" to doubleArrayOf(1.0, 2.0, 3.0),
        "VctB" to doubleArrayOf(4.0, 5.0, 6.0)
    ),
    val editDim: Int = 3,
    val selectedComp: Int = 0,
    val cellInput: String = ""
)

enum class StatSubView { SELECT_TYPE, TABLE, RESULTS }

data class StatModeState(
    val subView: StatSubView = StatSubView.SELECT_TYPE,
    val statType: StatType = StatType.ONE_VAR,
    val rows: List<StatRow> = listOf(
        StatRow(2.0, 4.0, 1.0),
        StatRow(4.0, 7.0, 1.0),
        StatRow(6.0, 11.0, 1.0)
    ),
    val selectedRow: Int = 0,
    val selectedCol: Int = 0, // 0=x, 1=y (if twoVar)
    val cellInput: String = "",
    val oneVarStats: OneVarStats? = null,
    val regression: RegressionResult? = null
)

enum class DistSubView { SELECT_TYPE, MODE_SELECT, PARAMS, LIST_INPUT, RESULT }

data class DistributionModeState(
    val subView: DistSubView = DistSubView.SELECT_TYPE,
    val distType: DistributionType = DistributionType.NORMAL_PD,
    val isListMode: Boolean = false,
    val fields: Map<String, String> = mapOf(
        "x" to "0", "Lower" to "-1", "Upper" to "1", "Fläche" to "0,95",
        "σ" to "1", "μ" to "0", "N" to "10", "p" to "0,5", "λ" to "3"
    ),
    val activeFieldIndex: Int = 0,
    val listValues: List<String> = listOf("0", "1", "2", "3"),
    val selectedListRow: Int = 0,
    val editBuffer: String = "",
    val resultLines: List<Pair<String, String>> = emptyList(),
    val error: String? = null
)

data class SpreadsheetModeState(
    val cells: Map<String, String> = mapOf(
        "A1" to "10",
        "A2" to "20",
        "A3" to "30",
        "B1" to "=Summe(A1:A3)",
        "B2" to "=Mittelwert(A1:A3)"
    ),
    val evaluated: Map<String, Result<Double>> = SpreadsheetEngine.evaluateAll(
        mapOf("A1" to "10", "A2" to "20", "A3" to "30", "B1" to "=Summe(A1:A3)", "B2" to "=Mittelwert(A1:A3)")
    ),
    val selectedCol: Int = 0,
    val selectedRow: Int = 0,
    val editBuffer: String = "",
    val isEditing: Boolean = false
)

enum class TableSubView { EDIT_F, EDIT_G, RANGE, TABLE_VIEW }

data class TableModeState(
    val subView: TableSubView = TableSubView.EDIT_F,
    val fDoc: EditorDocument = AstEditor.emptyDocument(),
    val gDoc: EditorDocument = AstEditor.emptyDocument(),
    val startText: String = "-2",
    val endText: String = "2",
    val stepText: String = "1",
    val activeRangeField: Int = 0, // 0=Start, 1=Ende, 2=Inkrement
    val rangeBuffer: String = "",
    val rows: List<Triple<Double, Double, Double?>> = emptyList(),
    val selectedRow: Int = 0,
    val error: String? = null
)

enum class EquationSubView { SELECT_KIND, SELECT_SIZE, INPUT_LINEAR, INPUT_POLY, RESULT }

data class EquationModeState(
    val subView: EquationSubView = EquationSubView.SELECT_KIND,
    val isPolynomial: Boolean = false,
    val size: Int = 2, // 2..4 unknowns or degree 2..4
    val linearCoeffs: List<List<String>> = List(2) { List(3) { "0" } },
    val polyCoeffs: List<String> = listOf("1", "-3", "2"),
    val selectedRow: Int = 0,
    val selectedCol: Int = 0,
    val cellBuffer: String = "",
    val linearResult: LinearSystemSolution? = null,
    val polyResult: PolynomialSolution? = null
)

enum class InequalitySubView { SELECT_DEGREE, SELECT_OP, INPUT_COEFFS, RESULT }

data class InequalityModeState(
    val subView: InequalitySubView = InequalitySubView.SELECT_DEGREE,
    val degree: Int = 2,
    val op: EquationInequalityEngine.InequalityOp = EquationInequalityEngine.InequalityOp.GT,
    val coeffs: List<String> = listOf("1", "0", "-4"),
    val selectedCoeff: Int = 0,
    val cellBuffer: String = "",
    val resultText: String = ""
)

enum class RatioSubView { SELECT_FORM, INPUT, RESULT }

data class RatioModeState(
    val subView: RatioSubView = RatioSubView.SELECT_FORM,
    val solveFirstRight: Boolean = true, // true: a:b = X:d, false: a:b = c:X
    val aText: String = "3",
    val bText: String = "4",
    val knownText: String = "12",
    val activeField: Int = 0,
    val editBuffer: String = "",
    val result: SymbolicValue? = null,
    val forceDecimal: Boolean = false
)

/**
 * Root UI state for the entire CASIO fx-991DE X ClassWiz calculator.
 */
data class CalculatorUiState(
    val themeMode: CalculatorThemeMode = CalculatorThemeMode.MATTE_DARK,
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val formatConfig: DisplayFormatConfig = DisplayFormatConfig(),
    val mode: CalcMode = CalcMode.CALCULATE,
    val shiftActive: Boolean = false,
    val alphaActive: Boolean = false,
    val stoPending: Boolean = false,
    val insertMode: Boolean = false,
    val overlay: ActiveOverlay = ActiveOverlay.None,
    val document: EditorDocument = AstEditor.emptyDocument(),
    val undoStack: List<EditorDocument> = emptyList(),
    val currentResult: SymbolicValue? = null,
    val preDmsResult: SymbolicValue? = null,
    val resultForceDecimal: Boolean = false,
    val resultShowingDms: Boolean = false,
    val resultShowingFact: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val historyBrowseIndex: Int = -1,
    val variables: Map<String, Double> = mapOf(
        "A" to 0.0, "B" to 0.0, "C" to 0.0,
        "D" to 0.0, "E" to 0.0, "F" to 0.0,
        "X" to 0.0, "Y" to 0.0, "M" to 0.0
    ),
    val ans: SymbolicValue = SymbolicValue.ofLong(0),
    val preAns: SymbolicValue = SymbolicValue.ofLong(0),
    val statusBanner: String? = null,
    // Specialized mode states
    val baseN: BaseNState = BaseNState(),
    val matrixState: MatrixModeState = MatrixModeState(),
    val vectorState: VectorModeState = VectorModeState(),
    val statState: StatModeState = StatModeState(),
    val distState: DistributionModeState = DistributionModeState(),
    val sheetState: SpreadsheetModeState = SpreadsheetModeState(),
    val tableState: TableModeState = TableModeState(),
    val eqState: EquationModeState = EquationModeState(),
    val ineqState: InequalityModeState = InequalityModeState(),
    val ratioState: RatioModeState = RatioModeState()
) {
    val hasMemoryM: Boolean get() = kotlin.math.abs(variables["M"] ?: 0.0) > 1e-15
}
