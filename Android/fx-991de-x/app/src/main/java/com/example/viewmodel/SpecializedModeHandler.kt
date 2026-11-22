package com.example.viewmodel

import com.naveen.fx991dex.engine.*
import kotlin.math.abs

/**
 * Modular pure-function handler for specialized ClassWiz modes:
 * Mode 3 (Basis-N), Mode 4 (Matrizen), Mode 5 (Vektorrechnung), Mode 6 (Statistik),
 * Mode 7 (Verteilungsfkt.), Mode 8 (Tabellenkalk.), Mode 9 (Tabellen),
 * Mode 10 (Gleichung/Funkt.), Mode 11 (Ungleichungen), Mode 13 (Verhältnisse).
 */
object SpecializedModeHandler {

    fun isSpecializedInputMode(state: CalculatorUiState): Boolean {
        return when (state.mode) {
            CalcMode.CALCULATE, CalcMode.COMPLEX, CalcMode.VERIFY -> false
            CalcMode.MATRIX -> state.matrixState.subView != MatrixSubView.CALC
            CalcMode.VECTOR -> state.vectorState.subView != VectorSubView.CALC
            CalcMode.TABLE -> true
            else -> true
        }
    }

    fun handleKey(state: CalculatorUiState, key: KeyId, shift: Boolean, alpha: Boolean): CalculatorUiState {
        return when (state.mode) {
            CalcMode.BASE_N -> handleBaseN(state, key, shift, alpha)
            CalcMode.MATRIX -> handleMatrix(state, key)
            CalcMode.VECTOR -> handleVector(state, key)
            CalcMode.STATISTICS -> handleStatistics(state, key)
            CalcMode.DISTRIBUTION -> handleDistribution(state, key)
            CalcMode.SPREADSHEET -> handleSpreadsheet(state, key, shift, alpha)
            CalcMode.TABLE -> handleTable(state, key, shift, alpha)
            CalcMode.EQUATION -> handleEquation(state, key)
            CalcMode.INEQUALITY -> handleInequality(state, key)
            CalcMode.RATIO -> handleRatio(state, key, shift)
            else -> state
        }
    }

    // --- Mode 3: Basis-N ---
    private fun handleBaseN(state: CalculatorUiState, key: KeyId, shift: Boolean, alpha: Boolean): CalculatorUiState {
        val bn = state.baseN
        // Base switch keys: x² (DEC), x^□ (HEX), log_□ (BIN), ln (OCT)
        when (key) {
            KeyId.X_SQ -> return state.copy(baseN = bn.copy(currentBase = NumberBase.DEC, input = "", error = null))
            KeyId.X_POW -> return state.copy(baseN = bn.copy(currentBase = NumberBase.HEX, input = "", error = null))
            KeyId.LOG_BASE -> return state.copy(baseN = bn.copy(currentBase = NumberBase.BIN, input = "", error = null))
            KeyId.LN -> return state.copy(baseN = bn.copy(currentBase = NumberBase.OCT, input = "", error = null))
            KeyId.AC -> return state.copy(baseN = bn.copy(input = "", resultValue = 0, error = null))
            KeyId.DEL -> return state.copy(baseN = bn.copy(input = bn.input.dropLast(1), error = null))
            KeyId.EQUALS -> {
                val res = BaseNEngine.evaluate(bn.input.ifBlank { BaseNEngine.formatValue(bn.resultValue, bn.currentBase) }, bn.currentBase)
                return res.fold(
                    onSuccess = { v -> state.copy(baseN = bn.copy(resultValue = v, error = null)) },
                    onFailure = { e -> state.copy(baseN = bn.copy(error = e.message ?: "Syntax FEHLER")) }
                )
            }
            else -> {}
        }
        val token = when (key) {
            KeyId.NUM_0 -> "0"; KeyId.NUM_1 -> "1"; KeyId.NUM_2 -> "2"; KeyId.NUM_3 -> "3"
            KeyId.NUM_4 -> "4"; KeyId.NUM_5 -> "5"; KeyId.NUM_6 -> "6"; KeyId.NUM_7 -> "7"
            KeyId.NUM_8 -> "8"; KeyId.NUM_9 -> "9"
            KeyId.NEGATE -> if (bn.currentBase == NumberBase.HEX) "A" else "-"
            KeyId.DMS -> if (bn.currentBase == NumberBase.HEX) "B" else ""
            KeyId.RECIPROCAL -> if (bn.currentBase == NumberBase.HEX) "C" else ""
            KeyId.SIN -> if (bn.currentBase == NumberBase.HEX) "D" else ""
            KeyId.COS -> if (bn.currentBase == NumberBase.HEX) "E" else ""
            KeyId.TAN -> if (bn.currentBase == NumberBase.HEX) "F" else ""
            KeyId.PLUS -> "+"; KeyId.MINUS -> "-"; KeyId.MUL -> "×"; KeyId.DIV -> "÷"
            KeyId.LPAREN -> "("; KeyId.RPAREN -> ")"
            else -> ""
        }
        if (token.isEmpty()) return state
        return state.copy(baseN = bn.copy(input = bn.input + token, error = null))
    }

