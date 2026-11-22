package com.example.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalCalculatorPalette
import com.example.viewmodel.*
import com.naveen.fx991dex.engine.NumberFormatMode
import com.naveen.fx991dex.engine.SymbolicFormatter
import com.naveen.fx991dex.engine.SymbolicValue

@Composable
fun CalculatorDisplay(
    state: CalculatorUiState,
    vm: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalculatorPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.displayBackground)
            .border(1.2.dp, palette.displayBorder, RoundedCornerShape(10.dp))
            .testTag("calculator_display")
    ) {
        // Authentic ClassWiz LCD Status Bar
        LcdStatusBar(state)
        HorizontalDivider(thickness = 0.75.dp, color = palette.displayBorder.copy(alpha = 0.7f))

        // Main LCD viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.overlay !is ActiveOverlay.None -> LcdOverlayContent(state, vm)
                SpecializedModeHandler.isSpecializedInputMode(state) -> SpecializedModeDisplay(state, vm)
                else -> NaturalAstViewport(state)
            }
        }
    }
}

@Composable
private fun LcdStatusBar(state: CalculatorUiState) {
    val palette = LocalCalculatorPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge("S", active = state.shiftActive, activeBg = palette.shiftGold, activeFg = Color(0xFF141414))
            StatusBadge("A", active = state.alphaActive, activeBg = palette.alphaRed, activeFg = Color(0xFFFFFFFF))
            StatusBadge("M", active = state.hasMemoryM, activeBg = palette.statusBadgeBg, activeFg = palette.statusBadgeText)
            if (state.stoPending) {
                StatusBadge("STO", active = true, activeBg = palette.cursorAccent, activeFg = Color(0xFF141414))
            }
            if (state.insertMode) {
                StatusBadge("INS", active = true, activeBg = palette.shiftGold, activeFg = Color(0xFF141414))
            }
            StatusBadge(state.angleUnit.badge, active = true, activeBg = palette.statusBadgeBg, activeFg = palette.statusBadgeText)

            when (state.formatConfig.numberFormat) {
                NumberFormatMode.FIX -> StatusBadge("FIX", active = true, activeBg = palette.statusBadgeBg, activeFg = palette.statusBadgeText)
                NumberFormatMode.SCI -> StatusBadge("SCI", active = true, activeBg = palette.statusBadgeBg, activeFg = palette.statusBadgeText)
                else -> {}
            }

            val modeBadge = when (state.mode) {
                CalcMode.BASE_N -> state.baseN.currentBase.label.uppercase()
                else -> state.mode.shortBadge
            }
            if (modeBadge.isNotEmpty()) {
                StatusBadge(modeBadge, active = true, activeBg = palette.cursorAccent.copy(alpha = 0.25f), activeFg = palette.displayPrimaryText)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val arrows = buildString {
                if (state.history.isNotEmpty()) append("▲")
                if (state.historyBrowseIndex != -1 && state.historyBrowseIndex < state.history.lastIndex) append("▼")
                else if (state.history.isNotEmpty()) append("▼")
            }
            Text(
                text = "Math${if (arrows.isNotEmpty()) " $arrows" else ""}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.displaySecondaryText
            )
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    active: Boolean,
    activeBg: Color,
    activeFg: Color
) {
    val palette = LocalCalculatorPalette.current
    val bg = if (active) activeBg else Color.Transparent
    val fg = if (active) activeFg else palette.displaySecondaryText.copy(alpha = 0.28f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .border(
                width = if (active) 0.dp else 0.6.dp,
                color = if (active) Color.Transparent else palette.displaySecondaryText.copy(alpha = 0.22f),
                shape = RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
private fun NaturalAstViewport(state: CalculatorUiState) {
    val palette = LocalCalculatorPalette.current
    val inputScroll = rememberScrollState()
    val outputScroll = rememberScrollState()

    LaunchedEffect(state.document) {
        inputScroll.animateScrollTo(inputScroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Optional subtle status/mode hint line (e.g., "Ans ▸ A", "M + Ans", or Verify/Matrix prompt)
        val banner = state.statusBanner ?: when (state.mode) {
            CalcMode.MATRIX -> "Matrix-Rechnung (OPTN für MatA–D, Det, Trn)"
            CalcMode.VECTOR -> "Vektor-Rechnung (OPTN für VctA–B, •, Winkel)"
            CalcMode.VERIFY -> "Berechn. prüfen (OPTN für =, ≠, <, >, ≤, ≥)"
            else -> null
        }
        if (banner != null) {
            Text(
                text = banner,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = palette.cursorAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Top Input Expression Line (Natural Textbook AST — top-left anchored like real ClassWiz)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp)
                .horizontalScroll(inputScroll),
            contentAlignment = Alignment.TopStart
        ) {
            NaturalMathSequenceView(
                sequence = state.document.root,
                cursor = state.document.cursor,
                baseFontSize = 21.sp,
                textColor = palette.displayPrimaryText,
                isRoot = true,
                modifier = Modifier.testTag("math_input_ast")
            )
        }

        // Bottom Right-Aligned Exact Symbolic / Decimal Result Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .horizontalScroll(outputScroll),
            contentAlignment = Alignment.BottomEnd
        ) {
            val result = state.currentResult
            if (result != null) {
                val outputColor = if (result is SymbolicValue.Error) palette.alphaRed else palette.displayPrimaryText
                val outputAst = SymbolicFormatter.toDisplayAst(
                    value = result,
                    forceDecimal = state.resultForceDecimal,
                    config = state.formatConfig,
                    angleUnit = state.angleUnit
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.testTag("math_output_ast")
                ) {
                    if (result.isExactSymbolic() && !state.resultForceDecimal && result !is SymbolicValue.PrimeFactorized) {
                        Text(
                            text = "EXAKT ",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.cursorAccent.copy(alpha = 0.8f),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    } else if (state.resultForceDecimal && result.isExactSymbolic()) {
                        Text(
                            text = "≈ ",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 16.sp,
                            color = palette.displaySecondaryText
                        )
                    }
                    NaturalMathSequenceView(
                        sequence = outputAst,
                        cursor = null,
                        baseFontSize = 24.sp,
                        textColor = outputColor
                    )
                }
            }
        }
    }
}
