package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CalculatorSettingsStore
import com.example.data.CalculatorThemeMode
import com.example.data.PersistedSettings
import com.naveen.fx991dex.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToLong

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = CalculatorSettingsStore(application.applicationContext)

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { saved ->
                _uiState.update { state ->
                    state.copy(
                        themeMode = saved.themeMode,
                        angleUnit = saved.angleUnit,
                        formatConfig = state.formatConfig.copy(
                            numberFormat = saved.numberFormat,
                            digits = saved.formatDigits,
                            fractionFormat = saved.fractionFormat,
                            complexFormat = saved.complexFormat
                        )
                    )
                }
            }
        }
    }

    private fun persistSettings(state: CalculatorUiState) {
        viewModelScope.launch {
            settingsStore.save(
                PersistedSettings(
                    themeMode = state.themeMode,
                    angleUnit = state.angleUnit,
                    numberFormat = state.formatConfig.numberFormat,
                    formatDigits = state.formatConfig.digits,
                    fractionFormat = state.formatConfig.fractionFormat,
                    complexFormat = state.formatConfig.complexFormat
                )
            )
        }
    }

    fun dismissOverlay() {
        _uiState.update { it.copy(overlay = ActiveOverlay.None, shiftActive = false, alphaActive = false) }
    }

    fun selectMode(mode: CalcMode) {
        _uiState.update { s ->
            s.copy(
                mode = mode,
                overlay = ActiveOverlay.None,
                shiftActive = false,
                alphaActive = false,
                stoPending = false,
                document = AstEditor.emptyDocument(),
                currentResult = null,
                statusBanner = null,
                matrixState = if (mode == CalcMode.MATRIX) s.matrixState.copy(subView = MatrixSubView.DEFINE_SELECT) else s.matrixState,
                vectorState = if (mode == CalcMode.VECTOR) s.vectorState.copy(subView = VectorSubView.DEFINE_SELECT) else s.vectorState,
                statState = if (mode == CalcMode.STATISTICS) s.statState.copy(subView = StatSubView.SELECT_TYPE) else s.statState,
                distState = if (mode == CalcMode.DISTRIBUTION) s.distState.copy(subView = DistSubView.SELECT_TYPE) else s.distState,
                tableState = if (mode == CalcMode.TABLE) s.tableState.copy(subView = TableSubView.EDIT_F) else s.tableState,
                eqState = if (mode == CalcMode.EQUATION) s.eqState.copy(subView = EquationSubView.SELECT_KIND) else s.eqState,
                ineqState = if (mode == CalcMode.INEQUALITY) s.ineqState.copy(subView = InequalitySubView.SELECT_DEGREE) else s.ineqState,
                ratioState = if (mode == CalcMode.RATIO) s.ratioState.copy(subView = RatioSubView.SELECT_FORM) else s.ratioState
            )
        }
    }

    fun onKeyPress(key: KeyId) {
        val state = _uiState.value

        // Global control keys always handled first
        if (key == KeyId.SHIFT) {
            _uiState.update { it.copy(shiftActive = !it.shiftActive, alphaActive = false, stoPending = false) }
            return
        }
        if (key == KeyId.ALPHA) {
            _uiState.update { it.copy(alphaActive = !it.alphaActive, shiftActive = false, stoPending = false) }
            return
        }
        if (key == KeyId.ON) {
            _uiState.update {
                it.copy(
                    overlay = ActiveOverlay.None,
                    shiftActive = false,
                    alphaActive = false,
                    stoPending = false,
                    document = AstEditor.emptyDocument(),
                    currentResult = null,
                    statusBanner = null
                )
            }
            return
        }
        if (key == KeyId.MENU_SETUP) {
            if (state.shiftActive) {
                _uiState.update { it.copy(overlay = ActiveOverlay.SetupMenu(0, SetupSubPage.ROOT), shiftActive = false, alphaActive = false) }
            } else {
                val idx = CalcMode.entries.indexOf(state.mode).coerceAtLeast(0)
                _uiState.update { it.copy(overlay = ActiveOverlay.MenuGrid(idx), shiftActive = false, alphaActive = false) }
            }
            return
        }

        // If an overlay is open, route keys to the overlay first
        if (state.overlay !is ActiveOverlay.None) {
            handleOverlayKey(key)
            return
        }

        val shift = state.shiftActive
        val alpha = state.alphaActive

        // Global SHIFT+9 (RESET), SHIFT+7 (CONST), SHIFT+8 (CONV), OPTN, SHIFT+STO (RECALL)
        if (shift && key == KeyId.NUM_9) {
            _uiState.update { it.copy(overlay = ActiveOverlay.ResetConfirm(0), shiftActive = false) }
            return
        }
        if (shift && key == KeyId.NUM_7) {
            _uiState.update { it.copy(overlay = ActiveOverlay.ConstMenu(null, 0), shiftActive = false) }
            return
        }
        if (shift && key == KeyId.NUM_8) {
            _uiState.update { it.copy(overlay = ActiveOverlay.ConvMenu(null, 0), shiftActive = false) }
            return
        }
        if (key == KeyId.OPTN) {
            if (shift) {
                openQrOverlay()
            } else {
                openOptnMenu()
            }
            return
        }
        if (key == KeyId.STO) {
            if (shift) {
                _uiState.update { it.copy(overlay = ActiveOverlay.RecallMemory, shiftActive = false) }
            } else {
                _uiState.update { it.copy(stoPending = !it.stoPending, shiftActive = false, alphaActive = false) }
            }
            return
        }

        // Handle pending STO -> variable assignment
        if (state.stoPending) {
            val varName = keyToVariableName(key)
            if (varName != null) {
                storeIntoVariable(varName)
                return
            } else {
                _uiState.update { it.copy(stoPending = false) }
            }
        }

        // Delegate to SpecializedModeHandler when inside modes 3-11 or 13
        if (SpecializedModeHandler.isSpecializedInputMode(state)) {
            _uiState.update { s ->
                val next = SpecializedModeHandler.handleKey(s, key, shift, alpha)
                next.copy(shiftActive = false, alphaActive = false)
            }
            return
        }

        // AST Modes: Mode 1 (Berechnungen), Mode 2 (Komplexe Zahlen), Mode 4/5 Calc View, Mode 12 (Berechn. prüfen)
        handleAstModeKey(key, shift, alpha)
    }

    private fun handleAstModeKey(key: KeyId, shift: Boolean, alpha: Boolean) {
        val s = _uiState.value

        // Navigation & History
        when (key) {
            KeyId.LEFT -> {
                _uiState.update {
                    it.copy(
                        document = AstEditor.moveLeft(it.document),
                        shiftActive = false, alphaActive = false, statusBanner = null
                    )
                }
                return
            }
            KeyId.RIGHT -> {
                _uiState.update {
                    it.copy(
                        document = AstEditor.moveRight(it.document),
                        shiftActive = false, alphaActive = false, statusBanner = null
                    )
                }
                return
            }
            KeyId.UP -> {
                val moved = AstEditor.moveUp(s.document)
                if (moved != null) {
                    _uiState.update { it.copy(document = moved, shiftActive = false, alphaActive = false) }
                } else if (s.history.isNotEmpty()) {
                    val nextIdx = if (s.historyBrowseIndex == -1) s.history.lastIndex else (s.historyBrowseIndex - 1).coerceAtLeast(0)
                    val entry = s.history[nextIdx]
                    _uiState.update {
                        it.copy(
                            document = EditorDocument(entry.input, CursorLocation(entry.input.id, entry.input.nodes.size)),
                            currentResult = entry.result,
                            resultForceDecimal = entry.forceDecimal,
                            historyBrowseIndex = nextIdx,
                            shiftActive = false, alphaActive = false
                        )
                    }
                }
                return
            }
            KeyId.DOWN -> {
                val moved = AstEditor.moveDown(s.document)
                if (moved != null) {
                    _uiState.update { it.copy(document = moved, shiftActive = false, alphaActive = false) }
                } else if (s.historyBrowseIndex != -1) {
                    val nextIdx = s.historyBrowseIndex + 1
                    if (nextIdx >= s.history.size) {
                        _uiState.update {
                            it.copy(
                                document = AstEditor.emptyDocument(),
                                currentResult = null,
                                historyBrowseIndex = -1,
                                shiftActive = false, alphaActive = false
                            )
                        }
                    } else {
                        val entry = s.history[nextIdx]
                        _uiState.update {
                            it.copy(
                                document = EditorDocument(entry.input, CursorLocation(entry.input.id, entry.input.nodes.size)),
                                currentResult = entry.result,
                                resultForceDecimal = entry.forceDecimal,
                                historyBrowseIndex = nextIdx,
                                shiftActive = false, alphaActive = false
                            )
                        }
                    }
                }
                return
            }
            KeyId.AC -> {
                _uiState.update {
                    it.copy(
                        document = AstEditor.emptyDocument(),
                        currentResult = null,
                        resultForceDecimal = false,
                        resultShowingDms = false,
                        resultShowingFact = false,
                        formatConfig = it.formatConfig.copy(engExponentOffset = null),
                        shiftActive = false, alphaActive = false,
                        historyBrowseIndex = -1,
                        statusBanner = if (shift) "AUS (Ruhezustand)" else null
                    )
                }
                return
            }
            KeyId.DEL -> {
                if (alpha) {
                    // UNDO
                    if (s.undoStack.isNotEmpty()) {
                        val prev = s.undoStack.last()
                        _uiState.update {
                            it.copy(document = prev, undoStack = it.undoStack.dropLast(1), alphaActive = false)
                        }
                    } else {
                        _uiState.update { it.copy(alphaActive = false) }
                    }
                } else if (shift) {
                    _uiState.update { it.copy(insertMode = !it.insertMode, shiftActive = false) }
                } else {
                    pushUndoAndEdit { AstEditor.deleteBeforeCursor(it) }
                }
                return
            }
            KeyId.EQUALS -> {
                evaluateCurrentDocument(forceDecimal = shift)
                return
            }
            KeyId.SD -> {
                if (alpha) {
                    insertTokenAutoAns("Y", TokenKind.VARIABLE)
                    return
                }
                if (shift) {
                    // Toggle improper (d/c) <-> mixed (a b/c) fraction display
                    _uiState.update {
                        val nextFmt = if (it.formatConfig.fractionFormat == FractionFormat.IMPROPER) {
                            FractionFormat.MIXED
                        } else FractionFormat.IMPROPER
                        val updated = it.copy(
                            formatConfig = it.formatConfig.copy(fractionFormat = nextFmt, engExponentOffset = null),
                            resultForceDecimal = false,
                            resultShowingFact = false,
                            resultShowingDms = false,
                            preDmsResult = null,
                            shiftActive = false
                        )
                        persistSettings(updated)
                        updated
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            resultForceDecimal = !it.resultForceDecimal,
                            resultShowingFact = false,
                            resultShowingDms = false,
                            preDmsResult = null,
                            formatConfig = it.formatConfig.copy(engExponentOffset = null),
                            shiftActive = false
                        )
                    }
                }
                return
            }
            KeyId.ENG -> {
                if (alpha) {
                    insertTokenAutoAns("i", TokenKind.CONSTANT)
                    return
                }
                val res = s.currentResult
                if (res != null && res !is SymbolicValue.Error) {
                    val v = abs(res.toDouble())
                    val curExp = s.formatConfig.engExponentOffset ?: if (v == 0.0) 0 else (floor(log10(v) / 3.0).toInt() * 3)
                    val nextExp = if (shift) curExp + 3 else curExp - 3
                    _uiState.update {
                        it.copy(
                            resultForceDecimal = true,
                            formatConfig = it.formatConfig.copy(engExponentOffset = nextExp),
                            shiftActive = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(shiftActive = false) }
                }
                return
            }
            KeyId.M_PLUS -> {
                if (alpha) {
                    insertTokenAutoAns("M", TokenKind.VARIABLE)
                    return
                }
                val evaluated = evaluateToValue() ?: return
                if (evaluated is SymbolicValue.Error) {
                    _uiState.update { it.copy(currentResult = evaluated, shiftActive = false, statusBanner = null) }
                    return
                }
                val delta = evaluated.toDouble()
                if (!delta.isFinite()) {
                    _uiState.update { it.copy(currentResult = SymbolicValue.Error("Math FEHLER"), shiftActive = false) }
                    return
                }
                val curM = s.variables["M"] ?: 0.0
                val nextM = if (shift) curM - delta else curM + delta
                _uiState.update {
                    it.copy(
                        variables = it.variables + ("M" to nextM),
                        currentResult = evaluated,
                        ans = evaluated,
                        shiftActive = false,
                        statusBanner = if (shift) "M − Ans" else "M + Ans"
                    )
                }
                return
            }
            KeyId.CALC -> {
                if (alpha) {
                    pushUndoAndEdit { AstEditor.insertToken(it, "=", TokenKind.COMPARE) }
                } else if (shift) {
                    // SOLVE
                    val guess = SymbolicFormatter.formatGermanNumber(s.variables["X"] ?: 1.0)
                    _uiState.update {
                        it.copy(overlay = ActiveOverlay.SolvePrompt(inputBuffer = guess), shiftActive = false)
                    }
                } else {
                    // CALC variable prompt
                    val vars = AstEditor.extractVariables(s.document.root)
                    if (vars.isEmpty()) {
                        evaluateCurrentDocument(forceDecimal = false)
                    } else {
                        val firstVal = SymbolicFormatter.formatGermanNumber(s.variables[vars.first()] ?: 0.0)
                        _uiState.update {
                            it.copy(
                                overlay = ActiveOverlay.CalcPrompt(
                                    variables = vars,
                                    currentIndex = 0,
                                    inputBuffer = firstVal,
                                    collectedValues = emptyMap()
                                ),
                                shiftActive = false
                            )
                        }
                    }
                }
                return
            }
            KeyId.DMS -> {
                if (alpha) {
                    insertTokenAutoAns("B", TokenKind.VARIABLE)
                } else if (shift) {
                    // FACT: Prime Factorization
                    val res = s.currentResult ?: evaluateToValue()
                    if (res != null && res !is SymbolicValue.Error) {
                        val d = res.toDouble()
                        val rounded = d.roundToLong()
                        if (abs(d - rounded) < 1e-9 && abs(rounded) in 2L..9_999_999_999L) {
                            val factors = Rational.primeFactorize(BigInteger.valueOf(rounded))
                            _uiState.update {
                                it.copy(
                                    currentResult = SymbolicValue.PrimeFactorized(rounded < 0, factors, BigInteger.valueOf(rounded)),
                                    resultForceDecimal = false,
                                    resultShowingFact = true,
                                    shiftActive = false
                                )
                            }
                        } else {
                            _uiState.update { it.copy(shiftActive = false, statusBanner = "FACT: Ganze Zahl erforderlich") }
                        }
                    }
                } else {
                    // Toggle DMS display when viewing a result, or insert ° into expression
                    if (s.currentResult != null && s.historyBrowseIndex == -1 && s.currentResult !is SymbolicValue.Error) {
                        if (!s.resultShowingDms) {
                            _uiState.update {
                                it.copy(
                                    preDmsResult = s.currentResult,
                                    currentResult = SymbolicValue.DmsValue(s.currentResult.toDouble()),
                                    resultShowingDms = true,
                                    shiftActive = false
                                )
                            }
                        } else {
                            _uiState.update {
                                val restored = s.preDmsResult ?: SymbolicValue.ofDouble(s.currentResult.toDouble())
                                it.copy(
                                    currentResult = restored,
                                    preDmsResult = null,
                                    resultShowingDms = false,
                                    shiftActive = false
                                )
                            }
                        }
                    } else {
                        pushUndoAndEdit { AstEditor.insertToken(it, "°", TokenKind.SYMBOL) }
                    }
                }
                return
            }
            else -> {}
        }

        // ALPHA variable & special function keys
        if (alpha) {
            when (key) {
                KeyId.NEGATE -> insertTokenAutoAns("A", TokenKind.VARIABLE)
                KeyId.RECIPROCAL -> insertTokenAutoAns("C", TokenKind.VARIABLE)
                KeyId.SIN -> insertTokenAutoAns("D", TokenKind.VARIABLE)
                KeyId.COS -> insertTokenAutoAns("E", TokenKind.VARIABLE)
                KeyId.TAN -> insertTokenAutoAns("F", TokenKind.VARIABLE)
                KeyId.RPAREN -> insertTokenAutoAns("X", TokenKind.VARIABLE)
                KeyId.X_VAR -> pushUndoAndEdit { AstEditor.insertSumProd(it, isProduct = true) } // Π
                KeyId.MUL -> insertTokenAutoAns("GCD(", TokenKind.FUNCTION)
                KeyId.DIV -> insertTokenAutoAns("LCM(", TokenKind.FUNCTION)
                KeyId.PLUS -> insertTokenAutoAns("Int(", TokenKind.FUNCTION)
                KeyId.MINUS -> insertTokenAutoAns("Intg(", TokenKind.FUNCTION)
                KeyId.DECIMAL -> insertTokenAutoAns("RanInt#(", TokenKind.FUNCTION)
                KeyId.EXP10 -> insertTokenAutoAns("e", TokenKind.CONSTANT)
                KeyId.ANS -> insertTokenAutoAns("PreAns", TokenKind.CONSTANT)
                else -> _uiState.update { it.copy(alphaActive = false) }
            }
            return
        }

        // SHIFT scientific & operator keys
        if (shift) {
            when (key) {
                KeyId.INTEGRAL -> pushUndoAndEdit { AstEditor.insertDerivative(it) }
                KeyId.X_VAR -> pushUndoAndEdit { AstEditor.insertSumProd(it, isProduct = false) } // Σ
                KeyId.FRAC -> pushUndoAndEdit { AstEditor.insertMixedFraction(it) }
                KeyId.SQRT -> pushUndoAndEdit { AstEditor.insertCubeRoot(it) }
                KeyId.X_SQ -> pushUndoAndEdit { AstEditor.insertPower(it, "3") }
                KeyId.X_POW -> pushUndoAndEdit { AstEditor.insertNthRoot(it) }
                KeyId.LOG_BASE -> pushUndoAndEdit { AstEditor.insertBaseWithPower(it, "10", TokenKind.DIGIT) }
                KeyId.LN -> pushUndoAndEdit { AstEditor.insertBaseWithPower(it, "e", TokenKind.CONSTANT) }
                KeyId.NEGATE -> insertOperatorWithAns("∠")
                KeyId.RECIPROCAL -> insertOperatorWithAns("!")
                KeyId.SIN -> insertTokenAutoAns("sin⁻¹(", TokenKind.FUNCTION)
                KeyId.COS -> insertTokenAutoAns("cos⁻¹(", TokenKind.FUNCTION)
                KeyId.TAN -> insertTokenAutoAns("tan⁻¹(", TokenKind.FUNCTION)
                KeyId.LPAREN -> pushUndoAndEdit { AstEditor.insertAbs(it) }
                KeyId.RPAREN -> pushUndoAndEdit { AstEditor.insertToken(it, ";", TokenKind.SEPARATOR) }
                KeyId.MUL -> insertOperatorWithAns("P")
                KeyId.DIV -> insertOperatorWithAns("C")
                KeyId.PLUS -> insertTokenAutoAns("Pol(", TokenKind.FUNCTION)
                KeyId.MINUS -> insertTokenAutoAns("Rec(", TokenKind.FUNCTION)
                KeyId.NUM_0 -> insertTokenAutoAns("Rnd(", TokenKind.FUNCTION)
                KeyId.DECIMAL -> insertTokenAutoAns("Ran#", TokenKind.CONSTANT)
                KeyId.EXP10 -> insertTokenAutoAns("π", TokenKind.CONSTANT)
                KeyId.ANS -> insertOperatorWithAns("%")
                else -> _uiState.update { it.copy(shiftActive = false) }
            }
            return
        }

        // Primary unshifted keys
        when (key) {
            KeyId.INTEGRAL -> pushUndoAndEdit { AstEditor.insertIntegral(it) }
            KeyId.X_VAR -> insertTokenAutoAns("X", TokenKind.VARIABLE)
            KeyId.FRAC -> pushUndoAndEdit { AstEditor.insertFraction(it) }
            KeyId.SQRT -> pushUndoAndEdit { AstEditor.insertRoot(it) }
            KeyId.X_SQ -> insertPowerWithAns("2")
            KeyId.X_POW -> insertPowerWithAns(null)
            KeyId.LOG_BASE -> pushUndoAndEdit { AstEditor.insertLogBase(it) }
            KeyId.LN -> insertTokenAutoAns("ln(", TokenKind.FUNCTION)
            KeyId.NEGATE -> insertTokenAutoAns("-", TokenKind.OPERATOR)
            KeyId.RECIPROCAL -> insertPowerWithAns("-1")
            KeyId.SIN -> insertTokenAutoAns("sin(", TokenKind.FUNCTION)
            KeyId.COS -> insertTokenAutoAns("cos(", TokenKind.FUNCTION)
            KeyId.TAN -> insertTokenAutoAns("tan(", TokenKind.FUNCTION)
            KeyId.LPAREN -> insertTokenAutoAns("(", TokenKind.PAREN)
            KeyId.RPAREN -> pushUndoAndEdit { AstEditor.insertToken(it, ")", TokenKind.PAREN) }
            KeyId.NUM_0 -> insertDigitOrStartFresh("0", TokenKind.DIGIT)
            KeyId.NUM_1 -> insertDigitOrStartFresh("1", TokenKind.DIGIT)
            KeyId.NUM_2 -> insertDigitOrStartFresh("2", TokenKind.DIGIT)
            KeyId.NUM_3 -> insertDigitOrStartFresh("3", TokenKind.DIGIT)
            KeyId.NUM_4 -> insertDigitOrStartFresh("4", TokenKind.DIGIT)
            KeyId.NUM_5 -> insertDigitOrStartFresh("5", TokenKind.DIGIT)
            KeyId.NUM_6 -> insertDigitOrStartFresh("6", TokenKind.DIGIT)
            KeyId.NUM_7 -> insertDigitOrStartFresh("7", TokenKind.DIGIT)
            KeyId.NUM_8 -> insertDigitOrStartFresh("8", TokenKind.DIGIT)
            KeyId.NUM_9 -> insertDigitOrStartFresh("9", TokenKind.DIGIT)
            KeyId.DECIMAL -> insertDigitOrStartFresh(",", TokenKind.DECIMAL)
            KeyId.PLUS -> insertOperatorWithAns("+")
            KeyId.MINUS -> insertOperatorWithAns("-")
            KeyId.MUL -> insertOperatorWithAns("×")
            KeyId.DIV -> insertOperatorWithAns("÷")
            KeyId.EXP10 -> insertOperatorWithAns("×10^")
            KeyId.ANS -> insertDigitOrStartFresh("Ans", TokenKind.CONSTANT)
            else -> {}
        }
    }

    private fun pushUndoAndEdit(transform: (EditorDocument) -> EditorDocument) {
        _uiState.update { s ->
            val baseDoc = if (s.currentResult != null && s.document.root.isEmpty) AstEditor.emptyDocument() else s.document
            val next = transform(baseDoc)
            s.copy(
                document = next,
                undoStack = (s.undoStack + baseDoc).takeLast(25),
                currentResult = null,
                resultShowingFact = false,
                resultShowingDms = false,
                formatConfig = s.formatConfig.copy(engExponentOffset = null),
                shiftActive = false,
                alphaActive = false,
                statusBanner = null
            )
        }
    }

    private fun insertDigitOrStartFresh(text: String, kind: TokenKind) {
        _uiState.update { s ->
            val baseDoc = if (s.currentResult != null) AstEditor.emptyDocument() else s.document
            val next = AstEditor.insertToken(baseDoc, text, kind)
            s.copy(
                document = next,
                undoStack = (s.undoStack + s.document).takeLast(25),
                currentResult = null,
                resultShowingFact = false,
                resultShowingDms = false,
                formatConfig = s.formatConfig.copy(engExponentOffset = null),
                shiftActive = false,
                alphaActive = false,
                statusBanner = null
            )
        }
    }

    private fun insertTokenAutoAns(text: String, kind: TokenKind) {
        insertDigitOrStartFresh(text, kind)
    }

    private fun insertOperatorWithAns(op: String) {
        _uiState.update { s ->
            val baseDoc = if (s.currentResult != null && s.currentResult !is SymbolicValue.Error) {
                AstEditor.insertToken(AstEditor.emptyDocument(), "Ans", TokenKind.CONSTANT)
            } else s.document
            val next = AstEditor.insertToken(baseDoc, op, TokenKind.OPERATOR)
            s.copy(
                document = next,
                undoStack = (s.undoStack + s.document).takeLast(25),
                currentResult = null,
                resultShowingFact = false,
                resultShowingDms = false,
                formatConfig = s.formatConfig.copy(engExponentOffset = null),
                shiftActive = false,
                alphaActive = false,
                statusBanner = null
            )
        }
    }

    private fun insertPowerWithAns(prefill: String?) {
        _uiState.update { s ->
            val baseDoc = if (s.currentResult != null && s.currentResult !is SymbolicValue.Error) {
                AstEditor.insertToken(AstEditor.emptyDocument(), "Ans", TokenKind.CONSTANT)
            } else s.document
            val next = AstEditor.insertPower(baseDoc, prefill)
            s.copy(
                document = next,
                undoStack = (s.undoStack + s.document).takeLast(25),
                currentResult = null,
                resultShowingFact = false,
                resultShowingDms = false,
                shiftActive = false,
                alphaActive = false,
                statusBanner = null
            )
        }
    }

    private fun evaluateToValue(extraVars: Map<String, Double> = emptyMap()): SymbolicValue? {
        val s = _uiState.value
        if (s.document.root.isEmpty) return s.currentResult
        var polRecUpdated: Pair<Double, Double>? = null
        val ctx = EvaluationContext(
            angleUnit = s.angleUnit,
            complexMode = s.mode == CalcMode.COMPLEX,
            variables = s.variables + extraVars,
            ans = s.ans,
            preAns = s.preAns,
            matrices = s.matrixState.matrices,
            vectors = s.vectorState.vectors,
            onPolRecStore = { x, y -> polRecUpdated = x to y }
        )
        val result = MathEvaluator.evaluate(s.document.root, ctx)
        polRecUpdated?.let { (x, y) ->
            _uiState.update { it.copy(variables = it.variables + ("X" to x) + ("Y" to y)) }
        }
        return result
    }

    private fun evaluateCurrentDocument(forceDecimal: Boolean, extraVars: Map<String, Double> = emptyMap()) {
        val s = _uiState.value
        if (s.document.root.isEmpty) {
            _uiState.update { it.copy(shiftActive = false, alphaActive = false) }
            return
        }
        val result = evaluateToValue(extraVars) ?: return
        _uiState.update { cur ->
            val nextPreAns = if (result !is SymbolicValue.Error) cur.ans else cur.preAns
            val nextAns = if (result !is SymbolicValue.Error) result else cur.ans
            val nextHistory = if (result !is SymbolicValue.Error) {
                (cur.history + HistoryEntry(cur.document.root, result, forceDecimal)).takeLast(40)
            } else cur.history
            val updatedMats = if (result is SymbolicValue.MatrixResult) {
                cur.matrixState.copy(matrices = cur.matrixState.matrices + ("MatAns" to result.data))
            } else cur.matrixState
            val updatedVcts = if (result is SymbolicValue.VectorResult) {
                cur.vectorState.copy(vectors = cur.vectorState.vectors + ("VctAns" to result.components))
            } else cur.vectorState

            cur.copy(
                currentResult = result,
                preDmsResult = null,
                resultForceDecimal = forceDecimal,
                resultShowingFact = false,
                resultShowingDms = result is SymbolicValue.DmsValue,
                formatConfig = cur.formatConfig.copy(engExponentOffset = null),
                ans = nextAns,
                preAns = nextPreAns,
                history = nextHistory,
                historyBrowseIndex = -1,
                matrixState = updatedMats,
                vectorState = updatedVcts,
                shiftActive = false,
                alphaActive = false,
                statusBanner = null
            )
        }
    }

    private fun storeIntoVariable(varName: String) {
        val value = evaluateToValue() ?: _uiState.value.ans
        if (value is SymbolicValue.Error) {
            _uiState.update { it.copy(stoPending = false, currentResult = value) }
            return
        }
        _uiState.update {
            it.copy(
                variables = it.variables + (varName to value.toDouble()),
                currentResult = value,
                ans = value,
                stoPending = false,
                shiftActive = false,
                alphaActive = false,
                statusBanner = "Ans ▸ $varName"
            )
        }
    }

    private fun keyToVariableName(key: KeyId): String? = when (key) {
        KeyId.NEGATE -> "A"
        KeyId.DMS -> "B"
        KeyId.RECIPROCAL -> "C"
        KeyId.SIN -> "D"
        KeyId.COS -> "E"
        KeyId.TAN -> "F"
        KeyId.RPAREN -> "X"
        KeyId.SD -> "Y"
        KeyId.M_PLUS -> "M"
        else -> null
    }

    // --- Overlays: MENU, SETUP, OPTN, CONST, CONV, RECALL, RESET, CALC, SOLVE, QR ---

    private fun openOptnMenu() {
        val s = _uiState.value
        val (title, items) = when (s.mode) {
            CalcMode.COMPLEX -> "Komplexe Zahlen (OPTN)" to listOf(
                OptnItem("cmplx_arg", "1: Argument Arg("),
                OptnItem("cmplx_conj", "2: Konjugiert Conjg("),
                OptnItem("cmplx_re", "3: Realteil ReP("),
                OptnItem("cmplx_im", "4: Imaginärteil ImP("),
                OptnItem("cmplx_polar", "5: ▸ r∠θ (Polarform)"),
                OptnItem("cmplx_rect", "6: ▸ a+bi (Kartesisch)")
            )
            CalcMode.BASE_N -> "Basis-N Logik (OPTN)" to listOf(
                OptnItem("bn_and", "1: AND"),
                OptnItem("bn_or", "2: OR"),
                OptnItem("bn_xor", "3: XOR"),
                OptnItem("bn_xnor", "4: XNOR"),
                OptnItem("bn_not", "5: NOT("),
                OptnItem("bn_neg", "6: Neg(")
            )
            CalcMode.MATRIX -> "Matrizen (OPTN)" to listOf(
                OptnItem("mat_def", "1: Matrix definieren"),
                OptnItem("mat_calc", "2: Matrix-Rechnung"),
                OptnItem("mat_a", "3: MatA"),
                OptnItem("mat_b", "4: MatB"),
                OptnItem("mat_c", "5: MatC"),
                OptnItem("mat_d", "6: MatD"),
                OptnItem("mat_det", "7: Determinante Det("),
                OptnItem("mat_trn", "8: Transponierte Trn("),
                OptnItem("mat_inv", "9: Inverse (⁻¹)")
            )
            CalcMode.VECTOR -> "Vektorrechnung (OPTN)" to listOf(
                OptnItem("vct_def", "1: Vektor definieren"),
                OptnItem("vct_calc", "2: Vektor-Rechnung"),
                OptnItem("vct_a", "3: VctA"),
                OptnItem("vct_b", "4: VctB"),
                OptnItem("vct_dot", "5: Skalarprodukt (•)"),
                OptnItem("vct_ang", "6: Winkel("),
                OptnItem("vct_unit", "7: Einheitsvektor("),
                OptnItem("vct_abs", "8: Betrag |Vct|")
            )
            CalcMode.STATISTICS -> "Statistik (OPTN)" to listOf(
                OptnItem("stat_type", "1: Typ wählen"),
                OptnItem("stat_data", "2: Datentabelle"),
                OptnItem("stat_calc", "3: Statistik-Berechnung")
            )
            CalcMode.SPREADSHEET -> "Tabellenkalk. (OPTN)" to listOf(
                OptnItem("ss_sum", "1: =Summe(A1:A5)"),
                OptnItem("ss_mean", "2: =Mittelwert(A1:A5)"),
                OptnItem("ss_min", "3: =Min(A1:A5)"),
                OptnItem("ss_max", "4: =Max(A1:A5)"),
                OptnItem("ss_clear", "5: Tabelle leeren")
            )
            CalcMode.VERIFY -> "Vergleichsoperatoren (OPTN)" to listOf(
                OptnItem("cmp_eq", "1: =  (Gleich)"),
                OptnItem("cmp_neq", "2: ≠  (Ungleich)"),
                OptnItem("cmp_gt", "3: >  (Größer)"),
                OptnItem("cmp_lt", "4: <  (Kleiner)"),
                OptnItem("cmp_ge", "5: ≥  (Größer gleich)"),
                OptnItem("cmp_le", "6: ≤  (Kleiner gleich)")
            )
            else -> "Optionen (OPTN)" to listOf(
                OptnItem("hyp_sinh", "1: sinh("),
                OptnItem("hyp_cosh", "2: cosh("),
                OptnItem("hyp_tanh", "3: tanh("),
                OptnItem("deg_unit", "4: Gradmaß [D]"),
                OptnItem("rad_unit", "5: Bogenmaß [R]"),
                OptnItem("grad_unit", "6: Neugrad [G]")
            )
        }
        _uiState.update { it.copy(overlay = ActiveOverlay.OptnMenu(title, items, 0), shiftActive = false) }
    }

    private fun openQrOverlay() {
        val s = _uiState.value
        val resText = s.currentResult?.let {
            SymbolicFormatter.toDecimalText(it, s.formatConfig, s.angleUnit)
        } ?: "—"
        _uiState.update {
            it.copy(
                overlay = ActiveOverlay.QrSummary(
                    expressionText = "Modus: ${s.mode.germanTitle} (${s.angleUnit.badge})",
                    resultText = resText
                ),
                shiftActive = false
            )
        }
    }

    fun executeOptnItem(id: String) {
        _uiState.update { it.copy(overlay = ActiveOverlay.None) }
        when (id) {
            "cmplx_arg" -> insertTokenAutoAns("Arg(", TokenKind.FUNCTION)
            "cmplx_conj" -> insertTokenAutoAns("Conjg(", TokenKind.FUNCTION)
            "cmplx_re" -> insertTokenAutoAns("ReP(", TokenKind.FUNCTION)
            "cmplx_im" -> insertTokenAutoAns("ImP(", TokenKind.FUNCTION)
            "cmplx_polar" -> setComplexFormat(ComplexFormat.POLAR)
            "cmplx_rect" -> setComplexFormat(ComplexFormat.RECTANGULAR)
            "bn_and" -> _uiState.update { it.copy(baseN = it.baseN.copy(input = it.baseN.input + " AND ")) }
            "bn_or" -> _uiState.update { it.copy(baseN = it.baseN.copy(input = it.baseN.input + " OR ")) }
            "bn_xor" -> _uiState.update { it.copy(baseN = it.baseN.copy(input = it.baseN.input + " XOR ")) }
            "bn_xnor" -> _uiState.update { it.copy(baseN = it.baseN.copy(input = it.baseN.input + " XNOR ")) }
            "bn_not" -> _uiState.update { it.copy(baseN = it.baseN.copy(input = it.baseN.input + "NOT(")) }
            "bn_neg" -> _uiState.update { it.copy(baseN = it.baseN.copy(input = it.baseN.input + "NEG(")) }
            "mat_def" -> _uiState.update { it.copy(matrixState = it.matrixState.copy(subView = MatrixSubView.DEFINE_SELECT)) }
            "mat_calc" -> _uiState.update { it.copy(matrixState = it.matrixState.copy(subView = MatrixSubView.CALC)) }
            "mat_a" -> insertMatOrVctToken("MatA", isMatrix = true)
            "mat_b" -> insertMatOrVctToken("MatB", isMatrix = true)
            "mat_c" -> insertMatOrVctToken("MatC", isMatrix = true)
            "mat_d" -> insertMatOrVctToken("MatD", isMatrix = true)
            "mat_det" -> { switchToMatCalc(); insertTokenAutoAns("Det(", TokenKind.FUNCTION) }
            "mat_trn" -> { switchToMatCalc(); insertTokenAutoAns("Trn(", TokenKind.FUNCTION) }
            "mat_inv" -> { switchToMatCalc(); insertPowerWithAns("-1") }
            "vct_def" -> _uiState.update { it.copy(vectorState = it.vectorState.copy(subView = VectorSubView.DEFINE_SELECT)) }
            "vct_calc" -> _uiState.update { it.copy(vectorState = it.vectorState.copy(subView = VectorSubView.CALC)) }
            "vct_a" -> insertMatOrVctToken("VctA", isMatrix = false)
            "vct_b" -> insertMatOrVctToken("VctB", isMatrix = false)
            "vct_dot" -> { switchToVctCalc(); insertOperatorWithAns("•") }
            "vct_ang" -> { switchToVctCalc(); insertTokenAutoAns("Winkel(", TokenKind.FUNCTION) }
            "vct_unit" -> { switchToVctCalc(); insertTokenAutoAns("Einheitsv(", TokenKind.FUNCTION) }
            "vct_abs" -> { switchToVctCalc(); pushUndoAndEdit { AstEditor.insertAbs(it) } }
            "stat_type" -> _uiState.update { it.copy(statState = it.statState.copy(subView = StatSubView.SELECT_TYPE)) }
            "stat_data" -> _uiState.update { it.copy(statState = it.statState.copy(subView = StatSubView.TABLE)) }
            "stat_calc" -> _uiState.update { SpecializedModeHandler.computeStatistics(it) }
            "ss_sum" -> applySpreadsheetFormula("=Summe(A1:A5)")
            "ss_mean" -> applySpreadsheetFormula("=Mittelwert(A1:A5)")
            "ss_min" -> applySpreadsheetFormula("=Min(A1:A5)")
            "ss_max" -> applySpreadsheetFormula("=Max(A1:A5)")
            "ss_clear" -> _uiState.update { it.copy(sheetState = SpreadsheetModeState(cells = emptyMap(), evaluated = emptyMap())) }
            "cmp_eq" -> pushUndoAndEdit { AstEditor.insertToken(it, "=", TokenKind.COMPARE) }
            "cmp_neq" -> pushUndoAndEdit { AstEditor.insertToken(it, "≠", TokenKind.COMPARE) }
            "cmp_gt" -> pushUndoAndEdit { AstEditor.insertToken(it, ">", TokenKind.COMPARE) }
            "cmp_lt" -> pushUndoAndEdit { AstEditor.insertToken(it, "<", TokenKind.COMPARE) }
            "cmp_ge" -> pushUndoAndEdit { AstEditor.insertToken(it, "≥", TokenKind.COMPARE) }
            "cmp_le" -> pushUndoAndEdit { AstEditor.insertToken(it, "≤", TokenKind.COMPARE) }
            "hyp_sinh" -> insertTokenAutoAns("sinh(", TokenKind.FUNCTION)
            "hyp_cosh" -> insertTokenAutoAns("cosh(", TokenKind.FUNCTION)
            "hyp_tanh" -> insertTokenAutoAns("tanh(", TokenKind.FUNCTION)
            "deg_unit" -> setAngleUnit(AngleUnit.DEG)
            "rad_unit" -> setAngleUnit(AngleUnit.RAD)
            "grad_unit" -> setAngleUnit(AngleUnit.GRAD)
        }
    }

    private fun switchToMatCalc() {
        _uiState.update { it.copy(matrixState = it.matrixState.copy(subView = MatrixSubView.CALC)) }
    }

    private fun switchToVctCalc() {
        _uiState.update { it.copy(vectorState = it.vectorState.copy(subView = VectorSubView.CALC)) }
    }

    private fun insertMatOrVctToken(token: String, isMatrix: Boolean) {
        if (isMatrix) switchToMatCalc() else switchToVctCalc()
        pushUndoAndEdit { AstEditor.insertToken(it, token, TokenKind.VARIABLE) }
    }

    private fun applySpreadsheetFormula(formula: String) {
        _uiState.update { s ->
            val ss = s.sheetState
            val id = SpreadsheetEngine.cellId(ss.selectedCol, ss.selectedRow)
            val updated = ss.cells + (id to formula)
            s.copy(sheetState = ss.copy(cells = updated, evaluated = SpreadsheetEngine.evaluateAll(updated), isEditing = false, editBuffer = ""))
        }
    }

    fun selectConstant(constant: ScientificConstant) {
        _uiState.update { it.copy(overlay = ActiveOverlay.None) }
        insertTokenAutoAns(constant.symbol, TokenKind.CONSTANT)
    }

    fun selectConversion(conversion: UnitConversion) {
        _uiState.update { it.copy(overlay = ActiveOverlay.None) }
        insertOperatorWithAns(conversion.label)
    }

    fun setThemeMode(theme: CalculatorThemeMode) {
        _uiState.update {
            val updated = it.copy(themeMode = theme, overlay = ActiveOverlay.None)
            persistSettings(updated)
            updated
        }
    }

    fun setAngleUnit(unit: AngleUnit) {
        _uiState.update {
            val updated = it.copy(angleUnit = unit, overlay = ActiveOverlay.None)
            persistSettings(updated)
            updated
        }
    }

    fun setNumberFormat(mode: NumberFormatMode, digits: Int = 4) {
        _uiState.update {
            val updated = it.copy(
                formatConfig = it.formatConfig.copy(numberFormat = mode, digits = digits, engExponentOffset = null),
                overlay = ActiveOverlay.None
            )
            persistSettings(updated)
            updated
        }
    }

    fun setFractionFormat(format: FractionFormat) {
        _uiState.update {
            val updated = it.copy(
                formatConfig = it.formatConfig.copy(fractionFormat = format),
                overlay = ActiveOverlay.None
            )
            persistSettings(updated)
            updated
        }
    }

    fun setComplexFormat(format: ComplexFormat) {
        _uiState.update {
            val updated = it.copy(
                formatConfig = it.formatConfig.copy(complexFormat = format),
                overlay = ActiveOverlay.None
            )
            persistSettings(updated)
            updated
        }
    }

    fun performReset(optionIndex: Int) {
        _uiState.update { s ->
            when (optionIndex) {
                0 -> {
                    // Einstellungen zurücksetzen
                    val reset = s.copy(
                        angleUnit = AngleUnit.DEG,
                        formatConfig = DisplayFormatConfig(),
                        overlay = ActiveOverlay.None,
                        statusBanner = "Einstellungen zurückgesetzt"
                    )
                    persistSettings(reset)
                    reset
                }
                1 -> {
                    // Speicher löschen
                    s.copy(
                        variables = s.variables.mapValues { 0.0 },
                        ans = SymbolicValue.ofLong(0),
                        preAns = SymbolicValue.ofLong(0),
                        history = emptyList(),
                        overlay = ActiveOverlay.None,
                        statusBanner = "Speicher gelöscht"
                    )
                }
                else -> {
                    // Alles initialisieren
                    val fresh = CalculatorUiState(themeMode = s.themeMode, statusBanner = "Alles initialisiert")
                    persistSettings(fresh)
                    fresh
                }
            }
        }
    }

    fun updateSetupSubPage(subPage: SetupSubPage) {
        _uiState.update { it.copy(overlay = ActiveOverlay.SetupMenu(0, subPage)) }
    }

    fun updateConstCategory(catIdx: Int?) {
        _uiState.update { it.copy(overlay = ActiveOverlay.ConstMenu(catIdx, 0)) }
    }

    fun updateConvCategory(catIdx: Int?) {
        _uiState.update { it.copy(overlay = ActiveOverlay.ConvMenu(catIdx, 0)) }
    }

    // --- Direct interactive helpers for touch inside specialized LCD screens ---
    fun selectMatrixSubView(view: MatrixSubView, slot: String? = null) {
        _uiState.update {
            it.copy(
                matrixState = it.matrixState.copy(
                    subView = view,
                    activeSlot = slot ?: it.matrixState.activeSlot
                )
            )
        }
    }

    fun selectMatrixDim(rows: Int, cols: Int) {
        _uiState.update { s ->
            val ms = s.matrixState
            val old = ms.matrices[ms.activeSlot]
            val mat = MatrixEngine.empty(rows, cols)
            if (old != null) {
                for (r in 0 until minOf(rows, old.size)) {
                    for (c in 0 until minOf(cols, old[0].size)) mat[r][c] = old[r][c]
                }
            }
            s.copy(
                matrixState = ms.copy(
                    editRows = rows, editCols = cols,
                    matrices = ms.matrices + (ms.activeSlot to mat),
                    selectedRow = 0, selectedCol = 0, cellInput = "",
                    subView = MatrixSubView.DEFINE_EDIT
                )
            )
        }
    }

    fun selectVectorSubView(view: VectorSubView, slot: String? = null) {
        _uiState.update {
            it.copy(
                vectorState = it.vectorState.copy(
                    subView = view,
                    activeSlot = slot ?: it.vectorState.activeSlot
                )
            )
        }
    }

    fun selectVectorDim(dim: Int) {
        _uiState.update { s ->
            val vs = s.vectorState
            val old = vs.vectors[vs.activeSlot]
            val vec = DoubleArray(dim) { i -> old?.getOrElse(i) { 0.0 } ?: 0.0 }
            s.copy(
                vectorState = vs.copy(
                    editDim = dim,
                    vectors = vs.vectors + (vs.activeSlot to vec),
                    selectedComp = 0, cellInput = "",
                    subView = VectorSubView.DEFINE_EDIT
                )
            )
        }
    }

    fun selectStatType(type: StatType) {
        _uiState.update { it.copy(statState = it.statState.copy(statType = type, subView = StatSubView.TABLE, selectedRow = 0, selectedCol = 0)) }
    }

    fun triggerStatCompute() {
        _uiState.update { SpecializedModeHandler.computeStatistics(it) }
    }

    fun selectDistType(type: DistributionType) {
        val supportsList = type !in setOf(DistributionType.NORMAL_CD, DistributionType.INVERSE_NORMAL)
        _uiState.update {
            it.copy(
                distState = it.distState.copy(
                    distType = type,
                    subView = if (supportsList) DistSubView.MODE_SELECT else DistSubView.PARAMS,
                    isListMode = false, activeFieldIndex = 0, editBuffer = "", error = null
                )
            )
        }
    }

    fun selectDistInputMode(listMode: Boolean) {
        _uiState.update {
            it.copy(
                distState = it.distState.copy(
                    isListMode = listMode,
                    subView = if (listMode) DistSubView.LIST_INPUT else DistSubView.PARAMS,
                    activeFieldIndex = 0, selectedListRow = 0, editBuffer = ""
                )
            )
        }
    }

    fun triggerDistCompute() {
        _uiState.update { SpecializedModeHandler.evaluateDistribution(it) }
    }

    fun selectEquationKind(isPoly: Boolean) {
        _uiState.update { it.copy(eqState = it.eqState.copy(isPolynomial = isPoly, subView = EquationSubView.SELECT_SIZE)) }
    }

    fun selectEquationSize(size: Int) {
        val key = when (size) { 2 -> KeyId.NUM_2; 3 -> KeyId.NUM_3; else -> KeyId.NUM_4 }
        _uiState.update { SpecializedModeHandler.handleKey(it, key, false, false) }
    }

    fun triggerEquationSolve() {
        _uiState.update { s ->
            if (s.eqState.isPolynomial) SpecializedModeHandler.solvePoly(s) else SpecializedModeHandler.solveLinear(s)
        }
    }

    fun selectInequalityDegree(deg: Int) {
        val key = when (deg) { 2 -> KeyId.NUM_2; 3 -> KeyId.NUM_3; else -> KeyId.NUM_4 }
        _uiState.update { SpecializedModeHandler.handleKey(it, key, false, false) }
    }

    fun selectInequalityOp(op: EquationInequalityEngine.InequalityOp) {
        _uiState.update { it.copy(ineqState = it.ineqState.copy(op = op, subView = InequalitySubView.INPUT_COEFFS, selectedCoeff = 0)) }
    }

    fun triggerInequalitySolve() {
        _uiState.update { SpecializedModeHandler.solveInequality(it) }
    }

    fun selectRatioForm(solveFirstRight: Boolean) {
        _uiState.update { it.copy(ratioState = it.ratioState.copy(solveFirstRight = solveFirstRight, subView = RatioSubView.INPUT, activeField = 0)) }
    }

    fun triggerRatioSolve() {
        _uiState.update { SpecializedModeHandler.solveRatio(it) }
    }

    fun selectSpreadsheetCell(col: Int, row: Int) {
        _uiState.update { it.copy(sheetState = it.sheetState.copy(selectedCol = col, selectedRow = row, isEditing = false, editBuffer = "")) }
    }

    private fun handleOverlayKey(key: KeyId) {
        val s = _uiState.value
        if (key == KeyId.AC) {
            dismissOverlay()
            return
        }
        when (val ov = s.overlay) {
            is ActiveOverlay.MenuGrid -> {
                val modes = CalcMode.entries
                // Direct shortcut keys 1–9, A–D
                val direct = when (key) {
                    KeyId.NUM_1 -> CalcMode.CALCULATE
                    KeyId.NUM_2 -> CalcMode.COMPLEX
                    KeyId.NUM_3 -> CalcMode.BASE_N
                    KeyId.NUM_4 -> CalcMode.MATRIX
                    KeyId.NUM_5 -> CalcMode.VECTOR
                    KeyId.NUM_6 -> CalcMode.STATISTICS
                    KeyId.NUM_7 -> CalcMode.DISTRIBUTION
                    KeyId.NUM_8 -> CalcMode.SPREADSHEET
                    KeyId.NUM_9 -> CalcMode.TABLE
                    KeyId.NEGATE -> CalcMode.EQUATION      // A
                    KeyId.DMS -> CalcMode.INEQUALITY       // B
                    KeyId.RECIPROCAL -> CalcMode.VERIFY    // C
                    KeyId.SIN -> CalcMode.RATIO            // D
                    else -> null
                }
                if (direct != null) {
                    selectMode(direct)
                    return
                }
                val idx = ov.selectedIndex
                val nextIdx = when (key) {
                    KeyId.LEFT -> (idx - 1 + modes.size) % modes.size
                    KeyId.RIGHT -> (idx + 1) % modes.size
                    KeyId.UP -> (idx - 4 + modes.size) % modes.size
                    KeyId.DOWN -> (idx + 4) % modes.size
                    KeyId.EQUALS -> { selectMode(modes[idx]); return }
                    else -> idx
                }
                _uiState.update { it.copy(overlay = ov.copy(selectedIndex = nextIdx)) }
            }
            is ActiveOverlay.SetupMenu -> {
                if (key == KeyId.LEFT && ov.subPage != SetupSubPage.ROOT) {
                    updateSetupSubPage(SetupSubPage.ROOT)
                    return
                }
                when (ov.subPage) {
                    SetupSubPage.ROOT -> when (key) {
                        KeyId.NUM_1 -> updateSetupSubPage(SetupSubPage.THEME)
                        KeyId.NUM_2 -> updateSetupSubPage(SetupSubPage.ANGLE_UNIT)
                        KeyId.NUM_3 -> updateSetupSubPage(SetupSubPage.NUMBER_FORMAT)
                        KeyId.NUM_4 -> updateSetupSubPage(SetupSubPage.FRACTION_FORMAT)
                        KeyId.NUM_5 -> updateSetupSubPage(SetupSubPage.COMPLEX_FORMAT)
                        else -> {}
                    }
                    SetupSubPage.THEME -> when (key) {
                        KeyId.NUM_1 -> setThemeMode(CalculatorThemeMode.MATTE_DARK)
                        KeyId.NUM_2 -> setThemeMode(CalculatorThemeMode.CLASSIC_LCD)
                        else -> {}
                    }
                    SetupSubPage.ANGLE_UNIT -> when (key) {
                        KeyId.NUM_1 -> setAngleUnit(AngleUnit.DEG)
                        KeyId.NUM_2 -> setAngleUnit(AngleUnit.RAD)
                        KeyId.NUM_3 -> setAngleUnit(AngleUnit.GRAD)
                        else -> {}
                    }
                    SetupSubPage.NUMBER_FORMAT -> when (key) {
                        KeyId.NUM_1 -> setNumberFormat(NumberFormatMode.FIX, 4)
                        KeyId.NUM_2 -> setNumberFormat(NumberFormatMode.SCI, 4)
                        KeyId.NUM_3 -> setNumberFormat(NumberFormatMode.NORM1)
                        KeyId.NUM_4 -> setNumberFormat(NumberFormatMode.NORM2)
                        else -> {}
                    }
                    SetupSubPage.FRACTION_FORMAT -> when (key) {
                        KeyId.NUM_1 -> setFractionFormat(FractionFormat.IMPROPER)
                        KeyId.NUM_2 -> setFractionFormat(FractionFormat.MIXED)
                        else -> {}
                    }
                    SetupSubPage.COMPLEX_FORMAT -> when (key) {
                        KeyId.NUM_1 -> setComplexFormat(ComplexFormat.RECTANGULAR)
                        KeyId.NUM_2 -> setComplexFormat(ComplexFormat.POLAR)
                        else -> {}
                    }
                }
            }
            is ActiveOverlay.OptnMenu -> {
                val num = keyToMenuNumber(key)
                if (num != null && num in 1..ov.items.size) {
                    executeOptnItem(ov.items[num - 1].id)
                    return
                }
                when (key) {
                    KeyId.UP -> _uiState.update { it.copy(overlay = ov.copy(selectedIndex = (ov.selectedIndex - 1 + ov.items.size) % ov.items.size)) }
                    KeyId.DOWN -> _uiState.update { it.copy(overlay = ov.copy(selectedIndex = (ov.selectedIndex + 1) % ov.items.size)) }
                    KeyId.EQUALS -> executeOptnItem(ov.items[ov.selectedIndex].id)
                    else -> {}
                }
            }
            is ActiveOverlay.ConstMenu -> {
                val num = keyToMenuNumber(key)
                if (ov.categoryIndex == null) {
                    if (num != null && num in 1..ScientificCatalog.constantCategories.size) {
                        updateConstCategory(num - 1)
                    }
                } else {
                    val catName = ScientificCatalog.constantCategories[ov.categoryIndex]
                    val list = ScientificCatalog.constants.filter { it.category == catName }
                    if (num != null && num in 1..list.size) selectConstant(list[num - 1])
                    else if (key == KeyId.LEFT) updateConstCategory(null)
                }
            }
            is ActiveOverlay.ConvMenu -> {
                val num = keyToMenuNumber(key)
                if (ov.categoryIndex == null) {
                    if (num != null && num in 1..ScientificCatalog.conversionCategories.size) {
                        updateConvCategory(num - 1)
                    }
                } else {
                    val catName = ScientificCatalog.conversionCategories[ov.categoryIndex]
                    val list = ScientificCatalog.conversions.filter { it.category == catName }
                    if (num != null && num in 1..list.size) selectConversion(list[num - 1])
                    else if (key == KeyId.LEFT) updateConvCategory(null)
                }
            }
            is ActiveOverlay.ResetConfirm -> {
                when (key) {
                    KeyId.NUM_1 -> performReset(0)
                    KeyId.NUM_2 -> performReset(1)
                    KeyId.NUM_3 -> performReset(2)
                    else -> {}
                }
            }
            is ActiveOverlay.RecallMemory, is ActiveOverlay.QrSummary -> {
                dismissOverlay()
            }
            is ActiveOverlay.CalcPrompt -> {
                when (key) {
                    KeyId.DEL -> _uiState.update { it.copy(overlay = ov.copy(inputBuffer = ov.inputBuffer.dropLast(1))) }
                    KeyId.EQUALS -> {
                        val vName = ov.variables[ov.currentIndex]
                        val parsed = ov.inputBuffer.replace(',', '.').toDoubleOrNull() ?: (s.variables[vName] ?: 0.0)
                        val updatedMap = ov.collectedValues + (vName to parsed)
                        _uiState.update { it.copy(variables = it.variables + (vName to parsed)) }
                        if (ov.currentIndex + 1 < ov.variables.size) {
                            val nextVar = ov.variables[ov.currentIndex + 1]
                            val nextDefault = SymbolicFormatter.formatGermanNumber(s.variables[nextVar] ?: 0.0)
                            _uiState.update {
                                it.copy(
                                    overlay = ov.copy(
                                        currentIndex = ov.currentIndex + 1,
                                        inputBuffer = nextDefault,
                                        collectedValues = updatedMap
                                    )
                                )
                            }
                        } else {
                            _uiState.update { it.copy(overlay = ActiveOverlay.None) }
                            evaluateCurrentDocument(forceDecimal = false, extraVars = updatedMap)
                        }
                    }
                    else -> {
                        val ch = SpecializedModeHandler.keyToNumericChar(key) ?: return
                        _uiState.update { it.copy(overlay = ov.copy(inputBuffer = ov.inputBuffer + ch)) }
                    }
                }
            }
            is ActiveOverlay.SolvePrompt -> {
                when (key) {
                    KeyId.DEL -> _uiState.update { it.copy(overlay = ov.copy(inputBuffer = ov.inputBuffer.dropLast(1), resultText = null)) }
                    KeyId.EQUALS -> {
                        if (ov.resultText != null) {
                            dismissOverlay()
                            return
                        }
                        val guess = ov.inputBuffer.replace(',', '.').toDoubleOrNull() ?: 1.0
                        val ctx = EvaluationContext(angleUnit = s.angleUnit, variables = s.variables, ans = s.ans)
                        val solved = MathEvaluator.solveEquation(s.document.root, guess, ctx)
                        solved.fold(
                            onSuccess = { (xSol, residual) ->
                                val xFmt = SymbolicFormatter.formatGermanNumber(xSol, s.formatConfig)
                                val rFmt = SymbolicFormatter.formatGermanNumber(residual, s.formatConfig)
                                _uiState.update {
                                    it.copy(
                                        variables = it.variables + ("X" to xSol),
                                        ans = SymbolicValue.ofDouble(xSol),
                                        overlay = ov.copy(resultText = "X = $xFmt\nL−R = $rFmt")
                                    )
                                }
                            },
                            onFailure = { e ->
                                _uiState.update { it.copy(overlay = ov.copy(resultText = e.message ?: "Keine Lösung")) }
                            }
                        )
                    }
                    else -> {
                        val ch = SpecializedModeHandler.keyToNumericChar(key) ?: return
                        _uiState.update { it.copy(overlay = ov.copy(inputBuffer = ov.inputBuffer + ch, resultText = null)) }
                    }
                }
            }
            ActiveOverlay.None -> {}
        }
    }

    private fun keyToMenuNumber(key: KeyId): Int? = when (key) {
        KeyId.NUM_1 -> 1; KeyId.NUM_2 -> 2; KeyId.NUM_3 -> 3
        KeyId.NUM_4 -> 4; KeyId.NUM_5 -> 5; KeyId.NUM_6 -> 6
        KeyId.NUM_7 -> 7; KeyId.NUM_8 -> 8; KeyId.NUM_9 -> 9
        else -> null
    }
}
