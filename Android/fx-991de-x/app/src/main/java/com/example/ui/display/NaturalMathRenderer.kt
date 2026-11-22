package com.example.ui.display

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalCalculatorPalette
import com.naveen.fx991dex.engine.CursorLocation
import com.naveen.fx991dex.engine.MathNode
import com.naveen.fx991dex.engine.MathSequence
import com.naveen.fx991dex.engine.TokenKind

/**
 * Recursive AST-based Natural Textbook Display (Natürliche Darstellung) Composable renderer.
 */
@Composable
fun NaturalMathSequenceView(
    sequence: MathSequence,
    cursor: CursorLocation?,
    baseFontSize: TextUnit = 21.sp,
    textColor: Color = LocalCalculatorPalette.current.displayPrimaryText,
    showEmptyPlaceholder: Boolean = false,
    isRoot: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalCalculatorPalette.current
    val isCursorHere = cursor != null && cursor.sequenceId == sequence.id

    // Blinking cursor animation
    val infiniteTransition = rememberInfiniteTransition(label = "lcd_cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sequence.isEmpty) {
            if (isRoot) {
                if (isCursorHere) {
                    BlinkingCursorBar(
                        heightDp = (baseFontSize.value * 1.15f).dp,
                        color = palette.cursorAccent.copy(alpha = cursorAlpha)
                    )
                }
            } else if (showEmptyPlaceholder) {
                PlaceholderBox(
                    fontSize = baseFontSize,
                    isFocused = isCursorHere,
                    cursorAlpha = cursorAlpha
                )
            }
        } else {
            sequence.nodes.forEachIndexed { idx, node ->
                if (isCursorHere && cursor.index == idx) {
                    BlinkingCursorBar(
                        heightDp = (baseFontSize.value * 1.1f).dp,
                        color = palette.cursorAccent.copy(alpha = cursorAlpha)
                    )
                }
                NaturalMathNodeView(
                    node = node,
                    cursor = cursor,
                    baseFontSize = baseFontSize,
                    textColor = textColor
                )
            }
            if (isCursorHere && cursor.index >= sequence.nodes.size) {
                BlinkingCursorBar(
                    heightDp = (baseFontSize.value * 1.1f).dp,
                    color = palette.cursorAccent.copy(alpha = cursorAlpha)
                )
            }
        }
    }
}