    // --- Mode 4: Matrizen ---
    private fun handleMatrix(state: CalculatorUiState, key: KeyId): CalculatorUiState {
        val ms = state.matrixState
        return when (ms.subView) {
            MatrixSubView.DEFINE_SELECT -> {
                val slot = when (key) {
                    KeyId.NUM_1 -> "MatA"; KeyId.NUM_2 -> "MatB"
                    KeyId.NUM_3 -> "MatC"; KeyId.NUM_4 -> "MatD"
                    KeyId.AC -> return state.copy(matrixState = ms.copy(subView = MatrixSubView.CALC))
                    else -> null
                } ?: return state
                val existing = ms.matrices[slot]
                state.copy(
                    matrixState = ms.copy(
                        activeSlot = slot,
                        editRows = existing?.size ?: 2,
                        editCols = existing?.firstOrNull()?.size ?: 2,
                        subView = MatrixSubView.DEFINE_DIM
                    )
                )
            }
            MatrixSubView.DEFINE_DIM -> {
                val num = keyToSmallInt(key)
                if (key == KeyId.AC) return state.copy(matrixState = ms.copy(subView = MatrixSubView.CALC))
                if (num != null && num in 1..4) {
                    val mat = MatrixEngine.empty(num, ms.editCols)
                    val old = ms.matrices[ms.activeSlot]
                    if (old != null) {
                        for (r in 0 until minOf(num, old.size)) {
                            for (c in 0 until minOf(ms.editCols, old[0].size)) mat[r][c] = old[r][c]
                        }
                    }
                    state.copy(
                        matrixState = ms.copy(
                            editRows = num,
                            matrices = ms.matrices + (ms.activeSlot to mat),
                            selectedRow = 0, selectedCol = 0, cellInput = "",
                            subView = MatrixSubView.DEFINE_EDIT
                        )
                    )
                } else if (key == KeyId.EQUALS) {
                    val mat = ms.matrices[ms.activeSlot] ?: MatrixEngine.empty(ms.editRows, ms.editCols)
                    state.copy(
                        matrixState = ms.copy(
                            matrices = ms.matrices + (ms.activeSlot to mat),
                            selectedRow = 0, selectedCol = 0, cellInput = "",
                            subView = MatrixSubView.DEFINE_EDIT
                        )
                    )
                } else state
            }
            MatrixSubView.DEFINE_EDIT -> {
                val mat = ms.matrices[ms.activeSlot] ?: MatrixEngine.empty(ms.editRows, ms.editCols)
                val rows = mat.size; val cols = mat[0].size
                when (key) {
                    KeyId.AC -> state.copy(matrixState = ms.copy(subView = MatrixSubView.CALC, cellInput = ""))
                    KeyId.DEL -> state.copy(matrixState = ms.copy(cellInput = ms.cellInput.dropLast(1)))
                    KeyId.UP -> commitMatrixCell(state).let { s ->
                        s.copy(matrixState = s.matrixState.copy(selectedRow = (ms.selectedRow - 1 + rows) % rows))
                    }
                    KeyId.DOWN -> commitMatrixCell(state).let { s ->
                        s.copy(matrixState = s.matrixState.copy(selectedRow = (ms.selectedRow + 1) % rows))
                    }
                    KeyId.LEFT -> commitMatrixCell(state).let { s ->
                        s.copy(matrixState = s.matrixState.copy(selectedCol = (ms.selectedCol - 1 + cols) % cols))
                    }
                    KeyId.RIGHT -> commitMatrixCell(state).let { s ->
                        s.copy(matrixState = s.matrixState.copy(selectedCol = (ms.selectedCol + 1) % cols))
                    }
                    KeyId.EQUALS -> {
                        val committed = commitMatrixCell(state)
                        val nextCol = (ms.selectedCol + 1) % cols
                        val nextRow = if (nextCol == 0) (ms.selectedRow + 1) % rows else ms.selectedRow
                        committed.copy(
                            matrixState = committed.matrixState.copy(selectedRow = nextRow, selectedCol = nextCol)
                        )
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(matrixState = ms.copy(cellInput = ms.cellInput + ch))
                    }
                }
            }
            MatrixSubView.CALC -> state
        }
    }

    private fun commitMatrixCell(state: CalculatorUiState): CalculatorUiState {
        val ms = state.matrixState
        if (ms.cellInput.isBlank()) return state
        val value = ms.cellInput.replace(',', '.').toDoubleOrNull() ?: return state.copy(
            matrixState = ms.copy(cellInput = "")
        )
        val old = ms.matrices[ms.activeSlot] ?: MatrixEngine.empty(ms.editRows, ms.editCols)
        val copy = Array(old.size) { r -> old[r].copyOf() }
        if (ms.selectedRow in copy.indices && ms.selectedCol in copy[0].indices) {
            copy[ms.selectedRow][ms.selectedCol] = value
        }
        return state.copy(matrixState = ms.copy(matrices = ms.matrices + (ms.activeSlot to copy), cellInput = ""))
    }

    // --- Mode 5: Vektorrechnung ---
    private fun handleVector(state: CalculatorUiState, key: KeyId): CalculatorUiState {
        val vs = state.vectorState
        return when (vs.subView) {
            VectorSubView.DEFINE_SELECT -> {
                val slot = when (key) {
                    KeyId.NUM_1 -> "VctA"; KeyId.NUM_2 -> "VctB"
                    KeyId.NUM_3 -> "VctC"; KeyId.NUM_4 -> "VctD"
                    KeyId.AC -> return state.copy(vectorState = vs.copy(subView = VectorSubView.CALC))
                    else -> null
                } ?: return state
                state.copy(vectorState = vs.copy(activeSlot = slot, subView = VectorSubView.DEFINE_DIM))
            }
            VectorSubView.DEFINE_DIM -> {
                if (key == KeyId.AC) return state.copy(vectorState = vs.copy(subView = VectorSubView.CALC))
                val dim = when (key) { KeyId.NUM_2 -> 2; KeyId.NUM_3 -> 3; else -> null } ?: return state
                val old = vs.vectors[vs.activeSlot]
                val vec = DoubleArray(dim) { i -> old?.getOrElse(i) { 0.0 } ?: 0.0 }
                state.copy(
                    vectorState = vs.copy(
                        editDim = dim,
                        vectors = vs.vectors + (vs.activeSlot to vec),
                        selectedComp = 0, cellInput = "",
                        subView = VectorSubView.DEFINE_EDIT
                    )
                )
            }
            VectorSubView.DEFINE_EDIT -> {
                val vec = vs.vectors[vs.activeSlot] ?: DoubleArray(vs.editDim)
                when (key) {
                    KeyId.AC -> state.copy(vectorState = vs.copy(subView = VectorSubView.CALC, cellInput = ""))
                    KeyId.DEL -> state.copy(vectorState = vs.copy(cellInput = vs.cellInput.dropLast(1)))
                    KeyId.UP, KeyId.LEFT -> commitVectorCell(state).let { s ->
                        s.copy(vectorState = s.vectorState.copy(selectedComp = (vs.selectedComp - 1 + vec.size) % vec.size))
                    }
                    KeyId.DOWN, KeyId.RIGHT, KeyId.EQUALS -> commitVectorCell(state).let { s ->
                        s.copy(vectorState = s.vectorState.copy(selectedComp = (vs.selectedComp + 1) % vec.size))
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(vectorState = vs.copy(cellInput = vs.cellInput + ch))
                    }
                }
            }
            VectorSubView.CALC -> state
        }
    }

    private fun commitVectorCell(state: CalculatorUiState): CalculatorUiState {
        val vs = state.vectorState
        if (vs.cellInput.isBlank()) return state
        val value = vs.cellInput.replace(',', '.').toDoubleOrNull() ?: return state.copy(
            vectorState = vs.copy(cellInput = "")
        )
        val old = vs.vectors[vs.activeSlot] ?: DoubleArray(vs.editDim)
        val copy = old.copyOf()
        if (vs.selectedComp in copy.indices) copy[vs.selectedComp] = value
        return state.copy(vectorState = vs.copy(vectors = vs.vectors + (vs.activeSlot to copy), cellInput = ""))
    }

