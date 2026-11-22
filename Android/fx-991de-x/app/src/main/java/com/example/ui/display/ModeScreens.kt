package com.example.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalCalculatorPalette
import com.example.viewmodel.*
import com.naveen.fx991dex.engine.*

/**
 * In-LCD screen router for specialized modes:
 * Mode 3 (Basis-N), Mode 4 (Matrizen), Mode 5 (Vektorrechnung), Mode 6 (Statistik),
 * Mode 7 (Verteilungsfkt.), Mode 8 (Tabellenkalk.), Mode 9 (Tabellen),
 * Mode 10 (Gleichung/Funkt.), Mode 11 (Ungleichungen), Mode 13 (Verhältnisse).
 */
@Composable
fun SpecializedModeDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    when (state.mode) {
        CalcMode.BASE_N -> BaseNDisplay(state)
        CalcMode.MATRIX -> MatrixDisplay(state, vm)
        CalcMode.VECTOR -> VectorDisplay(state, vm)
        CalcMode.STATISTICS -> StatisticsDisplay(state, vm)
        CalcMode.DISTRIBUTION -> DistributionDisplay(state, vm)
        CalcMode.SPREADSHEET -> SpreadsheetDisplay(state, vm)
        CalcMode.TABLE -> TableDisplay(state)
        CalcMode.EQUATION -> EquationDisplay(state, vm)
        CalcMode.INEQUALITY -> InequalityDisplay(state, vm)
        CalcMode.RATIO -> RatioDisplay(state, vm)
        else -> {}
    }
}