@Composable
private fun BlinkingCursorBar(heightDp: androidx.compose.ui.unit.Dp, color: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = 0.5.dp)
            .width(2.2.dp)
            .height(heightDp.coerceIn(11.dp, 28.dp))
            .background(color, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun PlaceholderBox(
    fontSize: TextUnit,
    isFocused: Boolean,
    cursorAlpha: Float
) {
    val palette = LocalCalculatorPalette.current
    val boxSize = (fontSize.value * 0.85f).coerceIn(10f, 18f).dp
    Box(
        modifier = Modifier
            .padding(horizontal = 1.5.dp, vertical = 1.dp)
            .size(width = boxSize, height = boxSize * 1.1f)
            .background(
                color = if (isFocused) palette.cursorAccent.copy(alpha = 0.22f) else palette.displaySlotBg.copy(alpha = 0.6f),
                shape = RoundedCornerShape(2.dp)
            )
            .border(
                width = if (isFocused) 1.3.dp else 1.dp,
                color = if (isFocused) palette.cursorAccent.copy(alpha = cursorAlpha) else palette.displaySecondaryText.copy(alpha = 0.65f),
                shape = RoundedCornerShape(2.dp)
            )
    )
}

@Composable
private fun NaturalMathNodeView(
    node: MathNode,
    cursor: CursorLocation?,
    baseFontSize: TextUnit,
    textColor: Color
) {
    val childFont = (baseFontSize.value * 0.78f).coerceAtLeast(11f).sp
    val tinyFont = (baseFontSize.value * 0.62f).coerceAtLeast(9.5f).sp

    when (node) {
        is MathNode.TokenNode -> {
            val isItalic = node.kind == TokenKind.VARIABLE || node.text == "e" || node.text == "i"
            val horizontalPad = when (node.kind) {
                TokenKind.OPERATOR, TokenKind.COMPARE -> 2.dp
                else -> 0.dp
            }
            Text(
                text = node.text,
                fontFamily = JetBrainsMonoFamily,
                fontSize = baseFontSize,
                fontWeight = if (node.kind == TokenKind.DIGIT) FontWeight.Medium else FontWeight.Normal,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = horizontalPad)
            )
        }

        is MathNode.FractionNode -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(IntrinsicSize.Max)
            ) {
                NaturalMathSequenceView(
                    sequence = node.numerator,
                    cursor = cursor,
                    baseFontSize = childFont,
                    textColor = textColor,
                    showEmptyPlaceholder = true,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.4.dp)
                        .background(textColor)
                )
                NaturalMathSequenceView(
                    sequence = node.denominator,
                    cursor = cursor,
                    baseFontSize = childFont,
                    textColor = textColor,
                    showEmptyPlaceholder = true,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }

        is MathNode.MixedFractionNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                NaturalMathSequenceView(
                    sequence = node.whole,
                    cursor = cursor,
                    baseFontSize = baseFontSize,
                    textColor = textColor,
                    showEmptyPlaceholder = true,
                    modifier = Modifier.padding(end = 2.dp)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    NaturalMathSequenceView(
                        sequence = node.numerator,
                        cursor = cursor,
                        baseFontSize = childFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.3.dp)
                            .background(textColor)
                    )
                    NaturalMathSequenceView(
                        sequence = node.denominator,
                        cursor = cursor,
                        baseFontSize = childFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        is MathNode.RootNode -> {
            RadicalContainer(
                indexContent = null,
                strokeColor = textColor,
                radicand = {
                    NaturalMathSequenceView(
                        sequence = node.radicand,
                        cursor = cursor,
                        baseFontSize = (baseFontSize.value * 0.92f).coerceAtLeast(12f).sp,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                }
            )
        }

        is MathNode.NthRootNode -> {
            RadicalContainer(
                indexContent = {
                    NaturalMathSequenceView(
                        sequence = node.index,
                        cursor = cursor,
                        baseFontSize = tinyFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                },
                strokeColor = textColor,
                radicand = {
                    NaturalMathSequenceView(
                        sequence = node.radicand,
                        cursor = cursor,
                        baseFontSize = (baseFontSize.value * 0.90f).coerceAtLeast(12f).sp,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                }
            )
        }

        is MathNode.PowerNode -> {
            Box(
                modifier = Modifier
                    .padding(bottom = (baseFontSize.value * 0.48f).dp, start = 0.5.dp, end = 1.dp)
            ) {
                NaturalMathSequenceView(
                    sequence = node.exponent,
                    cursor = cursor,
                    baseFontSize = tinyFont,
                    textColor = textColor,
                    showEmptyPlaceholder = true
                )
            }
        }

        is MathNode.SubscriptNode -> {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = node.baseLabel,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = baseFontSize,
                    color = textColor
                )
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    NaturalMathSequenceView(
                        sequence = node.subscript,
                        cursor = cursor,
                        baseFontSize = tinyFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                }
            }
        }

        is MathNode.LogBaseNode -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "log",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = (baseFontSize.value * 0.9f).sp,
                    color = textColor
                )
                Box(modifier = Modifier.padding(top = 8.dp, end = 1.dp)) {
                    NaturalMathSequenceView(
                        sequence = node.base,
                        cursor = cursor,
                        baseFontSize = tinyFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                }
                Text("(", fontFamily = JetBrainsMonoFamily, fontSize = baseFontSize, color = textColor)
                NaturalMathSequenceView(
                    sequence = node.argument,
                    cursor = cursor,
                    baseFontSize = (baseFontSize.value * 0.92f).sp,
                    textColor = textColor,
                    showEmptyPlaceholder = true
                )
                Text(")", fontFamily = JetBrainsMonoFamily, fontSize = baseFontSize, color = textColor)
            }
        }

        is MathNode.IntegralNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "∫",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = (baseFontSize.value * 1.45f).sp,
                    color = textColor
                )
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    NaturalMathSequenceView(
                        sequence = node.upper,
                        cursor = cursor,
                        baseFontSize = tinyFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                    Spacer(Modifier.height(4.dp))
                    NaturalMathSequenceView(
                        sequence = node.lower,
                        cursor = cursor,
                        baseFontSize = tinyFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                }
                Text("(", fontFamily = JetBrainsMonoFamily, fontSize = baseFontSize, color = textColor)
                NaturalMathSequenceView(
                    sequence = node.body,
                    cursor = cursor,
                    baseFontSize = childFont,
                    textColor = textColor,
                    showEmptyPlaceholder = true
                )
                Text(")dx", fontFamily = JetBrainsMonoFamily, fontSize = childFont, color = textColor)
            }
        }

        is MathNode.DerivativeNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    Text("d", fontFamily = JetBrainsMonoFamily, fontSize = tinyFont, color = textColor)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(textColor))
                    Text("dx", fontFamily = JetBrainsMonoFamily, fontSize = tinyFont, color = textColor)
                }
                Text("(", fontFamily = JetBrainsMonoFamily, fontSize = baseFontSize, color = textColor)
                NaturalMathSequenceView(
                    sequence = node.body,
                    cursor = cursor,
                    baseFontSize = childFont,
                    textColor = textColor,
                    showEmptyPlaceholder = true
                )
                Text(")|", fontFamily = JetBrainsMonoFamily, fontSize = baseFontSize, color = textColor)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("x=", fontFamily = JetBrainsMonoFamily, fontSize = tinyFont, fontStyle = FontStyle.Italic, color = textColor)
                    NaturalMathSequenceView(
                        sequence = node.atX,
                        cursor = cursor,
                        baseFontSize = tinyFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                }
            }
        }

        is MathNode.SumProdNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NaturalMathSequenceView(
                        sequence = node.upper,
                        cursor = cursor,
                        baseFontSize = tinyFont,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                    Text(
                        text = if (node.isProduct) "Π" else "Σ",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = (baseFontSize.value * 1.25f).sp,
                        color = textColor
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("x=", fontFamily = JetBrainsMonoFamily, fontSize = tinyFont, color = textColor)
                        NaturalMathSequenceView(
                            sequence = node.lower,
                            cursor = cursor,
                            baseFontSize = tinyFont,
                            textColor = textColor,
                            showEmptyPlaceholder = true
                        )
                    }
                }
                Text("(", fontFamily = JetBrainsMonoFamily, fontSize = baseFontSize, color = textColor)
                NaturalMathSequenceView(
                    sequence = node.body,
                    cursor = cursor,
                    baseFontSize = childFont,
                    textColor = textColor,
                    showEmptyPlaceholder = true
                )
                Text(")", fontFamily = JetBrainsMonoFamily, fontSize = baseFontSize, color = textColor)
            }
        }

        is MathNode.AbsNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min).padding(horizontal = 1.dp)
            ) {
                Box(Modifier.width(1.5.dp).fillMaxHeight().padding(vertical = 2.dp).background(textColor))
                Box(Modifier.padding(horizontal = 3.dp)) {
                    NaturalMathSequenceView(
                        sequence = node.inner,
                        cursor = cursor,
                        baseFontSize = (baseFontSize.value * 0.92f).sp,
                        textColor = textColor,
                        showEmptyPlaceholder = true
                    )
                }
                Box(Modifier.width(1.5.dp).fillMaxHeight().padding(vertical = 2.dp).background(textColor))
            }
        }

        is MathNode.MatrixNode -> {
            val matFont = (baseFontSize.value * 0.68f).coerceAtLeast(10f).sp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min).padding(horizontal = 2.dp)
            ) {
                // Left bracket
                Canvas(modifier = Modifier.width(5.dp).fillMaxHeight()) {
                    val s = 1.6.dp.toPx()
                    drawLine(textColor, Offset(size.width, 0f), Offset(0f, 0f), s)
                    drawLine(textColor, Offset(0f, 0f), Offset(0f, size.height), s)
                    drawLine(textColor, Offset(0f, size.height), Offset(size.width, size.height), s)
                }
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    node.cells.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { cell ->
                                NaturalMathSequenceView(
                                    sequence = cell,
                                    cursor = cursor,
                                    baseFontSize = matFont,
                                    textColor = textColor
                                )
                            }
                        }
                    }
                }
                // Right bracket
                Canvas(modifier = Modifier.width(5.dp).fillMaxHeight()) {
                    val s = 1.6.dp.toPx()
                    drawLine(textColor, Offset(0f, 0f), Offset(size.width, 0f), s)
                    drawLine(textColor, Offset(size.width, 0f), Offset(size.width, size.height), s)
                    drawLine(textColor, Offset(size.width, size.height), Offset(0f, size.height), s)
                }
            }
        }
    }
}

/**
 * Custom Canvas-drawn crisp √ radical sign with top overbar spanning the exact radicand width.
 */
@Composable
private fun RadicalContainer(
    indexContent: (@Composable () -> Unit)?,
    strokeColor: Color,
    radicand: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(IntrinsicSize.Min).padding(horizontal = 1.dp)
    ) {
        if (indexContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Top)
                    .padding(end = 1.dp, top = 0.dp)
            ) {
                indexContent()
            }
        }
        // Radical hook
        Canvas(
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
        ) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h * 0.56f)
                lineTo(w * 0.28f, h * 0.48f)
                lineTo(w * 0.55f, h * 0.92f)
                lineTo(w, 1.5.dp.toPx())
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        // Radicand with top vinculum overbar
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(strokeColor)
            )
            Box(modifier = Modifier.padding(start = 2.dp, end = 3.dp, top = 2.dp)) {
                radicand()
            }
        }
    }
}