    // --- Mode 6: Statistik ---
    private fun handleStatistics(state: CalculatorUiState, key: KeyId): CalculatorUiState {
        val st = state.statState
        return when (st.subView) {
            StatSubView.SELECT_TYPE -> {
                val type = when (key) {
                    KeyId.NUM_1 -> StatType.ONE_VAR
                    KeyId.NUM_2 -> StatType.LINEAR
                    KeyId.NUM_3 -> StatType.QUADRATIC
                    KeyId.NUM_4 -> StatType.LOGARITHMIC
                    KeyId.NUM_5 -> StatType.EXPONENTIAL
                    else -> null
                } ?: return state
                state.copy(statState = st.copy(statType = type, subView = StatSubView.TABLE, selectedRow = 0, selectedCol = 0, cellInput = ""))
            }
            StatSubView.TABLE -> {
                when (key) {
                    KeyId.AC -> computeStatistics(commitStatCell(state))
                    KeyId.DEL -> {
                        if (st.cellInput.isNotEmpty()) {
                            state.copy(statState = st.copy(cellInput = st.cellInput.dropLast(1)))
                        } else if (st.rows.size > 1 && st.selectedRow in st.rows.indices) {
                            val list = st.rows.toMutableList()
                            list.removeAt(st.selectedRow)
                            state.copy(statState = st.copy(rows = list, selectedRow = st.selectedRow.coerceAtMost(list.lastIndex)))
                        } else state
                    }
                    KeyId.UP -> commitStatCell(state).let { s ->
                        s.copy(statState = s.statState.copy(selectedRow = (st.selectedRow - 1).coerceAtLeast(0)))
                    }
                    KeyId.DOWN -> commitStatCell(state).let { s ->
                        val next = (st.selectedRow + 1).coerceAtMost(s.statState.rows.size)
                        s.copy(statState = s.statState.copy(selectedRow = next))
                    }
                    KeyId.LEFT -> if (st.statType.isTwoVar) commitStatCell(state).let { s ->
                        s.copy(statState = s.statState.copy(selectedCol = 0))
                    } else state
                    KeyId.RIGHT -> if (st.statType.isTwoVar) commitStatCell(state).let { s ->
                        s.copy(statState = s.statState.copy(selectedCol = 1))
                    } else state
                    KeyId.EQUALS -> {
                        val committed = commitStatCell(state)
                        val rows = committed.statState.rows
                        val nextRow = if (!st.statType.isTwoVar || st.selectedCol == 1) {
                            (st.selectedRow + 1).coerceAtMost(rows.size)
                        } else st.selectedRow
                        val nextCol = if (st.statType.isTwoVar) 1 - st.selectedCol else 0
                        committed.copy(statState = committed.statState.copy(selectedRow = nextRow, selectedCol = nextCol))
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(statState = st.copy(cellInput = st.cellInput + ch))
                    }
                }
            }
            StatSubView.RESULTS -> {
                if (key == KeyId.AC || key == KeyId.LEFT) {
                    state.copy(statState = st.copy(subView = StatSubView.TABLE))
                } else state
            }
        }
    }

    private fun commitStatCell(state: CalculatorUiState): CalculatorUiState {
        val st = state.statState
        if (st.cellInput.isBlank()) return state
        val value = st.cellInput.replace(',', '.').toDoubleOrNull() ?: return state.copy(statState = st.copy(cellInput = ""))
        val list = st.rows.toMutableList()
        while (list.size <= st.selectedRow) list.add(StatRow(0.0, 0.0, 1.0))
        val cur = list[st.selectedRow]
        list[st.selectedRow] = if (st.selectedCol == 0) cur.copy(x = value) else cur.copy(y = value)
        return state.copy(statState = st.copy(rows = list, cellInput = ""))
    }

    fun computeStatistics(state: CalculatorUiState): CalculatorUiState {
        val committed = commitStatCell(state)
        val st = committed.statState
        val oneVar = StatisticsEngine.computeOneVar(st.rows)
        val reg = if (st.statType.isTwoVar) StatisticsEngine.computeRegression(st.rows, st.statType) else null
        return committed.copy(statState = st.copy(subView = StatSubView.RESULTS, oneVarStats = oneVar, regression = reg))
    }

    // --- Mode 7: Verteilungsfunktionen ---
    fun distributionFieldKeys(type: DistributionType, isListMode: Boolean): List<String> {
        val xList = if (isListMode) emptyList() else listOf("x")
        return when (type) {
            DistributionType.NORMAL_PD -> xList + listOf("σ", "μ")
            DistributionType.NORMAL_CD -> listOf("Lower", "Upper", "σ", "μ")
            DistributionType.INVERSE_NORMAL -> listOf("Fläche", "σ", "μ")
            DistributionType.BINOMIAL_PD, DistributionType.BINOMIAL_CD -> xList + listOf("N", "p")
            DistributionType.POISSON_PD, DistributionType.POISSON_CD -> xList + listOf("λ")
        }
    }

