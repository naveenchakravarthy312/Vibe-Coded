package com.example.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CalculatorThemeMode
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalCalculatorPalette
import com.example.viewmodel.*
import com.naveen.fx991dex.engine.*

@Composable
fun LcdOverlayContent(
    state: CalculatorUiState,
    vm: CalculatorViewModel
) {
    when (val ov = state.overlay) {
        is ActiveOverlay.MenuGrid -> MenuGridOverlay(ov, vm)
        is ActiveOverlay.SetupMenu -> SetupOverlay(ov, state, vm)
        is ActiveOverlay.OptnMenu -> OptnOverlay(ov, vm)
        is ActiveOverlay.ConstMenu -> ConstOverlay(ov, vm)
        is ActiveOverlay.ConvMenu -> ConvOverlay(ov, vm)
        is ActiveOverlay.RecallMemory -> RecallOverlay(state, vm)
        is ActiveOverlay.ResetConfirm -> ResetOverlay(vm)
        is ActiveOverlay.CalcPrompt -> CalcPromptOverlay(ov)
        is ActiveOverlay.SolvePrompt -> SolvePromptOverlay(ov)
        is ActiveOverlay.QrSummary -> QrSummaryOverlay(ov, vm)
        ActiveOverlay.None -> {}
    }
}

private fun modeIcon(mode: CalcMode): ImageVector = when (mode) {
    CalcMode.CALCULATE -> Icons.Outlined.Calculate
    CalcMode.COMPLEX -> Icons.Outlined.AutoAwesome
    CalcMode.BASE_N -> Icons.Outlined.Memory
    CalcMode.MATRIX -> Icons.Outlined.GridOn
    CalcMode.VECTOR -> Icons.Outlined.NorthEast
    CalcMode.STATISTICS -> Icons.Outlined.BarChart
    CalcMode.DISTRIBUTION -> Icons.Outlined.ShowChart
    CalcMode.SPREADSHEET -> Icons.Outlined.TableChart
    CalcMode.TABLE -> Icons.Outlined.ViewList
    CalcMode.EQUATION -> Icons.Outlined.Functions
    CalcMode.INEQUALITY -> Icons.Outlined.CompareArrows
    CalcMode.VERIFY -> Icons.Outlined.CheckCircle
    CalcMode.RATIO -> Icons.Outlined.Balance
}