@Composable
private fun BaseNDisplay(state: CalculatorUiState) {
    val palette = LocalCalculatorPalette.current
    val bn = state.baseN
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Basis: ${bn.currentBase.label.uppercase()} (x²/x^□/log/ln)",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.cursorAccent
            )
            Text("OPTN: AND/OR/XOR/NOT", fontFamily = JetBrainsMonoFamily, fontSize = 9.5.sp, color = palette.displaySecondaryText)
        }
        Text(
            text = bn.input.ifEmpty { "0" },
            fontFamily = JetBrainsMonoFamily,
            fontSize = 18.sp,
            color = palette.displayPrimaryText,
            maxLines = 1,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
        if (bn.error != null) {
            Text(bn.error, fontFamily = JetBrainsMonoFamily, fontSize = 15.sp, color = palette.alphaRed)
        } else {
            // Live simultaneous readout in active base + compact reference strip for all 4 bases
            Text(
                text = "${BaseNEngine.formatValue(bn.resultValue, bn.currentBase)}  [${bn.currentBase.label}]",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = palette.displayPrimaryText,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NumberBase.entries.forEach { b ->
                    Text(
                        text = "${b.label}:${BaseNEngine.formatValue(bn.resultValue, b).takeLast(12)}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = if (b == bn.currentBase) palette.shiftGold else palette.displaySecondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val ms = state.matrixState
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (ms.subView) {
            MatrixSubView.DEFINE_SELECT -> {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Matrix definieren (1–4)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                    Text("Zur Rechnung ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.selectMatrixSubView(MatrixSubView.CALC) })
                }
                val slots = listOf("MatA", "MatB", "MatC", "MatD")
                slots.forEachIndexed { idx, slot ->
                    val existing = ms.matrices[slot]
                    val dimLabel = existing?.let { "${it.size}×${it[0].size}" } ?: "Leer"
                    LcdSelectableRow("${idx + 1}: $slot", dimLabel) {
                        vm.selectMatrixSubView(MatrixSubView.DEFINE_DIM, slot)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
            MatrixSubView.DEFINE_DIM -> {
                Text("${ms.activeSlot}: Dimension wählen (Zeilen × Spalten)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2 to 2, 2 to 3, 3 to 3, 4 to 4).forEach { (r, c) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.displaySlotBg)
                                .border(1.dp, palette.cursorAccent, RoundedCornerShape(4.dp))
                                .clickable { vm.selectMatrixDim(r, c) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("${r}×${c}", fontFamily = JetBrainsMonoFamily, fontSize = 13.sp, color = palette.displayPrimaryText)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Oder Ziffer 1–4 für Zeilen drücken · [AC] Rechnung", fontFamily = InterFontFamily, fontSize = 10.sp, color = palette.displaySecondaryText)
            }
            MatrixSubView.DEFINE_EDIT -> {
                val mat = ms.matrices[ms.activeSlot] ?: MatrixEngine.empty(ms.editRows, ms.editCols)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${ms.activeSlot} (${mat.size}×${mat[0].size})", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                    Text("[AC] Rechnen · OPTN: Det/Trn", fontFamily = JetBrainsMonoFamily, fontSize = 9.5.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.selectMatrixSubView(MatrixSubView.CALC) })
                }
                Spacer(Modifier.height(3.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    for (r in mat.indices) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            for (c in mat[r].indices) {
                                val sel = r == ms.selectedRow && c == ms.selectedCol
                                val txt = if (sel && ms.cellInput.isNotEmpty()) ms.cellInput else SymbolicFormatter.formatGermanNumber(mat[r][c])
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (sel) palette.displayHighlightBg else palette.displaySlotBg.copy(alpha = 0.5f))
                                        .border(if (sel) 1.dp else 0.5.dp, if (sel) palette.cursorAccent else palette.displayBorder, RoundedCornerShape(3.dp))
                                        .padding(vertical = 3.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(txt, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, color = palette.displayPrimaryText, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            MatrixSubView.CALC -> {}
        }
    }
}

@Composable
private fun VectorDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val vs = state.vectorState
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (vs.subView) {
            VectorSubView.DEFINE_SELECT -> {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vektor definieren (1–4)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                    Text("Zur Rechnung ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.selectVectorSubView(VectorSubView.CALC) })
                }
                listOf("VctA", "VctB", "VctC", "VctD").forEachIndexed { idx, slot ->
                    val existing = vs.vectors[slot]
                    LcdSelectableRow("${idx + 1}: $slot", existing?.let { "${it.size}D" } ?: "Leer") {
                        vm.selectVectorSubView(VectorSubView.DEFINE_DIM, slot)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
            VectorSubView.DEFINE_DIM -> {
                Text("${vs.activeSlot}: Dimension wählen", fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LcdSelectableRow("2: 2D-Vektor (x, y)", "") { vm.selectVectorDim(2) }
                }
                Spacer(Modifier.height(4.dp))
                LcdSelectableRow("3: 3D-Vektor (x, y, z)", "") { vm.selectVectorDim(3) }
            }
            VectorSubView.DEFINE_EDIT -> {
                val vec = vs.vectors[vs.activeSlot] ?: DoubleArray(vs.editDim)
                val labels = listOf("x", "y", "z")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${vs.activeSlot} (${vec.size}D)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                    Text("[AC] Rechnen · OPTN: • / × / Winkel", fontFamily = JetBrainsMonoFamily, fontSize = 9.5.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.selectVectorSubView(VectorSubView.CALC) })
                }
                Spacer(Modifier.height(4.dp))
                vec.forEachIndexed { idx, v ->
                    val sel = idx == vs.selectedComp
                    val txt = if (sel && vs.cellInput.isNotEmpty()) vs.cellInput else SymbolicFormatter.formatGermanNumber(v)
                    LcdSelectableRow("${labels.getOrElse(idx) { "v$idx" }} =", txt, isSelected = sel) {}
                    Spacer(Modifier.height(3.dp))
                }
            }
            VectorSubView.CALC -> {}
        }
    }
}

@Composable
private fun StatisticsDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val st = state.statState
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (st.subView) {
            StatSubView.SELECT_TYPE -> {
                Text("Statistik — Typ wählen (1–5)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                Spacer(Modifier.height(3.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(StatType.entries) { _, t ->
                        LcdSelectableRow(t.germanLabel, if (t.isTwoVar) "x, y" else "x") { vm.selectStatType(t) }
                    }
                }
            }
            StatSubView.TABLE -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(st.statType.germanLabel, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                    Text("Auswerten [AC] ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.shiftGold, modifier = Modifier.clickable { vm.triggerStatCompute() })
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth().background(palette.displaySlotBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("#", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.displaySecondaryText, modifier = Modifier.width(24.dp))
                    Text("x", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.cursorAccent, modifier = Modifier.weight(1f))
                    if (st.statType.isTwoVar) {
                        Text("y", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.cursorAccent, modifier = Modifier.weight(1f))
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(st.rows + StatRow(0.0, 0.0, 1.0)) { idx, row ->
                        val isNew = idx == st.rows.size
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text("${idx + 1}", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.displaySecondaryText, modifier = Modifier.width(24.dp))
                            val selX = idx == st.selectedRow && st.selectedCol == 0
                            val xTxt = if (selX && st.cellInput.isNotEmpty()) st.cellInput else if (isNew) "□" else SymbolicFormatter.formatGermanNumber(row.x)
                            Text(
                                text = xTxt,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = palette.displayPrimaryText,
                                modifier = Modifier.weight(1f).background(if (selX) palette.displayHighlightBg else palette.displayBackground).padding(horizontal = 4.dp)
                            )
                            if (st.statType.isTwoVar) {
                                val selY = idx == st.selectedRow && st.selectedCol == 1
                                val yTxt = if (selY && st.cellInput.isNotEmpty()) st.cellInput else if (isNew) "□" else SymbolicFormatter.formatGermanNumber(row.y)
                                Text(
                                    text = yTxt,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.5.sp,
                                    color = palette.displayPrimaryText,
                                    modifier = Modifier.weight(1f).background(if (selY) palette.displayHighlightBg else palette.displayBackground).padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            StatSubView.RESULTS -> {
                val fmt = { d: Double -> SymbolicFormatter.formatGermanNumber(d, state.formatConfig) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ergebnisse (${st.statType.germanLabel})", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                    Text("[AC] Tabelle", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.displaySecondaryText)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    val items = mutableListOf<Pair<String, String>>()
                    st.regression?.let { reg ->
                        items += "a =" to fmt(reg.a)
                        items += "b =" to fmt(reg.b)
                        reg.c?.let { items += "c =" to fmt(it) }
                        reg.r?.let { items += "r =" to fmt(it) }
                        reg.r2?.let { items += "r² =" to fmt(it) }
                    }
                    st.oneVarStats?.let { s ->
                        items += "x̄ (Mittelwert) =" to fmt(s.mean)
                        items += "σx (Std.Abw.) =" to fmt(s.popStdDev)
                        items += "sx (Stichpr.) =" to fmt(s.sampleStdDev)
                        items += "n =" to fmt(s.n)
                        items += "minX / Q1 =" to "${fmt(s.minX)} / ${fmt(s.q1)}"
                        items += "Med / Q3 / maxX =" to "${fmt(s.median)} / ${fmt(s.q3)} / ${fmt(s.maxX)}"
                    }
                    itemsIndexed(items) { _, (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.displaySecondaryText)
                            Text(v, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.displayPrimaryText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val ds = state.distState
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (ds.subView) {
            DistSubView.SELECT_TYPE -> {
                Text("Verteilungsfunktionen (1–7)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                Spacer(Modifier.height(2.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(DistributionType.entries) { _, t ->
                        LcdSelectableRow(t.germanName, "▸") { vm.selectDistType(t) }
                    }
                }
            }
            DistSubView.MODE_SELECT -> {
                Text(ds.distType.germanName, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                Spacer(Modifier.height(6.dp))
                LcdSelectableRow("1: Liste (mehrere x-Werte)", "") { vm.selectDistInputMode(true) }
                Spacer(Modifier.height(4.dp))
                LcdSelectableRow("2: Variable (Einzelwert)", "") { vm.selectDistInputMode(false) }
            }
            DistSubView.LIST_INPUT -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${ds.distType.germanName} — Liste (x)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                    Text("[=] Parameter ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.shiftGold)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    itemsIndexed(ds.listValues) { idx, v ->
                        val sel = idx == ds.selectedListRow
                        val txt = if (sel && ds.editBuffer.isNotEmpty()) ds.editBuffer else v
                        LcdSelectableRow("x${idx + 1} =", txt, isSelected = sel) {}
                    }
                }
            }
            DistSubView.PARAMS -> {
                val keys = SpecializedModeHandler.distributionFieldKeys(ds.distType, ds.isListMode)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ds.distType.germanName, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                    Text("Berechnen [=] ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.triggerDistCompute() })
                }
                Spacer(Modifier.height(3.dp))
                keys.forEachIndexed { idx, k ->
                    val sel = idx == ds.activeFieldIndex
                    val v = if (sel && ds.editBuffer.isNotEmpty()) ds.editBuffer else (ds.fields[k] ?: "0")
                    LcdSelectableRow("$k =", v, isSelected = sel) {}
                    Spacer(Modifier.height(2.dp))
                }
                ds.error?.let { Text(it, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.alphaRed) }
            }
            DistSubView.RESULT -> {
                Text("${ds.distType.germanName} — Ergebnis", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    itemsIndexed(ds.resultLines) { _, (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontFamily = JetBrainsMonoFamily, fontSize = 13.sp, color = palette.displaySecondaryText)
                            Text(value, fontFamily = JetBrainsMonoFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.displayPrimaryText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpreadsheetDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val ss = state.sheetState
    val activeId = SpreadsheetEngine.cellId(ss.selectedCol, ss.selectedRow)
    val formulaText = if (ss.isEditing) ss.editBuffer else (ss.cells[activeId] ?: "")

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp)) {
        // Formula bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.displaySlotBg, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$activeId: $formulaText", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = palette.cursorAccent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("OPTN: Summe/Mittelw.", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = palette.displaySecondaryText)
        }
        Spacer(Modifier.height(2.dp))
        // Column headers A..E
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("", modifier = Modifier.width(20.dp))
            SpreadsheetEngine.columns.forEachIndexed { cIdx, colName ->
                Text(
                    text = colName,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (cIdx == ss.selectedCol) palette.cursorAccent else palette.displaySecondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Visible 4-row sliding window
        val startRow = (ss.selectedRow - 1).coerceIn(0, SpreadsheetEngine.MAX_ROWS - 4)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in startRow until (startRow + 4)) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text("${r + 1}", fontFamily = JetBrainsMonoFamily, fontSize = 9.5.sp, color = palette.displaySecondaryText, modifier = Modifier.width(20.dp))
                    for (c in 0..4) {
                        val id = SpreadsheetEngine.cellId(c, r)
                        val sel = c == ss.selectedCol && r == ss.selectedRow
                        val displayVal = when {
                            sel && ss.isEditing -> ss.editBuffer
                            ss.cells.containsKey(id) -> ss.evaluated[id]?.fold(
                                onSuccess = { SymbolicFormatter.formatGermanNumber(it, state.formatConfig) },
                                onFailure = { "FEHLER" }
                            ) ?: ""
                            else -> ""
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp)
                                .background(if (sel) palette.displayHighlightBg else palette.displaySlotBg.copy(alpha = 0.35f))
                                .border(if (sel) 1.dp else 0.5.dp, if (sel) palette.cursorAccent else palette.displayBorder)
                                .clickable { vm.selectSpreadsheetCell(c, r) }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(displayVal, fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.displayPrimaryText, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableDisplay(state: CalculatorUiState) {
    val palette = LocalCalculatorPalette.current
    val ts = state.tableState
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (ts.subView) {
            TableSubView.EDIT_F -> {
                Text("Wertetabelle — Funktion f(x) eingeben, dann [=]", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text("f(x) = ", fontFamily = JetBrainsMonoFamily, fontSize = 18.sp, color = palette.shiftGold)
                    NaturalMathSequenceView(ts.fDoc.root, ts.fDoc.cursor, baseFontSize = 19.sp, isRoot = true)
                }
            }
            TableSubView.EDIT_G -> {
                Text("Optional g(x) eingeben (leer lassen + [=] überspringt)", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text("g(x) = ", fontFamily = JetBrainsMonoFamily, fontSize = 18.sp, color = palette.shiftGold)
                    NaturalMathSequenceView(ts.gDoc.root, ts.gDoc.cursor, baseFontSize = 19.sp, isRoot = true)
                }
            }
            TableSubView.RANGE -> {
                Text("Tabellenbereich festlegen", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                Spacer(Modifier.height(4.dp))
                val fields = listOf("Start" to ts.startText, "Ende" to ts.endText, "Inkrement" to ts.stepText)
                fields.forEachIndexed { idx, (label, value) ->
                    val sel = idx == ts.activeRangeField
                    val shown = if (sel && ts.rangeBuffer.isNotEmpty()) ts.rangeBuffer else value
                    LcdSelectableRow("$label:", shown, isSelected = sel) {}
                    Spacer(Modifier.height(2.dp))
                }
                ts.error?.let { Text(it, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.alphaRed) }
            }
            TableSubView.TABLE_VIEW -> {
                val fmt = { d: Double -> SymbolicFormatter.formatGermanNumber(d, state.formatConfig) }
                val hasG = ts.rows.any { it.third != null }
                Row(modifier = Modifier.fillMaxWidth().background(palette.displaySlotBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("x", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = palette.cursorAccent, modifier = Modifier.weight(1f))
                    Text("f(x)", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = palette.shiftGold, modifier = Modifier.weight(1f))
                    if (hasG) Text("g(x)", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = palette.alphaRed, modifier = Modifier.weight(1f))
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(ts.rows) { idx, (x, fx, gx) ->
                        val sel = idx == ts.selectedRow
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (sel) palette.displayHighlightBg else palette.displayBackground)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(fmt(x), fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, color = palette.displayPrimaryText, modifier = Modifier.weight(1f))
                            Text(fmt(fx), fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, color = palette.displayPrimaryText, modifier = Modifier.weight(1f))
                            if (hasG) Text(gx?.let(fmt) ?: "—", fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, color = palette.displayPrimaryText, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquationDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val eq = state.eqState
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (eq.subView) {
            EquationSubView.SELECT_KIND -> {
                Text("Gleichung / Funktion wählen", fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                Spacer(Modifier.height(6.dp))
                LcdSelectableRow("1: Gleichungssystem (2–4 Unbek.)", "▸") { vm.selectEquationKind(false) }
                Spacer(Modifier.height(4.dp))
                LcdSelectableRow("2: Polynom-Gleich. (Grad 2–4)", "▸") { vm.selectEquationKind(true) }
            }
            EquationSubView.SELECT_SIZE -> {
                val prompt = if (eq.isPolynomial) "Polynom-Grad wählen (2–4)" else "Anzahl Unbekannte wählen (2–4)"
                Text(prompt, fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4).forEach { sz ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(palette.displaySlotBg)
                                .border(1.dp, palette.cursorAccent, RoundedCornerShape(5.dp))
                                .clickable { vm.selectEquationSize(sz) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$sz", fontFamily = JetBrainsMonoFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.displayPrimaryText)
                        }
                    }
                }
            }
            EquationSubView.INPUT_LINEAR -> {
                val vars = listOf("x", "y", "z", "t")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gleichungssystem (${eq.size} Unbek.)", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = palette.cursorAccent)
                    Text("Lösen [=] ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.triggerEquationSolve() })
                }
                Spacer(Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    eq.linearCoeffs.forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEachIndexed { c, value ->
                                val sel = r == eq.selectedRow && c == eq.selectedCol
                                val txt = if (sel && eq.cellBuffer.isNotEmpty()) eq.cellBuffer else value
                                val suffix = if (c < eq.size) vars[c] else ""
                                val prefix = if (c == eq.size) "=" else ""
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (sel) palette.displayHighlightBg else palette.displaySlotBg.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                                        .border(if (sel) 1.dp else 0.5.dp, if (sel) palette.cursorAccent else palette.displayBorder, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 3.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text("$prefix$txt$suffix", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.displayPrimaryText, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            EquationSubView.INPUT_POLY -> {
                val labels = when (eq.size) {
                    2 -> listOf("a (x²)", "b (x)", "c")
                    3 -> listOf("a (x³)", "b (x²)", "c (x)", "d")
                    else -> listOf("a (x⁴)", "b (x³)", "c (x²)", "d (x)", "e")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Polynom Grad ${eq.size} = 0", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                    Text("Lösen [=] ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.triggerEquationSolve() })
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    eq.polyCoeffs.forEachIndexed { idx, v ->
                        val sel = idx == eq.selectedCol
                        val txt = if (sel && eq.cellBuffer.isNotEmpty()) eq.cellBuffer else v
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (sel) palette.displayHighlightBg else palette.displaySlotBg.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .border(if (sel) 1.dp else 0.5.dp, if (sel) palette.cursorAccent else palette.displayBorder, RoundedCornerShape(4.dp))
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Text(labels.getOrElse(idx) { "" }, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = palette.displaySecondaryText)
                            Text(txt, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.displayPrimaryText)
                        }
                    }
                }
            }
            EquationSubView.RESULT -> {
                val fmt = { d: Double -> SymbolicFormatter.formatGermanNumber(d, state.formatConfig) }
                Text("Lösung ([AC] zurück)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(3.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!eq.isPolynomial) {
                        when (val sol = eq.linearResult) {
                            is LinearSystemSolution.Unique -> {
                                val names = listOf("x", "y", "z", "t")
                                itemsIndexed(sol.values) { i, v ->
                                    Text("${names[i]} = ${fmt(v)}", fontFamily = JetBrainsMonoFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.displayPrimaryText)
                                }
                            }
                            LinearSystemSolution.Infinite -> item { Text("Unendlich viele Lösungen", fontFamily = JetBrainsMonoFamily, fontSize = 14.sp, color = palette.shiftGold) }
                            LinearSystemSolution.None, null -> item { Text("Keine Lösung", fontFamily = JetBrainsMonoFamily, fontSize = 14.sp, color = palette.alphaRed) }
                        }
                    } else {
                        val sol = eq.polyResult
                        if (sol != null) {
                            itemsIndexed(sol.roots) { i, r ->
                                val text = if (r.isReal) {
                                    "x${i + 1} = ${fmt(r.real)}"
                                } else {
                                    val sign = if (r.imag < 0) "−" else "+"
                                    "x${i + 1} = ${fmt(r.real)} $sign ${fmt(kotlin.math.abs(r.imag))}i"
                                }
                                Text(text, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.displayPrimaryText)
                            }
                            if (sol.vertexX != null && sol.vertexY != null) {
                                item {
                                    val kind = if (sol.isMinimum == true) "Minimum" else "Maximum"
                                    Text(
                                        "$kind: x=${fmt(sol.vertexX)}, y=${fmt(sol.vertexY)}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.5.sp,
                                        color = palette.shiftGold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InequalityDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val iq = state.ineqState
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (iq.subView) {
            InequalitySubView.SELECT_DEGREE -> {
                Text("Ungleichungen — Polynomgrad (2–4)", fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4).forEach { deg ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(palette.displaySlotBg)
                                .border(1.dp, palette.cursorAccent, RoundedCornerShape(5.dp))
                                .clickable { vm.selectInequalityDegree(deg) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Grad $deg", fontFamily = JetBrainsMonoFamily, fontSize = 13.sp, color = palette.displayPrimaryText)
                        }
                    }
                }
            }
            InequalitySubView.SELECT_OP -> {
                Text("Ungleichheitszeichen wählen (1–4)", fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(4.dp))
                EquationInequalityEngine.InequalityOp.entries.forEachIndexed { idx, op ->
                    LcdSelectableRow("${idx + 1}: P(x) ${op.symbol}", "") { vm.selectInequalityOp(op) }
                    Spacer(Modifier.height(2.dp))
                }
            }
            InequalitySubView.INPUT_COEFFS -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Koeffizienten für P(x) ${iq.op.symbol}", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                    Text("Lösen [=] ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.triggerInequalitySolve() })
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    iq.coeffs.forEachIndexed { idx, v ->
                        val sel = idx == iq.selectedCoeff
                        val txt = if (sel && iq.cellBuffer.isNotEmpty()) iq.cellBuffer else v
                        val power = iq.degree - idx
                        val lbl = when (power) { 0 -> "c"; 1 -> "x"; else -> "x^$power" }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (sel) palette.displayHighlightBg else palette.displaySlotBg.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .border(if (sel) 1.dp else 0.5.dp, if (sel) palette.cursorAccent else palette.displayBorder, RoundedCornerShape(4.dp))
                                .padding(vertical = 4.dp)
                        ) {
                            Text(lbl, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = palette.displaySecondaryText)
                            Text(txt, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.displayPrimaryText)
                        }
                    }
                }
            }
            InequalitySubView.RESULT -> {
                Text("Lösungsintervall ([AC] zurück)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(10.dp))
                Text(iq.resultText, fontFamily = JetBrainsMonoFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.displayPrimaryText)
            }
        }
    }
}

@Composable
private fun RatioDisplay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val rs = state.ratioState
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        when (rs.subView) {
            RatioSubView.SELECT_FORM -> {
                Text("Verhältnisse (Proportionalität)", fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
                Spacer(Modifier.height(6.dp))
                LcdSelectableRow("1: a : b = X : d", "▸") { vm.selectRatioForm(true) }
                Spacer(Modifier.height(4.dp))
                LcdSelectableRow("2: a : b = c : X", "▸") { vm.selectRatioForm(false) }
            }
            RatioSubView.INPUT -> {
                val formTitle = if (rs.solveFirstRight) "a : b = X : d" else "a : b = c : X"
                val thirdLabel = if (rs.solveFirstRight) "d" else "c"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formTitle, fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp, color = palette.cursorAccent)
                    Text("Berechnen [=] ▸", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.shiftGold, modifier = Modifier.clickable { vm.triggerRatioSolve() })
                }
                Spacer(Modifier.height(6.dp))
                val fields = listOf("a" to rs.aText, "b" to rs.bText, thirdLabel to rs.knownText)
                fields.forEachIndexed { idx, (lbl, v) ->
                    val sel = idx == rs.activeField
                    val shown = if (sel && rs.editBuffer.isNotEmpty()) rs.editBuffer else v
                    LcdSelectableRow("$lbl =", shown, isSelected = sel) {}
                    Spacer(Modifier.height(2.dp))
                }
            }
            RatioSubView.RESULT -> {
                Text("Verhältnis-Ergebnis (S⇔D für Dezimal)", fontFamily = JetBrainsMonoFamily, fontSize = 10.5.sp, color = palette.cursorAccent)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("X =", fontFamily = JetBrainsMonoFamily, fontSize = 18.sp, color = palette.shiftGold)
                    rs.result?.let { res ->
                        val ast = SymbolicFormatter.toDisplayAst(res, rs.forceDecimal, state.formatConfig, state.angleUnit)
                        NaturalMathSequenceView(ast, cursor = null, baseFontSize = 22.sp)
                    }
                }
            }
        }
    }
}