    private fun handleDistribution(state: CalculatorUiState, key: KeyId): CalculatorUiState {
        val ds = state.distState
        return when (ds.subView) {
            DistSubView.SELECT_TYPE -> {
                val type = when (key) {
                    KeyId.NUM_1 -> DistributionType.NORMAL_PD
                    KeyId.NUM_2 -> DistributionType.NORMAL_CD
                    KeyId.NUM_3 -> DistributionType.INVERSE_NORMAL
                    KeyId.NUM_4 -> DistributionType.BINOMIAL_PD
                    KeyId.NUM_5 -> DistributionType.BINOMIAL_CD
                    KeyId.NUM_6 -> DistributionType.POISSON_PD
                    KeyId.NUM_7 -> DistributionType.POISSON_CD
                    else -> null
                } ?: return state
                val supportsList = type !in setOf(DistributionType.NORMAL_CD, DistributionType.INVERSE_NORMAL)
                state.copy(
                    distState = ds.copy(
                        distType = type,
                        subView = if (supportsList) DistSubView.MODE_SELECT else DistSubView.PARAMS,
                        isListMode = false, activeFieldIndex = 0, editBuffer = "", error = null
                    )
                )
            }
            DistSubView.MODE_SELECT -> {
                when (key) {
                    KeyId.NUM_1 -> state.copy(distState = ds.copy(isListMode = true, subView = DistSubView.LIST_INPUT, selectedListRow = 0, editBuffer = ""))
                    KeyId.NUM_2 -> state.copy(distState = ds.copy(isListMode = false, subView = DistSubView.PARAMS, activeFieldIndex = 0, editBuffer = ""))
                    KeyId.AC -> state.copy(distState = ds.copy(subView = DistSubView.SELECT_TYPE))
                    else -> state
                }
            }
            DistSubView.LIST_INPUT -> {
                when (key) {
                    KeyId.AC -> state.copy(distState = ds.copy(subView = DistSubView.SELECT_TYPE))
                    KeyId.DEL -> state.copy(distState = ds.copy(editBuffer = ds.editBuffer.dropLast(1)))
                    KeyId.UP -> commitDistListRow(state).let { s ->
                        s.copy(distState = s.distState.copy(selectedListRow = (ds.selectedListRow - 1).coerceAtLeast(0)))
                    }
                    KeyId.DOWN -> commitDistListRow(state).let { s ->
                        s.copy(distState = s.distState.copy(selectedListRow = (ds.selectedListRow + 1).coerceAtMost(s.distState.listValues.size)))
                    }
                    KeyId.EQUALS -> {
                        if (ds.editBuffer.isNotBlank()) {
                            val committed = commitDistListRow(state)
                            committed.copy(distState = committed.distState.copy(selectedListRow = (ds.selectedListRow + 1).coerceAtMost(committed.distState.listValues.size)))
                        } else {
                            state.copy(distState = ds.copy(subView = DistSubView.PARAMS, activeFieldIndex = 0, editBuffer = ""))
                        }
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(distState = ds.copy(editBuffer = ds.editBuffer + ch))
                    }
                }
            }
            DistSubView.PARAMS -> {
                val keys = distributionFieldKeys(ds.distType, ds.isListMode)
                when (key) {
                    KeyId.AC -> state.copy(distState = ds.copy(subView = DistSubView.SELECT_TYPE, editBuffer = "", error = null))
                    KeyId.DEL -> state.copy(distState = ds.copy(editBuffer = ds.editBuffer.dropLast(1)))
                    KeyId.UP -> commitDistParam(state, keys).let { s ->
                        s.copy(distState = s.distState.copy(activeFieldIndex = (ds.activeFieldIndex - 1 + keys.size) % keys.size))
                    }
                    KeyId.DOWN -> commitDistParam(state, keys).let { s ->
                        s.copy(distState = s.distState.copy(activeFieldIndex = (ds.activeFieldIndex + 1) % keys.size))
                    }
                    KeyId.EQUALS -> {
                        val committed = commitDistParam(state, keys)
                        if (ds.activeFieldIndex < keys.lastIndex) {
                            committed.copy(distState = committed.distState.copy(activeFieldIndex = ds.activeFieldIndex + 1))
                        } else {
                            evaluateDistribution(committed)
                        }
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(distState = ds.copy(editBuffer = ds.editBuffer + ch))
                    }
                }
            }
            DistSubView.RESULT -> {
                if (key == KeyId.AC || key == KeyId.EQUALS) {
                    state.copy(distState = ds.copy(subView = DistSubView.PARAMS))
                } else state
            }
        }
    }

    private fun commitDistListRow(state: CalculatorUiState): CalculatorUiState {
        val ds = state.distState
        if (ds.editBuffer.isBlank()) return state
        val list = ds.listValues.toMutableList()
        if (ds.selectedListRow < list.size) list[ds.selectedListRow] = ds.editBuffer else list.add(ds.editBuffer)
        return state.copy(distState = ds.copy(listValues = list, editBuffer = ""))
    }

    private fun commitDistParam(state: CalculatorUiState, keys: List<String>): CalculatorUiState {
        val ds = state.distState
        if (ds.editBuffer.isBlank() || ds.activeFieldIndex !in keys.indices) return state
        val fieldKey = keys[ds.activeFieldIndex]
        return state.copy(distState = ds.copy(fields = ds.fields + (fieldKey to ds.editBuffer), editBuffer = ""))
    }

    fun evaluateDistribution(state: CalculatorUiState): CalculatorUiState {
        val ds = state.distState
        fun num(k: String): Double = ds.fields[k]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        return try {
            val fmt = { d: Double -> SymbolicFormatter.formatGermanNumber(d, state.formatConfig) }
            val lines = if (ds.isListMode) {
                ds.listValues.mapNotNull { raw ->
                    val xv = raw.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
                    val p = when (ds.distType) {
                        DistributionType.NORMAL_PD -> DistributionEngine.normalPd(xv, num("σ"), num("μ"))
                        DistributionType.BINOMIAL_PD -> DistributionEngine.binomialPd(xv.toInt(), num("N").toInt(), num("p"))
                        DistributionType.BINOMIAL_CD -> DistributionEngine.binomialCd(xv.toInt(), num("N").toInt(), num("p"))
                        DistributionType.POISSON_PD -> DistributionEngine.poissonPd(xv.toInt(), num("λ"))
                        DistributionType.POISSON_CD -> DistributionEngine.poissonCd(xv.toInt(), num("λ"))
                        else -> 0.0
                    }
                    "x=$raw" to fmt(p)
                }
            } else {
                val res = when (ds.distType) {
                    DistributionType.NORMAL_PD -> "p =" to fmt(DistributionEngine.normalPd(num("x"), num("σ"), num("μ")))
                    DistributionType.NORMAL_CD -> "P =" to fmt(DistributionEngine.normalCd(num("Lower"), num("Upper"), num("σ"), num("μ")))
                    DistributionType.INVERSE_NORMAL -> "xInv =" to fmt(DistributionEngine.inverseNormal(num("Fläche"), num("σ"), num("μ")))
                    DistributionType.BINOMIAL_PD -> "P =" to fmt(DistributionEngine.binomialPd(num("x").toInt(), num("N").toInt(), num("p")))
                    DistributionType.BINOMIAL_CD -> "P =" to fmt(DistributionEngine.binomialCd(num("x").toInt(), num("N").toInt(), num("p")))
                    DistributionType.POISSON_PD -> "P =" to fmt(DistributionEngine.poissonPd(num("x").toInt(), num("λ")))
                    DistributionType.POISSON_CD -> "P =" to fmt(DistributionEngine.poissonCd(num("x").toInt(), num("λ")))
                }
                listOf(res)
            }
            state.copy(distState = ds.copy(subView = DistSubView.RESULT, resultLines = lines, error = null))
        } catch (e: Exception) {
            state.copy(distState = ds.copy(error = e.message ?: "Math FEHLER"))
        }
    }