@Composable
private fun MenuGridOverlay(ov: ActiveOverlay.MenuGrid, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val modes = CalcMode.entries
    val selected = modes.getOrElse(ov.selectedIndex) { CalcMode.CALCULATE }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selected.keyCode}: ${selected.germanTitle}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.displayPrimaryText
            )
            Text(
                text = "1–9, A–D / [AC]",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.displaySecondaryText
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(modes) { index, mode ->
                val isSelected = index == ov.selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isSelected) palette.cursorAccent.copy(alpha = 0.22f) else palette.displaySlotBg.copy(alpha = 0.45f))
                        .border(
                            width = if (isSelected) 1.2.dp else 0.6.dp,
                            color = if (isSelected) palette.cursorAccent else palette.displayBorder,
                            shape = RoundedCornerShape(5.dp)
                        )
                        .clickable { vm.selectMode(mode) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("menu_mode_${mode.keyCode}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = mode.keyCode,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) palette.cursorAccent else palette.shiftGold
                        )
                        Icon(
                            imageVector = modeIcon(mode),
                            contentDescription = mode.germanTitle,
                            tint = palette.displayPrimaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = mode.germanTitle,
                        fontFamily = InterFontFamily,
                        fontSize = 8.5.sp,
                        color = palette.displayPrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupOverlay(ov: ActiveOverlay.SetupMenu, state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        val header = when (ov.subPage) {
            SetupSubPage.ROOT -> "SETUP — Einstellungen"
            SetupSubPage.THEME -> "1: Design / Display-Thema"
            SetupSubPage.ANGLE_UNIT -> "2: Winkeleinheit"
            SetupSubPage.NUMBER_FORMAT -> "3: Zahlenformat"
            SetupSubPage.FRACTION_FORMAT -> "4: Bruchergebnis"
            SetupSubPage.COMPLEX_FORMAT -> "5: Komplexe Zahlen"
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(header, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
            Text(
                text = if (ov.subPage == SetupSubPage.ROOT) "[AC] Schließen" else "◄ Zurück",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.displaySecondaryText,
                modifier = Modifier.clickable {
                    if (ov.subPage == SetupSubPage.ROOT) vm.dismissOverlay()
                    else vm.updateSetupSubPage(SetupSubPage.ROOT)
                }
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            when (ov.subPage) {
                SetupSubPage.ROOT -> {
                    val rows = listOf(
                        "1: Thema" to state.themeMode.germanName,
                        "2: Winkeleinheit" to state.angleUnit.germanName,
                        "3: Zahlenformat" to state.formatConfig.numberFormat.germanName,
                        "4: Bruchergebnis" to state.formatConfig.fractionFormat.germanName,
                        "5: Komplexe Zahlen" to state.formatConfig.complexFormat.germanName
                    )
                    itemsIndexed(rows) { idx, (label, curVal) ->
                        LcdSelectableRow(
                            left = label,
                            right = curVal,
                            testTag = "setup_root_${idx + 1}"
                        ) {
                            when (idx) {
                                0 -> vm.updateSetupSubPage(SetupSubPage.THEME)
                                1 -> vm.updateSetupSubPage(SetupSubPage.ANGLE_UNIT)
                                2 -> vm.updateSetupSubPage(SetupSubPage.NUMBER_FORMAT)
                                3 -> vm.updateSetupSubPage(SetupSubPage.FRACTION_FORMAT)
                                4 -> vm.updateSetupSubPage(SetupSubPage.COMPLEX_FORMAT)
                            }
                        }
                    }
                }
                SetupSubPage.THEME -> {
                    itemsIndexed(CalculatorThemeMode.entries) { idx, mode ->
                        LcdSelectableRow(
                            left = "${idx + 1}: ${mode.germanName}",
                            right = if (state.themeMode == mode) "✓" else "",
                            isSelected = state.themeMode == mode,
                            testTag = "setup_theme_${mode.name}"
                        ) { vm.setThemeMode(mode) }
                    }
                }
                SetupSubPage.ANGLE_UNIT -> {
                    itemsIndexed(AngleUnit.entries) { idx, unit ->
                        LcdSelectableRow(
                            left = "${idx + 1}: ${unit.germanName}",
                            right = if (state.angleUnit == unit) "✓" else "[${unit.badge}]",
                            isSelected = state.angleUnit == unit,
                            testTag = "setup_angle_${unit.name}"
                        ) { vm.setAngleUnit(unit) }
                    }
                }
                SetupSubPage.NUMBER_FORMAT -> {
                    val opts = listOf(
                        Triple("1: Fix (4 Stellen)", NumberFormatMode.FIX, 4),
                        Triple("2: Sci (4 Stellen)", NumberFormatMode.SCI, 4),
                        Triple("3: Norm 1", NumberFormatMode.NORM1, 10),
                        Triple("4: Norm 2", NumberFormatMode.NORM2, 10)
                    )
                    itemsIndexed(opts) { _, (label, mode, digits) ->
                        LcdSelectableRow(
                            left = label,
                            right = if (state.formatConfig.numberFormat == mode) "✓" else "",
                            isSelected = state.formatConfig.numberFormat == mode,
                            testTag = "setup_fmt_${mode.name}"
                        ) { vm.setNumberFormat(mode, digits) }
                    }
                }
                SetupSubPage.FRACTION_FORMAT -> {
                    itemsIndexed(FractionFormat.entries) { idx, fmt ->
                        LcdSelectableRow(
                            left = "${idx + 1}: ${fmt.germanName}",
                            right = if (state.formatConfig.fractionFormat == fmt) "✓" else "",
                            isSelected = state.formatConfig.fractionFormat == fmt
                        ) { vm.setFractionFormat(fmt) }
                    }
                }
                SetupSubPage.COMPLEX_FORMAT -> {
                    itemsIndexed(ComplexFormat.entries) { idx, fmt ->
                        LcdSelectableRow(
                            left = "${idx + 1}: ${fmt.germanName}",
                            right = if (state.formatConfig.complexFormat == fmt) "✓" else "",
                            isSelected = state.formatConfig.complexFormat == fmt
                        ) { vm.setComplexFormat(fmt) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptnOverlay(ov: ActiveOverlay.OptnMenu, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            text = ov.title,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = palette.cursorAccent,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            itemsIndexed(ov.items) { idx, item ->
                LcdSelectableRow(
                    left = item.label,
                    right = "",
                    isSelected = idx == ov.selectedIndex,
                    testTag = "optn_${item.id}"
                ) { vm.executeOptnItem(item.id) }
            }
        }
    }
}

@Composable
private fun ConstOverlay(ov: ActiveOverlay.ConstMenu, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = if (ov.categoryIndex == null) "Wiss. Konstanten (CONST)" else ScientificCatalog.constantCategories[ov.categoryIndex],
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.shiftGold
            )
            if (ov.categoryIndex != null) {
                Text(
                    text = "◄ Kategorien",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = palette.displaySecondaryText,
                    modifier = Modifier.clickable { vm.updateConstCategory(null) }
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (ov.categoryIndex == null) {
                itemsIndexed(ScientificCatalog.constantCategories) { idx, cat ->
                    LcdSelectableRow(left = cat, right = "▸") { vm.updateConstCategory(idx) }
                }
            } else {
                val catName = ScientificCatalog.constantCategories[ov.categoryIndex]
                val list = ScientificCatalog.constants.filter { it.category == catName }
                itemsIndexed(list) { idx, c ->
                    LcdSelectableRow(
                        left = "${idx + 1}: ${c.symbol}  ${c.nameDe}",
                        right = c.unit
                    ) { vm.selectConstant(c) }
                }
            }
        }
    }
}

@Composable
private fun ConvOverlay(ov: ActiveOverlay.ConvMenu, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = if (ov.categoryIndex == null) "Einheitenumrechn. (CONV)" else ScientificCatalog.conversionCategories[ov.categoryIndex],
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.shiftGold
            )
            if (ov.categoryIndex != null) {
                Text(
                    text = "◄ Kategorien",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = palette.displaySecondaryText,
                    modifier = Modifier.clickable { vm.updateConvCategory(null) }
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (ov.categoryIndex == null) {
                itemsIndexed(ScientificCatalog.conversionCategories) { idx, cat ->
                    LcdSelectableRow(left = cat, right = "▸") { vm.updateConvCategory(idx) }
                }
            } else {
                val catName = ScientificCatalog.conversionCategories[ov.categoryIndex]
                val list = ScientificCatalog.conversions.filter { it.category == catName }
                itemsIndexed(list) { idx, conv ->
                    LcdSelectableRow(left = "${idx + 1}: ${conv.label}", right = "") { vm.selectConversion(conv) }
                }
            }
        }
    }
}

@Composable
private fun RecallOverlay(state: CalculatorUiState, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    val ordered = listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RECALL — Variablenspeicher", fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
            Text("[AC] Zurück", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = palette.displaySecondaryText, modifier = Modifier.clickable { vm.dismissOverlay() })
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(ordered) { _, key ->
                val v = state.variables[key] ?: 0.0
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.displaySlotBg.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$key=", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.alphaRed)
                    Text(
                        SymbolicFormatter.formatGermanNumber(v, state.formatConfig),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = palette.displayPrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Ans=${SymbolicFormatter.toDecimalText(state.ans, state.formatConfig, state.angleUnit)}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.displaySecondaryText
            )
            Text(
                "PreAns=${SymbolicFormatter.toDecimalText(state.preAns, state.formatConfig, state.angleUnit)}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.displaySecondaryText
            )
        }
    }
}

@Composable
private fun ResetOverlay(vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("RESET — Zurücksetzen", fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.alphaRed)
        LcdSelectableRow("1: Einstellungen zurücksetzen", "") { vm.performReset(0) }
        LcdSelectableRow("2: Variablenspeicher löschen", "") { vm.performReset(1) }
        LcdSelectableRow("3: Alles initialisieren", "") { vm.performReset(2) }
    }
}

@Composable
private fun CalcPromptOverlay(ov: ActiveOverlay.CalcPrompt) {
    val palette = LocalCalculatorPalette.current
    val vName = ov.variables.getOrElse(ov.currentIndex) { "X" }
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("CALC — Wert eingeben (${ov.currentIndex + 1}/${ov.variables.size})", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.cursorAccent)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$vName ?", fontFamily = JetBrainsMonoFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.alphaRed)
            Text(
                text = ov.inputBuffer.ifEmpty { "0" },
                fontFamily = JetBrainsMonoFamily,
                fontSize = 22.sp,
                color = palette.displayPrimaryText
            )
        }
        Text("Drücke [=] zum Bestätigen · [AC] Abbrechen", fontFamily = InterFontFamily, fontSize = 10.sp, color = palette.displaySecondaryText)
    }
}

@Composable
private fun SolvePromptOverlay(ov: ActiveOverlay.SolvePrompt) {
    val palette = LocalCalculatorPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("SOLVE — Newton-Raphson Gleichungslöser", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.shiftGold)
        if (ov.resultText != null) {
            Text(
                text = ov.resultText,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.displayPrimaryText
            )
            Text("Drücke [=] oder [AC] zum Fortfahren", fontFamily = InterFontFamily, fontSize = 10.sp, color = palette.displaySecondaryText)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Startwert X:", fontFamily = JetBrainsMonoFamily, fontSize = 16.sp, color = palette.displaySecondaryText)
                Text(ov.inputBuffer.ifEmpty { "1" }, fontFamily = JetBrainsMonoFamily, fontSize = 22.sp, color = palette.displayPrimaryText)
            }
            Text("Drücke [=] zum Lösen · Gleichung mit ALPHA+CALC (=)", fontFamily = InterFontFamily, fontSize = 10.sp, color = palette.displaySecondaryText)
        }
    }
}

@Composable
private fun QrSummaryOverlay(ov: ActiveOverlay.QrSummary, vm: CalculatorViewModel) {
    val palette = LocalCalculatorPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp).clickable { vm.dismissOverlay() },
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Offline ClassWiz Status (SHIFT+OPTN)", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = palette.shiftGold)
        Text(ov.expressionText, fontFamily = JetBrainsMonoFamily, fontSize = 13.sp, color = palette.displayPrimaryText)
        Text("Ergebnis: ${ov.resultText}", fontFamily = JetBrainsMonoFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.cursorAccent)
        Text("100% Offline-Engine aktiv · [AC] Schließen", fontFamily = InterFontFamily, fontSize = 10.sp, color = palette.displaySecondaryText)
    }
}

@Composable
fun LcdSelectableRow(
    left: String,
    right: String,
    isSelected: Boolean = false,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val palette = LocalCalculatorPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) palette.displayHighlightBg else palette.displaySlotBg.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = left,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 12.sp,
            color = palette.displayPrimaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (right.isNotEmpty()) {
            Text(
                text = right,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.cursorAccent
            )
        }
    }
}
