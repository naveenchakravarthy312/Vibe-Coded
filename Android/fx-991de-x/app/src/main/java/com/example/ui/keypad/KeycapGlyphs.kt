package com.example.ui.keypad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.viewmodel.KeyId

private val TightTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/**
 * Renders an authentic CASIO fx-991DE X ClassWiz mathematical keycap face
 * (stacked fractions, Canvas radicals with solid/hollow boxes, integral with bounds,
 * log with subscript base box, true superscripts x², x^□, x⁻¹, ×10^x, etc.).
 */
@Composable
fun KeycapFace(
    keyId: KeyId,
    fallbackText: String,
    color: Color,
    isNumeric: Boolean,
    isActionBlue: Boolean,
    isPill: Boolean
) {
    when (keyId) {
        KeyId.FRAC -> StackedFractionGlyph(color = color, boxSize = 6.dp, lineW = 14.dp, filledTop = true)
        KeyId.SQRT -> RadicalGlyph(color = color, indexText = null, boxSize = 7.dp, filled = true)
        KeyId.X_SQ -> BaseSuperscriptGlyph(base = "x", sup = "2", color = color, baseSize = 14.sp, supSize = 9.5.sp, italicBase = true)
        KeyId.X_POW -> BaseBoxSuperscriptGlyph(base = "x", color = color, baseSize = 14.sp, boxSize = 5.5.dp)
        KeyId.LOG_BASE -> LogBaseKeycapGlyph(color = color)
        KeyId.INTEGRAL -> IntegralKeycapGlyph(color = color)
        KeyId.X_VAR -> Text(
            text = "x",
            style = TightTextStyle,
            fontFamily = JetBrainsMonoFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = color
        )
        KeyId.RECIPROCAL -> BaseSuperscriptGlyph(base = "x", sup = "−1", color = color, baseSize = 14.sp, supSize = 8.5.sp, italicBase = true)
        KeyId.DMS -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            Text("°", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            Text("'", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
            Text("″", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        KeyId.SD -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("S", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color)
            Text("⇔", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 0.5.dp))
            Text("D", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color)
        }
        KeyId.EXP10 -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "×10",
                style = TightTextStyle,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "x",
                style = TightTextStyle,
                fontFamily = JetBrainsMonoFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
        else -> {
            val isOperator = keyId in setOf(KeyId.PLUS, KeyId.MINUS, KeyId.MUL, KeyId.DIV)
            val fontSize = when {
                isNumeric && fallbackText.length == 1 -> 17.sp
                isNumeric -> 13.5.sp
                isOperator -> 18.sp
                isActionBlue -> 13.sp
                isPill -> 9.5.sp
                fallbackText.length >= 4 -> 10.5.sp
                else -> 12.5.sp
            }
            Text(
                text = fallbackText,
                style = TightTextStyle,
                fontFamily = if (isNumeric || isOperator) JetBrainsMonoFamily else InterFontFamily,
                fontSize = fontSize,
                fontWeight = if (isNumeric || isActionBlue || isOperator) FontWeight.Bold else FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Renders authentic gold SHIFT secondary legends above each ClassWiz keycap
 * (including mathematical textbook icons like d/dx, mixed fraction, ³√□, ⁿ√□, 10^■, e^■, a b/c ⇔ d/c).
 */
@Composable
fun ShiftLegendGlyph(
    keyId: KeyId,
    fallbackText: String,
    color: Color
) {
    when (keyId) {
        KeyId.INTEGRAL -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("d", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = color)
            Text("/", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 7.5.sp, color = color)
            Text("dx", style = TightTextStyle, fontFamily = InterFontFamily, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = color)
        }
        KeyId.FRAC -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            OutlineBox(size = 4.5.dp, color = color, filled = false)
            StackedFractionGlyph(color = color, boxSize = 4.dp, lineW = 8.dp, filledTop = false)
        }
        KeyId.SQRT -> RadicalGlyph(color = color, indexText = "3", boxSize = 4.5.dp, filled = false, compact = true)
        KeyId.X_SQ -> BaseSuperscriptGlyph(base = "x", sup = "3", color = color, baseSize = 8.5.sp, supSize = 6.5.sp, italicBase = true)
        KeyId.X_POW -> RadicalGlyph(color = color, indexText = "□", boxSize = 4.5.dp, filled = false, compact = true)
        KeyId.LOG_BASE -> BaseBoxSuperscriptGlyph(base = "10", color = color, baseSize = 8.sp, boxSize = 3.8.dp, filled = true)
        KeyId.LN -> BaseBoxSuperscriptGlyph(base = "e", color = color, baseSize = 8.5.sp, boxSize = 3.8.dp, filled = true)
        KeyId.SIN -> BaseSuperscriptGlyph(base = "sin", sup = "−1", color = color, baseSize = 8.sp, supSize = 6.sp, italicBase = false)
        KeyId.COS -> BaseSuperscriptGlyph(base = "cos", sup = "−1", color = color, baseSize = 8.sp, supSize = 6.sp, italicBase = false)
        KeyId.TAN -> BaseSuperscriptGlyph(base = "tan", sup = "−1", color = color, baseSize = 8.sp, supSize = 6.sp, italicBase = false)
        KeyId.SD -> Text(
            text = "aᵇ/c⇔ᵈ/c",
            style = TightTextStyle,
            fontFamily = InterFontFamily,
            fontSize = 7.2.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
        else -> {
            if (fallbackText.isNotEmpty()) {
                Text(
                    text = fallbackText,
                    style = TightTextStyle,
                    fontFamily = InterFontFamily,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun OutlineBox(size: Dp, color: Color, filled: Boolean) {
    Box(
        modifier = Modifier
            .size(size)
            .background(if (filled) color else Color.Transparent, RoundedCornerShape(1.dp))
            .border(if (filled) 0.dp else 1.dp, color, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun StackedFractionGlyph(
    color: Color,
    boxSize: Dp,
    lineW: Dp,
    filledTop: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        OutlineBox(size = boxSize, color = color, filled = filledTop)
        Box(modifier = Modifier.width(lineW).height(1.2.dp).background(color))
        OutlineBox(size = boxSize, color = color, filled = false)
    }
}

@Composable
private fun RadicalGlyph(
    color: Color,
    indexText: String?,
    boxSize: Dp,
    filled: Boolean,
    compact: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (indexText != null) {
            if (indexText == "□") {
                Box(modifier = Modifier.padding(bottom = 5.dp, end = 0.5.dp)) {
                    OutlineBox(size = 3.5.dp, color = color, filled = false)
                }
            } else {
                Text(
                    text = indexText,
                    style = TightTextStyle,
                    fontFamily = InterFontFamily,
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        val radH = if (compact) 9.dp else 14.dp
        val radW = if (compact) 5.5.dp else 8.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(width = radW, height = radH)) {
                val w = size.width
                val h = size.height
                val p = Path().apply {
                    moveTo(0f, h * 0.58f)
                    lineTo(w * 0.30f, h * 0.50f)
                    lineTo(w * 0.55f, h * 0.92f)
                    lineTo(w, 1.dp.toPx())
                }
                drawPath(
                    path = p,
                    color = color,
                    style = Stroke(width = if (compact) 1.dp.toPx() else 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.width(boxSize + 3.dp).height(if (compact) 1.dp else 1.3.dp).background(color))
                Spacer(Modifier.height(if (compact) 1.dp else 2.dp))
                OutlineBox(size = boxSize, color = color, filled = filled)
            }
        }
    }
}

@Composable
private fun BaseSuperscriptGlyph(
    base: String,
    sup: String,
    color: Color,
    baseSize: TextUnit,
    supSize: TextUnit,
    italicBase: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = base,
            style = TightTextStyle,
            fontFamily = if (italicBase) JetBrainsMonoFamily else InterFontFamily,
            fontStyle = if (italicBase) FontStyle.Italic else FontStyle.Normal,
            fontSize = baseSize,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = sup,
            style = TightTextStyle,
            fontFamily = InterFontFamily,
            fontSize = supSize,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(bottom = (baseSize.value * 0.42f).dp)
        )
    }
}

@Composable
private fun BaseBoxSuperscriptGlyph(
    base: String,
    color: Color,
    baseSize: TextUnit,
    boxSize: Dp,
    filled: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = base,
            style = TightTextStyle,
            fontFamily = JetBrainsMonoFamily,
            fontStyle = if (base == "x" || base == "e") FontStyle.Italic else FontStyle.Normal,
            fontSize = baseSize,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Box(modifier = Modifier.padding(bottom = (baseSize.value * 0.45f).dp, start = 1.dp)) {
            OutlineBox(size = boxSize, color = color, filled = filled)
        }
    }
}

@Composable
private fun LogBaseKeycapGlyph(color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "log",
            style = TightTextStyle,
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Box(modifier = Modifier.padding(top = 6.dp, start = 0.5.dp, end = 1.dp)) {
            OutlineBox(size = 4.5.dp, color = color, filled = false)
        }
        OutlineBox(size = 6.dp, color = color, filled = true)
    }
}

@Composable
private fun IntegralKeycapGlyph(color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "∫",
            style = TightTextStyle,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = color
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(end = 1.5.dp)
        ) {
            OutlineBox(size = 4.dp, color = color, filled = false)
            OutlineBox(size = 4.dp, color = color, filled = false)
        }
        OutlineBox(size = 6.dp, color = color, filled = true)
    }
}