    // --- Mode 8: Tabellenkalkulation ---
    private fun handleSpreadsheet(state: CalculatorUiState, key: KeyId, shift: Boolean, alpha: Boolean): CalculatorUiState {
        val ss = state.sheetState
        val activeId = SpreadsheetEngine.cellId(ss.selectedCol, ss.selectedRow)
        when (key) {
            KeyId.UP -> return commitSheetCell(state).let { s ->
                s.copy(sheetState = s.sheetState.copy(selectedRow = (ss.selectedRow - 1).coerceAtLeast(0), isEditing = false))
            }
            KeyId.DOWN -> return commitSheetCell(state).let { s ->
                s.copy(sheetState = s.sheetState.copy(selectedRow = (ss.selectedRow + 1).coerceAtMost(SpreadsheetEngine.MAX_ROWS - 1), isEditing = false))
            }
            KeyId.LEFT -> return commitSheetCell(state).let { s ->
                s.copy(sheetState = s.sheetState.copy(selectedCol = (ss.selectedCol - 1).coerceAtLeast(0), isEditing = false))
            }
            KeyId.RIGHT -> return commitSheetCell(state).let { s ->
                s.copy(sheetState = s.sheetState.copy(selectedCol = (ss.selectedCol + 1).coerceAtMost(4), isEditing = false))
            }
            KeyId.AC -> return state.copy(sheetState = ss.copy(editBuffer = "", isEditing = false))
            KeyId.DEL -> {
                return if (ss.isEditing) {
                    state.copy(sheetState = ss.copy(editBuffer = ss.editBuffer.dropLast(1)))
                } else {
                    val updated = ss.cells - activeId
                    state.copy(sheetState = ss.copy(cells = updated, evaluated = SpreadsheetEngine.evaluateAll(updated)))
                }
            }
            KeyId.EQUALS -> {
                val committed = commitSheetCell(state)
                return committed.copy(
                    sheetState = committed.sheetState.copy(
                        selectedRow = (ss.selectedRow + 1).coerceAtMost(SpreadsheetEngine.MAX_ROWS - 1),
                        isEditing = false
                    )
                )
            }
            else -> {}
        }
        val token = when {
            alpha && key == KeyId.CALC -> "="
            alpha && key == KeyId.NEGATE -> "A"
            alpha && key == KeyId.DMS -> "B"
            alpha && key == KeyId.RECIPROCAL -> "C"
            alpha && key == KeyId.SIN -> "D"
            alpha && key == KeyId.COS -> "E"
            key == KeyId.PLUS -> "+"
            key == KeyId.MINUS -> "-"
            key == KeyId.MUL -> "*"
            key == KeyId.DIV -> "/"
            key == KeyId.LPAREN -> "("
            key == KeyId.RPAREN -> ")"
            else -> keyToNumericChar(key) ?: ""
        }
        if (token.isEmpty()) return state
        val startBuf = if (ss.isEditing) ss.editBuffer else ""
        return state.copy(sheetState = ss.copy(editBuffer = startBuf + token, isEditing = true))
    }

    private fun commitSheetCell(state: CalculatorUiState): CalculatorUiState {
        val ss = state.sheetState
        if (!ss.isEditing) return state
        val id = SpreadsheetEngine.cellId(ss.selectedCol, ss.selectedRow)
        val updated = if (ss.editBuffer.isBlank()) ss.cells - id else ss.cells + (id to ss.editBuffer)
        return state.copy(
            sheetState = ss.copy(
                cells = updated,
                evaluated = SpreadsheetEngine.evaluateAll(updated),
                editBuffer = "",
                isEditing = false
            )
        )
    }

    // --- Mode 9: Wertetabelle ---
    private fun handleTable(state: CalculatorUiState, key: KeyId, shift: Boolean, alpha: Boolean): CalculatorUiState {
        val ts = state.tableState
        return when (ts.subView) {
            TableSubView.EDIT_F, TableSubView.EDIT_G -> {
                val activeDoc = if (ts.subView == TableSubView.EDIT_F) ts.fDoc else ts.gDoc
                if (key == KeyId.EQUALS) {
                    return if (ts.subView == TableSubView.EDIT_F) {
                        state.copy(tableState = ts.copy(subView = TableSubView.EDIT_G))
                    } else {
                        state.copy(tableState = ts.copy(subView = TableSubView.RANGE, activeRangeField = 0, rangeBuffer = ""))
                    }
                }
                if (key == KeyId.AC) {
                    val cleared = AstEditor.emptyDocument()
                    return state.copy(
                        tableState = if (ts.subView == TableSubView.EDIT_F) ts.copy(fDoc = cleared) else ts.copy(gDoc = cleared)
                    )
                }
                val nextDoc = applySimpleAstKey(activeDoc, key, shift, alpha)
                state.copy(
                    tableState = if (ts.subView == TableSubView.EDIT_F) ts.copy(fDoc = nextDoc) else ts.copy(gDoc = nextDoc)
                )
            }
            TableSubView.RANGE -> {
                when (key) {
                    KeyId.AC -> state.copy(tableState = ts.copy(subView = TableSubView.EDIT_F, rangeBuffer = ""))
                    KeyId.DEL -> state.copy(tableState = ts.copy(rangeBuffer = ts.rangeBuffer.dropLast(1)))
                    KeyId.UP -> commitTableRange(state).let { s ->
                        s.copy(tableState = s.tableState.copy(activeRangeField = (ts.activeRangeField + 2) % 3))
                    }
                    KeyId.DOWN -> commitTableRange(state).let { s ->
                        s.copy(tableState = s.tableState.copy(activeRangeField = (ts.activeRangeField + 1) % 3))
                    }
                    KeyId.EQUALS -> {
                        val committed = commitTableRange(state)
                        if (ts.activeRangeField < 2) {
                            committed.copy(tableState = committed.tableState.copy(activeRangeField = ts.activeRangeField + 1))
                        } else {
                            buildTableRows(committed)
                        }
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(tableState = ts.copy(rangeBuffer = ts.rangeBuffer + ch))
                    }
                }
            }
            TableSubView.TABLE_VIEW -> {
                when (key) {
                    KeyId.AC -> state.copy(tableState = ts.copy(subView = TableSubView.EDIT_F))
                    KeyId.UP -> state.copy(tableState = ts.copy(selectedRow = (ts.selectedRow - 1).coerceAtLeast(0)))
                    KeyId.DOWN -> state.copy(tableState = ts.copy(selectedRow = (ts.selectedRow + 1).coerceAtMost((ts.rows.size - 1).coerceAtLeast(0))))
                    else -> state
                }
            }
        }
    }

    private fun commitTableRange(state: CalculatorUiState): CalculatorUiState {
        val ts = state.tableState
        if (ts.rangeBuffer.isBlank()) return state
        val updated = when (ts.activeRangeField) {
            0 -> ts.copy(startText = ts.rangeBuffer, rangeBuffer = "")
            1 -> ts.copy(endText = ts.rangeBuffer, rangeBuffer = "")
            else -> ts.copy(stepText = ts.rangeBuffer, rangeBuffer = "")
        }
        return state.copy(tableState = updated)
    }

