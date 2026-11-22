package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CalculatorEngine
import com.example.ui.theme.*

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val expression by viewModel.expression.collectAsState()
    val previewResult by viewModel.previewResult.collectAsState()
    val angleMode by viewModel.angleMode.collectAsState()
    val isScientific by viewModel.isScientificExpanded.collectAsState()
    val memoryValue by viewModel.memoryValue.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val scrollState = rememberScrollState()

    // Auto-scroll expression to end when it changes
    LaunchedEffect(expression) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ----------------------------------------------------
        // TOP DISPLAY CARD
        // ----------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Secondary Accessory Row (Angle mode, Memory chips, Scientific toggle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // DEG/RAD Toggle Chip
                    FilterChip(
                        selected = angleMode == CalculatorEngine.AngleMode.RAD,
                        onClick = { viewModel.toggleAngleMode() },
                        label = {
                            Text(
                                text = angleMode.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("deg_rad_toggle")
                    )

                    // Memory Chips Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (memoryValue != 0.0) {
                            AssistChip(
                                onClick = { viewModel.memoryClear() },
                                label = { Text("MC", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                            AssistChip(
                                onClick = { viewModel.memoryRecall() },
                                label = { Text("MR", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                        AssistChip(
                            onClick = { viewModel.memoryAdd() },
                            label = { Text("M+", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                        AssistChip(
                            onClick = { viewModel.memorySub() },
                            label = { Text("M-", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Scientific Expander Button
                    FilledTonalIconButton(
                        onClick = { viewModel.toggleScientific() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("scientific_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isScientific) Icons.Default.KeyboardArrowUp else Icons.Default.Functions,
                            contentDescription = "Toggle scientific keypad",
                            tint = if (isScientific) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Main Display Text (Expression & Live Result)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    // Primary Expression Text (Horizontally scrollable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = if (expression.isEmpty()) "0" else expression,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = ScratchpadMonoFontFamily,
                                fontSize = if (expression.length > 14) 28.sp else 36.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (expression.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.testTag("calculator_expression_display")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Result Preview or Error Message
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            ),
                            textAlign = TextAlign.End,
                            modifier = Modifier.testTag("calculator_error_text")
                        )
                    } else if (previewResult.isNotEmpty()) {
                        Text(
                            text = previewResult,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = ScratchpadMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            textAlign = TextAlign.End,
                            modifier = Modifier.testTag("calculator_preview_result")
                        )
                    } else {
                        // Empty placeholder to maintain layout balance
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ----------------------------------------------------
        // KEYPAD SECTION
        // ----------------------------------------------------
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Scientific Keypad (Expandable)
            AnimatedVisibility(
                visible = isScientific,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    // Sci Row 1: sin, cos, tan, ln, log
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sciKeyModifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                        val sciBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        val sciColor = MaterialTheme.colorScheme.primary

                        CalcKeyButton("sin", { viewModel.onFunction("sin") }, sciKeyModifier, sciBg, sciColor, fontSize = 14)
                        CalcKeyButton("cos", { viewModel.onFunction("cos") }, sciKeyModifier, sciBg, sciColor, fontSize = 14)
                        CalcKeyButton("tan", { viewModel.onFunction("tan") }, sciKeyModifier, sciBg, sciColor, fontSize = 14)
                        CalcKeyButton("ln", { viewModel.onFunction("ln") }, sciKeyModifier, sciBg, sciColor, fontSize = 14)
                        CalcKeyButton("log", { viewModel.onFunction("log") }, sciKeyModifier, sciBg, sciColor, fontSize = 14)
                    }

                    // Sci Row 2: √, ^, π, e, !
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sciKeyModifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                        val sciBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        val sciColor = MaterialTheme.colorScheme.secondary

                        CalcKeyButton("√", { viewModel.onFunction("sqrt") }, sciKeyModifier, sciBg, sciColor, fontSize = 16)
                        CalcKeyButton("^", { viewModel.onOperator("^") }, sciKeyModifier, sciBg, sciColor, fontSize = 16)
                        CalcKeyButton("π", { viewModel.onConstant("π") }, sciKeyModifier, sciBg, sciColor, fontSize = 16)
                        CalcKeyButton("e", { viewModel.onConstant("e") }, sciKeyModifier, sciBg, sciColor, fontSize = 16)
                        CalcKeyButton("!", { viewModel.onOperator("!") }, sciKeyModifier, sciBg, sciColor, fontSize = 16)
                    }

                    // Sci Row 3: asin, acos, atan, |x|, 1/x
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sciKeyModifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                        val sciBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        val sciColor = MaterialTheme.colorScheme.onSurfaceVariant

                        CalcKeyButton("asin", { viewModel.onFunction("asin") }, sciKeyModifier, sciBg, sciColor, fontSize = 13)
                        CalcKeyButton("acos", { viewModel.onFunction("acos") }, sciKeyModifier, sciBg, sciColor, fontSize = 13)
                        CalcKeyButton("atan", { viewModel.onFunction("atan") }, sciKeyModifier, sciBg, sciColor, fontSize = 13)
                        CalcKeyButton("|x|", { viewModel.onFunction("abs") }, sciKeyModifier, sciBg, sciColor, fontSize = 14)
                        CalcKeyButton("1/x", {
                            viewModel.onFunction("1/")
                        }, sciKeyModifier, sciBg, sciColor, fontSize = 14)
                    }
                }
            }

            // Standard Keypad Rows (5 rows x 4 columns)
            val rowHeight = if (isScientific) 52.dp else 60.dp
            val numBg = MaterialTheme.colorScheme.surfaceVariant
            val numColor = MaterialTheme.colorScheme.onSurface
            val opBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            val opColor = MaterialTheme.colorScheme.primary

            // Row 1: AC, ( ), %, ÷
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val m = Modifier
                    .weight(1f)
                    .height(rowHeight)
                CalcKeyButton(
                    text = "AC",
                    onClick = { viewModel.onClear() },
                    modifier = m,
                    backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.error,
                    testTag = "calc_key_ac"
                )
                CalcKeyButton(
                    text = "( )",
                    onClick = { viewModel.onParenthesis() },
                    modifier = m,
                    backgroundColor = opBg,
                    contentColor = opColor,
                    testTag = "calc_key_parens"
                )
                CalcKeyButton(
                    text = "%",
                    onClick = { viewModel.onOperator("%") },
                    modifier = m,
                    backgroundColor = opBg,
                    contentColor = opColor,
                    testTag = "calc_key_percent"
                )
                CalcKeyButton(
                    text = "÷",
                    onClick = { viewModel.onOperator("÷") },
                    modifier = m,
                    backgroundColor = opBg,
                    contentColor = opColor,
                    testTag = "calc_key_divide"
                )
            }

            // Row 2: 7, 8, 9, ×
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val m = Modifier
                    .weight(1f)
                    .height(rowHeight)
                CalcKeyButton("7", { viewModel.onDigit("7") }, m, numBg, numColor)
                CalcKeyButton("8", { viewModel.onDigit("8") }, m, numBg, numColor)
                CalcKeyButton("9", { viewModel.onDigit("9") }, m, numBg, numColor)
                CalcKeyButton("×", { viewModel.onOperator("×") }, m, opBg, opColor, testTag = "calc_key_multiply")
            }

            // Row 3: 4, 5, 6, −
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val m = Modifier
                    .weight(1f)
                    .height(rowHeight)
                CalcKeyButton("4", { viewModel.onDigit("4") }, m, numBg, numColor)
                CalcKeyButton("5", { viewModel.onDigit("5") }, m, numBg, numColor)
                CalcKeyButton("6", { viewModel.onDigit("6") }, m, numBg, numColor)
                CalcKeyButton("−", { viewModel.onOperator("-") }, m, opBg, opColor, testTag = "calc_key_subtract")
            }

            // Row 4: 1, 2, 3, +
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val m = Modifier
                    .weight(1f)
                    .height(rowHeight)
                CalcKeyButton("1", { viewModel.onDigit("1") }, m, numBg, numColor)
                CalcKeyButton("2", { viewModel.onDigit("2") }, m, numBg, numColor)
                CalcKeyButton("3", { viewModel.onDigit("3") }, m, numBg, numColor)
                CalcKeyButton("+", { viewModel.onOperator("+") }, m, opBg, opColor, testTag = "calc_key_add")
            }

            // Row 5: 0, ., ⌫, =
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val m = Modifier
                    .weight(1f)
                    .height(rowHeight)
                CalcKeyButton("0", { viewModel.onDigit("0") }, m, numBg, numColor)
                CalcKeyButton(".", { viewModel.onDecimal() }, m, numBg, numColor, testTag = "calc_key_decimal")
                CalcKeyButton(
                    text = "⌫",
                    onClick = { viewModel.onBackspace() },
                    modifier = m,
                    backgroundColor = numBg,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    testTag = "calc_key_backspace"
                )
                CalcKeyButton(
                    text = "=",
                    onClick = { viewModel.onEquals() },
                    modifier = m,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    isAccent = true,
                    fontSize = 24,
                    testTag = "calc_key_equals"
                )
            }
        }
    }
}
