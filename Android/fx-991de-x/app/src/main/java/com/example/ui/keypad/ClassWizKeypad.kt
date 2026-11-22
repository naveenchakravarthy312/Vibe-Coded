package com.example.ui.keypad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalCalculatorPalette
import com.example.viewmodel.KeyId

private enum class KeyStyle {
    SCIENTIFIC, NUMERIC, ACTION_BLUE, SHIFT_PILL, ALPHA_PILL, MENU_PILL, ON_PILL
}

private data class KeySpec(
    val id: KeyId,
    val primary: String,
    val shiftLabel: String = "",
    val alphaLabel: String = "",
    val style: KeyStyle = KeyStyle.SCIENTIFIC,
    val testTag: String = "key_${id.name.lowercase()}"
)

private val CaptionStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeight = 10.sp
)

/**
 * Exact 50-key German CASIO fx-991DE X ClassWiz keypad layout with authentic compact
 * keycap proportions, textbook mathematical key faces, and crisp unclipped SHIFT/ALPHA legends.
 */
@Composable
fun ClassWizKeypad(
    shiftActive: Boolean,
    alphaActive: Boolean,
    onKeyPress: (KeyId) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val triggerKey: (KeyId) -> Unit = { key ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKeyPress(key)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- Control Cluster + Row 1 flanking the sculpted central 4-way D-Pad ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2.0f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left 2 columns: [SHIFT, ALPHA] over [OPTN, CALC]
            Column(
                modifier = Modifier.weight(2.05f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(0.95f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcKeyButton(
                        spec = KeySpec(KeyId.SHIFT, "SHIFT", style = KeyStyle.SHIFT_PILL),
                        isHighlighted = shiftActive,
                        onPress = triggerKey,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    CalcKeyButton(
                        spec = KeySpec(KeyId.ALPHA, "ALPHA", style = KeyStyle.ALPHA_PILL),
                        isHighlighted = alphaActive,
                        onPress = triggerKey,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1.05f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcKeyButton(
                        spec = KeySpec(KeyId.OPTN, "OPTN", shiftLabel = "QR"),
                        onPress = triggerKey,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    CalcKeyButton(
                        spec = KeySpec(KeyId.CALC, "CALC", shiftLabel = "SOLVE", alphaLabel = "="),
                        onPress = triggerKey,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // Central sculpted 4-way D-Pad (circular, proportional)
            Box(
                modifier = Modifier
                    .weight(1.75f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ClassWizDPad(
                    onKeyPress = triggerKey,
                    modifier = Modifier
                        .fillMaxHeight(0.92f)
                        .aspectRatio(1f)
                )
            }

            // Right 2 columns: [MENU/SETUP, ON] over [∫dx, x]
            Column(
                modifier = Modifier.weight(2.05f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(0.95f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcKeyButton(
                        spec = KeySpec(KeyId.MENU_SETUP, "MENU", shiftLabel = "SETUP", style = KeyStyle.MENU_PILL),
                        onPress = triggerKey,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    CalcKeyButton(
                        spec = KeySpec(KeyId.ON, "ON", style = KeyStyle.ON_PILL),
                        onPress = triggerKey,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1.05f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcKeyButton(
                        spec = KeySpec(KeyId.INTEGRAL, "∫□", shiftLabel = "d/dx"),
                        onPress = triggerKey,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    CalcKeyButton(
                        spec = KeySpec(KeyId.X_VAR, "x", shiftLabel = "Σ", alphaLabel = "Π"),
                        onPress = triggerKey,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }

        // --- Row 2 (6 scientific keys) ---
        KeyRow(
            keys = listOf(
                KeySpec(KeyId.FRAC, "□/□", shiftLabel = "a b/c"),
                KeySpec(KeyId.SQRT, "√□", shiftLabel = "³√□"),
                KeySpec(KeyId.X_SQ, "x²", shiftLabel = "x³", alphaLabel = "DEC"),
                KeySpec(KeyId.X_POW, "x^□", shiftLabel = "ⁿ√□", alphaLabel = "HEX"),
                KeySpec(KeyId.LOG_BASE, "log□", shiftLabel = "10^x", alphaLabel = "BIN"),
                KeySpec(KeyId.LN, "ln", shiftLabel = "e^x", alphaLabel = "OCT")
            ),
            spacing = 5.dp,
            onPress = triggerKey,
            modifier = Modifier.weight(1.0f)
        )

        // --- Row 3 (6 scientific keys) ---
        KeyRow(
            keys = listOf(
                KeySpec(KeyId.NEGATE, "(-)", shiftLabel = "∠", alphaLabel = "A"),
                KeySpec(KeyId.DMS, "° ' ″", shiftLabel = "FACT", alphaLabel = "B"),
                KeySpec(KeyId.RECIPROCAL, "x⁻¹", shiftLabel = "x!", alphaLabel = "C"),
                KeySpec(KeyId.SIN, "sin", shiftLabel = "sin⁻¹", alphaLabel = "D"),
                KeySpec(KeyId.COS, "cos", shiftLabel = "cos⁻¹", alphaLabel = "E"),
                KeySpec(KeyId.TAN, "tan", shiftLabel = "tan⁻¹", alphaLabel = "F")
            ),
            spacing = 5.dp,
            onPress = triggerKey,
            modifier = Modifier.weight(1.0f)
        )

        // --- Row 4 (6 scientific keys) ---
        KeyRow(
            keys = listOf(
                KeySpec(KeyId.STO, "STO", shiftLabel = "RECALL"),
                KeySpec(KeyId.ENG, "ENG", shiftLabel = "←", alphaLabel = "i"),
                KeySpec(KeyId.LPAREN, "(", shiftLabel = "Abs"),
                KeySpec(KeyId.RPAREN, ")", shiftLabel = ";", alphaLabel = "x"),
                KeySpec(KeyId.SD, "S⇔D", shiftLabel = "a/b⇔c", alphaLabel = "y"),
                KeySpec(KeyId.M_PLUS, "M+", shiftLabel = "M−", alphaLabel = "M")
            ),
            spacing = 5.dp,
            onPress = triggerKey,
            modifier = Modifier.weight(1.0f)
        )

        Spacer(Modifier.height(2.dp))

        // --- Row 5 (5 lower numpad/operator keys) ---
        KeyRow(
            keys = listOf(
                KeySpec(KeyId.NUM_7, "7", shiftLabel = "CONST", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.NUM_8, "8", shiftLabel = "CONV", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.NUM_9, "9", shiftLabel = "RESET", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.DEL, "DEL", shiftLabel = "INS", alphaLabel = "UNDO", style = KeyStyle.ACTION_BLUE),
                KeySpec(KeyId.AC, "AC", shiftLabel = "OFF", style = KeyStyle.ACTION_BLUE)
            ),
            spacing = 7.dp,
            onPress = triggerKey,
            modifier = Modifier.weight(1.12f)
        )

        // --- Row 6 ---
        KeyRow(
            keys = listOf(
                KeySpec(KeyId.NUM_4, "4", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.NUM_5, "5", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.NUM_6, "6", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.MUL, "×", shiftLabel = "nPr", alphaLabel = "ggT", style = KeyStyle.SCIENTIFIC),
                KeySpec(KeyId.DIV, "÷", shiftLabel = "nCr", alphaLabel = "kgV", style = KeyStyle.SCIENTIFIC)
            ),
            spacing = 7.dp,
            onPress = triggerKey,
            modifier = Modifier.weight(1.12f)
        )

        // --- Row 7 ---
        KeyRow(
            keys = listOf(
                KeySpec(KeyId.NUM_1, "1", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.NUM_2, "2", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.NUM_3, "3", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.PLUS, "+", shiftLabel = "Pol", alphaLabel = "Int", style = KeyStyle.SCIENTIFIC),
                KeySpec(KeyId.MINUS, "−", shiftLabel = "Rec", alphaLabel = "Intg", style = KeyStyle.SCIENTIFIC)
            ),
            spacing = 7.dp,
            onPress = triggerKey,
            modifier = Modifier.weight(1.12f)
        )

        // --- Row 8 ---
        KeyRow(
            keys = listOf(
                KeySpec(KeyId.NUM_0, "0", shiftLabel = "Rnd", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.DECIMAL, ",", shiftLabel = "Ran#", alphaLabel = "RanInt", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.EXP10, "×10ˣ", shiftLabel = "π", alphaLabel = "e", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.ANS, "Ans", shiftLabel = "%", alphaLabel = "PreAns", style = KeyStyle.NUMERIC),
                KeySpec(KeyId.EQUALS, "=", shiftLabel = "≈", style = KeyStyle.NUMERIC)
            ),
            spacing = 7.dp,
            onPress = triggerKey,
            modifier = Modifier.weight(1.12f)
        )
    }
}

@Composable
private fun KeyRow(
    keys: List<KeySpec>,
    spacing: androidx.compose.ui.unit.Dp,
    onPress: (KeyId) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { spec ->
            CalcKeyButton(
                spec = spec,
                onPress = onPress,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CalcKeyButton(
    spec: KeySpec,
    isHighlighted: Boolean = false,
    onPress: (KeyId) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalculatorPalette.current
    val interactionSource = remember { MutableInteractionSource() }

    val bgColor = when (spec.style) {
        KeyStyle.NUMERIC -> palette.numKeyBg
        KeyStyle.ACTION_BLUE -> palette.actionBlueBg
        KeyStyle.SHIFT_PILL -> if (isHighlighted) palette.shiftGold else palette.sciKeyBg
        KeyStyle.ALPHA_PILL -> if (isHighlighted) palette.alphaRed else palette.sciKeyBg
        KeyStyle.MENU_PILL, KeyStyle.ON_PILL -> palette.sciKeyBg
        KeyStyle.SCIENTIFIC -> palette.sciKeyBg
    }

    val fgColor = when (spec.style) {
        KeyStyle.NUMERIC -> palette.numKeyText
        KeyStyle.ACTION_BLUE -> palette.actionBlueText
        KeyStyle.SHIFT_PILL -> if (isHighlighted) Color(0xFF141414) else palette.shiftGold
        KeyStyle.ALPHA_PILL -> if (isHighlighted) Color(0xFFFFFFFF) else palette.alphaRed
        KeyStyle.MENU_PILL, KeyStyle.ON_PILL -> palette.sciKeyText
        KeyStyle.SCIENTIFIC -> palette.sciKeyText
    }

    val borderColor = when {
        isHighlighted && spec.style == KeyStyle.SHIFT_PILL -> palette.shiftGold
        isHighlighted && spec.style == KeyStyle.ALPHA_PILL -> palette.alphaRed
        spec.style == KeyStyle.NUMERIC -> Color.Transparent
        spec.style == KeyStyle.ACTION_BLUE -> Color.Transparent
        else -> palette.sciKeyBorder
    }

    val isPill = spec.style in setOf(KeyStyle.SHIFT_PILL, KeyStyle.ALPHA_PILL, KeyStyle.MENU_PILL, KeyStyle.ON_PILL)
    val keyShape = if (isPill) RoundedCornerShape(14.dp) else RoundedCornerShape(6.dp)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Generous unclipped SHIFT (gold) / ALPHA (coral-red) caption strip above each keycap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(15.dp)
                .padding(horizontal = 1.dp),
            horizontalArrangement = if (spec.shiftLabel.isNotEmpty() && spec.alphaLabel.isNotEmpty()) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.Center
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (spec.shiftLabel.isNotEmpty()) {
                ShiftLegendGlyph(
                    keyId = spec.id,
                    fallbackText = spec.shiftLabel,
                    color = palette.shiftGold
                )
            }
            if (spec.alphaLabel.isNotEmpty()) {
                val isMathVar = spec.alphaLabel in setOf("x", "y", "i", "e")
                Text(
                    text = spec.alphaLabel,
                    style = CaptionStyle,
                    fontFamily = InterFontFamily,
                    fontSize = 8.sp,
                    fontStyle = if (isMathVar) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = FontWeight.Bold,
                    color = palette.alphaRed,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Compact, well-proportioned physical keycap (fill = false enforces sleek ClassWiz height)
        val capMaxHeight = when {
            isPill -> 26.dp
            spec.style == KeyStyle.NUMERIC || spec.style == KeyStyle.ACTION_BLUE -> 36.dp
            spec.id in setOf(KeyId.PLUS, KeyId.MINUS, KeyId.MUL, KeyId.DIV) -> 36.dp
            else -> 29.dp
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .height(capMaxHeight)
                .clip(keyShape)
                .background(bgColor)
                .border(0.8.dp, borderColor, keyShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = { onPress(spec.id) }
                )
                .testTag(spec.testTag),
            contentAlignment = Alignment.Center
        ) {
            KeycapFace(
                keyId = spec.id,
                fallbackText = spec.primary,
                color = fgColor,
                isNumeric = spec.style == KeyStyle.NUMERIC,
                isActionBlue = spec.style == KeyStyle.ACTION_BLUE,
                isPill = isPill
            )
        }
    }
}

/**
 * Central 4-way sculpted ClassWiz D-Pad (▲ ▼ ◄ ►) for 2D AST cursor & history navigation.
 */
@Composable
private fun ClassWizDPad(
    onKeyPress: (KeyId) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalculatorPalette.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(palette.dpadBg)
            .border(1.4.dp, palette.dpadRing, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // UP
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.55f)
                .fillMaxHeight(0.38f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable { onKeyPress(KeyId.UP) }
                .testTag("key_up"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Nach oben",
                tint = palette.dpadArrow,
                modifier = Modifier.size(18.dp)
            )
        }
        // DOWN
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.55f)
                .fillMaxHeight(0.38f)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .clickable { onKeyPress(KeyId.DOWN) }
                .testTag("key_down"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Nach unten",
                tint = palette.dpadArrow,
                modifier = Modifier.size(18.dp)
            )
        }
        // LEFT
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.38f)
                .fillMaxHeight(0.55f)
                .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                .clickable { onKeyPress(KeyId.LEFT) }
                .testTag("key_left"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Nach links",
                tint = palette.dpadArrow,
                modifier = Modifier.size(18.dp)
            )
        }
        // RIGHT
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.38f)
                .fillMaxHeight(0.55f)
                .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                .clickable { onKeyPress(KeyId.RIGHT) }
                .testTag("key_right"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Nach rechts",
                tint = palette.dpadArrow,
                modifier = Modifier.size(18.dp)
            )
        }
        // Subtle tactile center dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(palette.canvasBackground.copy(alpha = 0.65f))
                .border(0.8.dp, palette.dpadRing, CircleShape)
        )
    }
}