    private fun buildTableRows(state: CalculatorUiState): CalculatorUiState {
        val ts = state.tableState
        val start = ts.startText.replace(',', '.').toDoubleOrNull() ?: -2.0
        val end = ts.endText.replace(',', '.').toDoubleOrNull() ?: 2.0
        val step = ts.stepText.replace(',', '.').toDoubleOrNull() ?: 1.0
        if (step <= 0.0 || end < start) {
            return state.copy(tableState = ts.copy(error = "Bereich FEHLER"))
        }
        val rows = mutableListOf<Triple<Double, Double, Double?>>()
        var x = start
        var count = 0
        while (x <= end + step * 1e-6 && count < 45) {
            val ctx = EvaluationContext(angleUnit = state.angleUnit, variables = state.variables + ("X" to x))
            val fx = if (ts.fDoc.root.isEmpty) x else MathEvaluator.evaluate(ts.fDoc.root, ctx).toDouble()
            val gx = if (ts.gDoc.root.isEmpty) null else MathEvaluator.evaluate(ts.gDoc.root, ctx).toDouble()
            rows += Triple(x, fx, gx)
            x += step
            count++
        }
        return state.copy(tableState = ts.copy(subView = TableSubView.TABLE_VIEW, rows = rows, selectedRow = 0, error = null))
    }

    // --- Mode 10: Gleichung / Funktion ---
    private fun handleEquation(state: CalculatorUiState, key: KeyId): CalculatorUiState {
        val eq = state.eqState
        return when (eq.subView) {
            EquationSubView.SELECT_KIND -> {
                when (key) {
                    KeyId.NUM_1 -> state.copy(eqState = eq.copy(isPolynomial = false, subView = EquationSubView.SELECT_SIZE))
                    KeyId.NUM_2 -> state.copy(eqState = eq.copy(isPolynomial = true, subView = EquationSubView.SELECT_SIZE))
                    else -> state
                }
            }
            EquationSubView.SELECT_SIZE -> {
                if (key == KeyId.AC) return state.copy(eqState = eq.copy(subView = EquationSubView.SELECT_KIND))
                val sz = when (key) {
                    KeyId.NUM_2 -> 2; KeyId.NUM_3 -> 3; KeyId.NUM_4 -> 4
                    else -> null
                } ?: return state
                if (eq.isPolynomial) {
                    val defaults = when (sz) {
                        2 -> listOf("1", "-3", "2")
                        3 -> listOf("1", "-6", "11", "-6")
                        else -> listOf("1", "0", "-5", "0", "4")
                    }
                    state.copy(eqState = eq.copy(size = sz, polyCoeffs = defaults, selectedCol = 0, cellBuffer = "", subView = EquationSubView.INPUT_POLY))
                } else {
                    val grid = List(sz) { r -> List(sz + 1) { c -> if (r == c) "1" else if (c == sz) "${r + 1}" else "0" } }
                    state.copy(eqState = eq.copy(size = sz, linearCoeffs = grid, selectedRow = 0, selectedCol = 0, cellBuffer = "", subView = EquationSubView.INPUT_LINEAR))
                }
            }
            EquationSubView.INPUT_LINEAR -> {
                val rows = eq.size; val cols = eq.size + 1
                when (key) {
                    KeyId.AC -> state.copy(eqState = eq.copy(subView = EquationSubView.SELECT_KIND, cellBuffer = ""))
                    KeyId.DEL -> state.copy(eqState = eq.copy(cellBuffer = eq.cellBuffer.dropLast(1)))
                    KeyId.UP -> commitLinearCell(state).let { s ->
                        s.copy(eqState = s.eqState.copy(selectedRow = (eq.selectedRow - 1 + rows) % rows))
                    }
                    KeyId.DOWN -> commitLinearCell(state).let { s ->
                        s.copy(eqState = s.eqState.copy(selectedRow = (eq.selectedRow + 1) % rows))
                    }
                    KeyId.LEFT -> commitLinearCell(state).let { s ->
                        s.copy(eqState = s.eqState.copy(selectedCol = (eq.selectedCol - 1 + cols) % cols))
                    }
                    KeyId.RIGHT -> commitLinearCell(state).let { s ->
                        s.copy(eqState = s.eqState.copy(selectedCol = (eq.selectedCol + 1) % cols))
                    }
                    KeyId.EQUALS -> {
                        val committed = commitLinearCell(state)
                        if (eq.cellBuffer.isBlank() && eq.selectedRow == rows - 1 && eq.selectedCol == cols - 1) {
                            solveLinear(committed)
                        } else if (eq.selectedRow == rows - 1 && eq.selectedCol == cols - 1) {
                            solveLinear(committed)
                        } else {
                            val nc = (eq.selectedCol + 1) % cols
                            val nr = if (nc == 0) (eq.selectedRow + 1) % rows else eq.selectedRow
                            committed.copy(eqState = committed.eqState.copy(selectedRow = nr, selectedCol = nc))
                        }
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(eqState = eq.copy(cellBuffer = eq.cellBuffer + ch))
                    }
                }
            }
            EquationSubView.INPUT_POLY -> {
                val n = eq.size + 1
                when (key) {
                    KeyId.AC -> state.copy(eqState = eq.copy(subView = EquationSubView.SELECT_KIND, cellBuffer = ""))
                    KeyId.DEL -> state.copy(eqState = eq.copy(cellBuffer = eq.cellBuffer.dropLast(1)))
                    KeyId.LEFT -> commitPolyCell(state).let { s ->
                        s.copy(eqState = s.eqState.copy(selectedCol = (eq.selectedCol - 1 + n) % n))
                    }
                    KeyId.RIGHT -> commitPolyCell(state).let { s ->
                        s.copy(eqState = s.eqState.copy(selectedCol = (eq.selectedCol + 1) % n))
                    }
                    KeyId.EQUALS -> {
                        val committed = commitPolyCell(state)
                        if (eq.selectedCol == n - 1) {
                            solvePoly(committed)
                        } else {
                            committed.copy(eqState = committed.eqState.copy(selectedCol = eq.selectedCol + 1))
                        }
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(eqState = eq.copy(cellBuffer = eq.cellBuffer + ch))
                    }
                }
            }
            EquationSubView.RESULT -> {
                if (key == KeyId.AC || key == KeyId.EQUALS) {
                    state.copy(
                        eqState = eq.copy(
                            subView = if (eq.isPolynomial) EquationSubView.INPUT_POLY else EquationSubView.INPUT_LINEAR
                        )
                    )
                } else state
            }
        }
    }

    private fun commitLinearCell(state: CalculatorUiState): CalculatorUiState {
        val eq = state.eqState
        if (eq.cellBuffer.isBlank()) return state
        val grid = eq.linearCoeffs.map { it.toMutableList() }.toMutableList()
        if (eq.selectedRow in grid.indices && eq.selectedCol in grid[0].indices) {
            grid[eq.selectedRow][eq.selectedCol] = eq.cellBuffer
        }
        return state.copy(eqState = eq.copy(linearCoeffs = grid, cellBuffer = ""))
    }

    private fun commitPolyCell(state: CalculatorUiState): CalculatorUiState {
        val eq = state.eqState
        if (eq.cellBuffer.isBlank()) return state
        val list = eq.polyCoeffs.toMutableList()
        if (eq.selectedCol in list.indices) list[eq.selectedCol] = eq.cellBuffer
        return state.copy(eqState = eq.copy(polyCoeffs = list, cellBuffer = ""))
    }

    fun solveLinear(state: CalculatorUiState): CalculatorUiState {
        val committed = commitLinearCell(state)
        val eq = committed.eqState
        val n = eq.size
        val mat = Array(n) { r -> DoubleArray(n) { c -> eq.linearCoeffs[r][c].replace(',', '.').toDoubleOrNull() ?: 0.0 } }
        val rhs = DoubleArray(n) { r -> eq.linearCoeffs[r][n].replace(',', '.').toDoubleOrNull() ?: 0.0 }
        val sol = EquationInequalityEngine.solveLinearSystem(mat, rhs)
        return committed.copy(eqState = eq.copy(subView = EquationSubView.RESULT, linearResult = sol))
    }

    fun solvePoly(state: CalculatorUiState): CalculatorUiState {
        val committed = commitPolyCell(state)
        val eq = committed.eqState
        val coeffs = DoubleArray(eq.polyCoeffs.size) { i -> eq.polyCoeffs[i].replace(',', '.').toDoubleOrNull() ?: 0.0 }
        val sol = EquationInequalityEngine.solvePolynomial(coeffs)
        return committed.copy(eqState = eq.copy(subView = EquationSubView.RESULT, polyResult = sol))
    }

    // --- Mode 11: Ungleichungen ---
    private fun handleInequality(state: CalculatorUiState, key: KeyId): CalculatorUiState {
        val iq = state.ineqState
        return when (iq.subView) {
            InequalitySubView.SELECT_DEGREE -> {
                val deg = when (key) { KeyId.NUM_2 -> 2; KeyId.NUM_3 -> 3; KeyId.NUM_4 -> 4; else -> null } ?: return state
                val defaults = when (deg) {
                    2 -> listOf("1", "0", "-4")
                    3 -> listOf("1", "-2", "-1", "2")
                    else -> listOf("1", "0", "-5", "0", "4")
                }
                state.copy(ineqState = iq.copy(degree = deg, coeffs = defaults, subView = InequalitySubView.SELECT_OP))
            }
            InequalitySubView.SELECT_OP -> {
                if (key == KeyId.AC) return state.copy(ineqState = iq.copy(subView = InequalitySubView.SELECT_DEGREE))
                val op = when (key) {
                    KeyId.NUM_1 -> EquationInequalityEngine.InequalityOp.GT
                    KeyId.NUM_2 -> EquationInequalityEngine.InequalityOp.LT
                    KeyId.NUM_3 -> EquationInequalityEngine.InequalityOp.GE
                    KeyId.NUM_4 -> EquationInequalityEngine.InequalityOp.LE
                    else -> null
                } ?: return state
                state.copy(ineqState = iq.copy(op = op, selectedCoeff = 0, cellBuffer = "", subView = InequalitySubView.INPUT_COEFFS))
            }
            InequalitySubView.INPUT_COEFFS -> {
                val n = iq.degree + 1
                when (key) {
                    KeyId.AC -> state.copy(ineqState = iq.copy(subView = InequalitySubView.SELECT_DEGREE, cellBuffer = ""))
                    KeyId.DEL -> state.copy(ineqState = iq.copy(cellBuffer = iq.cellBuffer.dropLast(1)))
                    KeyId.LEFT -> commitIneqCell(state).let { s ->
                        s.copy(ineqState = s.ineqState.copy(selectedCoeff = (iq.selectedCoeff - 1 + n) % n))
                    }
                    KeyId.RIGHT -> commitIneqCell(state).let { s ->
                        s.copy(ineqState = s.ineqState.copy(selectedCoeff = (iq.selectedCoeff + 1) % n))
                    }
                    KeyId.EQUALS -> {
                        val committed = commitIneqCell(state)
                        if (iq.selectedCoeff == n - 1) {
                            solveInequality(committed)
                        } else {
                            committed.copy(ineqState = committed.ineqState.copy(selectedCoeff = iq.selectedCoeff + 1))
                        }
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(ineqState = iq.copy(cellBuffer = iq.cellBuffer + ch))
                    }
                }
            }
            InequalitySubView.RESULT -> {
                if (key == KeyId.AC || key == KeyId.EQUALS) {
                    state.copy(ineqState = iq.copy(subView = InequalitySubView.INPUT_COEFFS))
                } else state
            }
        }
    }

    private fun commitIneqCell(state: CalculatorUiState): CalculatorUiState {
        val iq = state.ineqState
        if (iq.cellBuffer.isBlank()) return state
        val list = iq.coeffs.toMutableList()
        if (iq.selectedCoeff in list.indices) list[iq.selectedCoeff] = iq.cellBuffer
        return state.copy(ineqState = iq.copy(coeffs = list, cellBuffer = ""))
    }

    fun solveInequality(state: CalculatorUiState): CalculatorUiState {
        val committed = commitIneqCell(state)
        val iq = committed.ineqState
        val coeffs = DoubleArray(iq.coeffs.size) { i -> iq.coeffs[i].replace(',', '.').toDoubleOrNull() ?: 0.0 }
        val text = EquationInequalityEngine.solveInequality(coeffs, iq.op)
        return committed.copy(ineqState = iq.copy(subView = InequalitySubView.RESULT, resultText = text))
    }

    // --- Mode 13: Verhältnisse ---
    private fun handleRatio(state: CalculatorUiState, key: KeyId, shift: Boolean): CalculatorUiState {
        val rs = state.ratioState
        return when (rs.subView) {
            RatioSubView.SELECT_FORM -> {
                when (key) {
                    KeyId.NUM_1 -> state.copy(ratioState = rs.copy(solveFirstRight = true, subView = RatioSubView.INPUT, activeField = 0, editBuffer = ""))
                    KeyId.NUM_2 -> state.copy(ratioState = rs.copy(solveFirstRight = false, subView = RatioSubView.INPUT, activeField = 0, editBuffer = ""))
                    else -> state
                }
            }
            RatioSubView.INPUT -> {
                when (key) {
                    KeyId.AC -> state.copy(ratioState = rs.copy(subView = RatioSubView.SELECT_FORM, editBuffer = ""))
                    KeyId.DEL -> state.copy(ratioState = rs.copy(editBuffer = rs.editBuffer.dropLast(1)))
                    KeyId.LEFT, KeyId.UP -> commitRatioCell(state).let { s ->
                        s.copy(ratioState = s.ratioState.copy(activeField = (rs.activeField + 2) % 3))
                    }
                    KeyId.RIGHT, KeyId.DOWN -> commitRatioCell(state).let { s ->
                        s.copy(ratioState = s.ratioState.copy(activeField = (rs.activeField + 1) % 3))
                    }
                    KeyId.EQUALS -> {
                        val committed = commitRatioCell(state)
                        if (rs.activeField == 2) {
                            solveRatio(committed)
                        } else {
                            committed.copy(ratioState = committed.ratioState.copy(activeField = rs.activeField + 1))
                        }
                    }
                    else -> {
                        val ch = keyToNumericChar(key) ?: return state
                        state.copy(ratioState = rs.copy(editBuffer = rs.editBuffer + ch))
                    }
                }
            }
            RatioSubView.RESULT -> {
                when (key) {
                    KeyId.SD -> state.copy(ratioState = rs.copy(forceDecimal = !rs.forceDecimal))
                    KeyId.AC, KeyId.EQUALS -> state.copy(ratioState = rs.copy(subView = RatioSubView.INPUT))
                    else -> state
                }
            }
        }
    }

    private fun commitRatioCell(state: CalculatorUiState): CalculatorUiState {
        val rs = state.ratioState
        if (rs.editBuffer.isBlank()) return state
        val updated = when (rs.activeField) {
            0 -> rs.copy(aText = rs.editBuffer, editBuffer = "")
            1 -> rs.copy(bText = rs.editBuffer, editBuffer = "")
            else -> rs.copy(knownText = rs.editBuffer, editBuffer = "")
        }
        return state.copy(ratioState = updated)
    }

    fun solveRatio(state: CalculatorUiState): CalculatorUiState {
        val committed = commitRatioCell(state)
        val rs = committed.ratioState
        val a = rs.aText.replace(',', '.').toDoubleOrNull() ?: 1.0
        val b = rs.bText.replace(',', '.').toDoubleOrNull() ?: 1.0
        val k = rs.knownText.replace(',', '.').toDoubleOrNull() ?: 1.0
        val res = EquationInequalityEngine.solveRatio(a, b, k, rs.solveFirstRight)
        return committed.copy(ratioState = rs.copy(subView = RatioSubView.RESULT, result = res, forceDecimal = false))
    }

    // --- Helpers ---
    private fun keyToSmallInt(key: KeyId): Int? = when (key) {
        KeyId.NUM_1 -> 1; KeyId.NUM_2 -> 2; KeyId.NUM_3 -> 3; KeyId.NUM_4 -> 4
        else -> null
    }

    fun keyToNumericChar(key: KeyId): String? = when (key) {
        KeyId.NUM_0 -> "0"; KeyId.NUM_1 -> "1"; KeyId.NUM_2 -> "2"; KeyId.NUM_3 -> "3"
        KeyId.NUM_4 -> "4"; KeyId.NUM_5 -> "5"; KeyId.NUM_6 -> "6"; KeyId.NUM_7 -> "7"
        KeyId.NUM_8 -> "8"; KeyId.NUM_9 -> "9"
        KeyId.DECIMAL -> ","
        KeyId.NEGATE, KeyId.MINUS -> "-"
        else -> null
    }

    private fun applySimpleAstKey(doc: EditorDocument, key: KeyId, shift: Boolean, alpha: Boolean): EditorDocument {
        return when (key) {
            KeyId.LEFT -> AstEditor.moveLeft(doc)
            KeyId.RIGHT -> AstEditor.moveRight(doc)
            KeyId.UP -> AstEditor.moveUp(doc) ?: doc
            KeyId.DOWN -> AstEditor.moveDown(doc) ?: doc
            KeyId.DEL -> AstEditor.deleteBeforeCursor(doc)
            KeyId.X_VAR -> AstEditor.insertToken(doc, "X", TokenKind.VARIABLE)
            KeyId.FRAC -> AstEditor.insertFraction(doc)
            KeyId.SQRT -> if (shift) AstEditor.insertCubeRoot(doc) else AstEditor.insertRoot(doc)
            KeyId.X_SQ -> AstEditor.insertPower(doc, if (shift) "3" else "2")
            KeyId.X_POW -> if (shift) AstEditor.insertNthRoot(doc) else AstEditor.insertPower(doc)
            KeyId.LOG_BASE -> if (shift) AstEditor.insertBaseWithPower(doc, "10", TokenKind.DIGIT) else AstEditor.insertLogBase(doc)
            KeyId.LN -> if (shift) AstEditor.insertBaseWithPower(doc, "e", TokenKind.CONSTANT) else AstEditor.insertToken(doc, "ln(", TokenKind.FUNCTION)
            KeyId.SIN -> AstEditor.insertToken(doc, if (shift) "sin⁻¹(" else "sin(", TokenKind.FUNCTION)
            KeyId.COS -> AstEditor.insertToken(doc, if (shift) "cos⁻¹(" else "cos(", TokenKind.FUNCTION)
            KeyId.TAN -> AstEditor.insertToken(doc, if (shift) "tan⁻¹(" else "tan(", TokenKind.FUNCTION)
            KeyId.LPAREN -> if (shift) AstEditor.insertAbs(doc) else AstEditor.insertToken(doc, "(", TokenKind.PAREN)
            KeyId.RPAREN -> AstEditor.insertToken(doc, ")", TokenKind.PAREN)
            KeyId.PLUS -> AstEditor.insertToken(doc, "+", TokenKind.OPERATOR)
            KeyId.MINUS -> AstEditor.insertToken(doc, "-", TokenKind.OPERATOR)
            KeyId.NEGATE -> AstEditor.insertToken(doc, "-", TokenKind.OPERATOR)
            KeyId.MUL -> AstEditor.insertToken(doc, "×", TokenKind.OPERATOR)
            KeyId.DIV -> AstEditor.insertToken(doc, "÷", TokenKind.OPERATOR)
            KeyId.EXP10 -> when {
                shift -> AstEditor.insertToken(doc, "π", TokenKind.CONSTANT)
                alpha -> AstEditor.insertToken(doc, "e", TokenKind.CONSTANT)
                else -> AstEditor.insertToken(doc, "×10^", TokenKind.OPERATOR)
            }
            else -> {
                val ch = keyToNumericChar(key) ?: return doc
                val kind = if (ch == ",") TokenKind.DECIMAL else TokenKind.DIGIT
                AstEditor.insertToken(doc, ch, kind)
            }
        }
    }
}
