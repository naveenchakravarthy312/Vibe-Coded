package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UnitCategory
import com.example.engine.UnitConverter
import com.example.engine.UnitDefinition
import com.example.ui.theme.ScratchpadMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val fromUnit by viewModel.fromUnit.collectAsState()
    val toUnit by viewModel.toUnit.collectAsState()
    val converterInput by viewModel.converterInput.collectAsState()
    val convertedOutput by viewModel.convertedOutput.collectAsState()
    val quickComparisons by viewModel.quickComparisons.collectAsState()

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val categoryScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp)
    ) {
        // ----------------------------------------------------
        // CATEGORY PICKER HORIZONTAL BAR
        // ----------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(categoryScrollState)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UnitCategory.values().forEach { cat ->
                val isSelected = cat == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectCategory(cat) },
                    label = {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = getCategoryIcon(cat),
                            contentDescription = cat.title,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("cat_chip_${cat.id}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ----------------------------------------------------
        // CONVERTER CARDS (FROM & TO)
        // ----------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // "From" Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FROM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Unit selector button
                    FilledTonalButton(
                        onClick = { showFromPicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("from_unit_selector")
                    ) {
                        Text(
                            text = "${fromUnit.name} (${fromUnit.symbol})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select unit",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Input value display
                Text(
                    text = converterInput,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = ScratchpadMonoFontFamily,
                        fontSize = if (converterInput.length > 10) 28.sp else 34.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("converter_input_display")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Divider with Swap button centered
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )
                    IconButton(
                        onClick = { viewModel.swapUnits() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .testTag("swap_units_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap units",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // "To" Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Target unit selector button
                    FilledTonalButton(
                        onClick = { showToPicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("to_unit_selector")
                    ) {
                        Text(
                            text = "${toUnit.name} (${toUnit.symbol})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select target unit",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Converted output display
                Text(
                    text = convertedOutput,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = ScratchpadMonoFontFamily,
                        fontSize = if (convertedOutput.length > 10) 28.sp else 34.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("converter_output_display")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons: Copy, Save to History, Push to Calc
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Unit Conversion", "$converterInput ${fromUnit.symbol} = $convertedOutput ${toUnit.symbol}")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(38.dp).testTag("copy_conversion_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.saveConversionToHistory()
                            Toast.makeText(context, "Saved to History log", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(38.dp).testTag("save_conversion_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = {
                            viewModel.pushConvertedToCalculator(convertedOutput)
                        },
                        modifier = Modifier.weight(1.2f).height(38.dp).testTag("push_to_calc_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = "To Calc", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("To Calc", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ----------------------------------------------------
        // NUMERIC KEYPAD & QUICK COMPARISONS
        // ----------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Numeric Keypad for unit entry
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val numBg = MaterialTheme.colorScheme.surfaceVariant
                val numColor = MaterialTheme.colorScheme.onSurface
                val keyHeight = 44.dp

                // Row 1: 7, 8, 9, C
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcKeyButton("7", { viewModel.onConverterKey("7") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton("8", { viewModel.onConverterKey("8") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton("9", { viewModel.onConverterKey("9") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton(
                        "C", { viewModel.onConverterKey("CLEAR") },
                        Modifier.weight(1f).height(keyHeight),
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.error,
                        fontSize = 16,
                        testTag = "conv_key_clear"
                    )
                }

                // Row 2: 4, 5, 6, ⌫
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcKeyButton("4", { viewModel.onConverterKey("4") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton("5", { viewModel.onConverterKey("5") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton("6", { viewModel.onConverterKey("6") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton(
                        "⌫", { viewModel.onConverterKey("BACKSPACE") },
                        Modifier.weight(1f).height(keyHeight),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16,
                        testTag = "conv_key_backspace"
                    )
                }

                // Row 3: 1, 2, 3, ±
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcKeyButton("1", { viewModel.onConverterKey("1") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton("2", { viewModel.onConverterKey("2") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton("3", { viewModel.onConverterKey("3") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton("±", { viewModel.onConverterKey("+/-") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                }

                // Row 4: 0, .
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalcKeyButton("0", { viewModel.onConverterKey("0") }, Modifier.weight(2f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton(".", { viewModel.onConverterKey(".") }, Modifier.weight(1f).height(keyHeight), numBg, numColor, fontSize = 18)
                    CalcKeyButton("⇄", { viewModel.swapUnits() }, Modifier.weight(1f).height(keyHeight), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, fontSize = 18)
                }
            }

            // Quick Reference List (Converts to all other units in real-time)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    Text(
                        text = "All Units",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(quickComparisons) { item ->
                            val isTarget = item.unit.id == toUnit.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setToUnit(item.unit) },
                                color = if (isTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.unit.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.valueFormatted,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = ScratchpadMonoFontFamily,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = if (isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = item.unit.symbol,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Unit Picker Bottom Sheets
    if (showFromPicker) {
        UnitPickerSheet(
            title = "Convert From",
            units = UnitConverter.getUnitsForCategory(selectedCategory),
            selectedUnit = fromUnit,
            onSelect = {
                viewModel.setFromUnit(it)
                showFromPicker = false
            },
            onDismiss = { showFromPicker = false }
        )
    }

    if (showToPicker) {
        UnitPickerSheet(
            title = "Convert To",
            units = UnitConverter.getUnitsForCategory(selectedCategory),
            selectedUnit = toUnit,
            onSelect = {
                viewModel.setToUnit(it)
                showToPicker = false
            },
            onDismiss = { showToPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitPickerSheet(
    title: String,
    units: List<UnitDefinition>,
    selectedUnit: UnitDefinition,
    onSelect: (UnitDefinition) -> Unit,
    onDismiss: () -> Unit
) {
    var searchFilter by remember { mutableStateOf("") }
    val filteredUnits = remember(units, searchFilter) {
        if (searchFilter.isBlank()) units
        else units.filter {
            it.name.contains(searchFilter, ignoreCase = true) ||
                it.symbol.contains(searchFilter, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                placeholder = { Text("Filter units...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Filter units",
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredUnits) { unit ->
                    val isSelected = unit.id == selectedUnit.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { onSelect(unit) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = unit.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = unit.symbol,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = ScratchpadMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun getCategoryIcon(cat: UnitCategory) = when (cat) {
    UnitCategory.LENGTH -> Icons.Default.Straighten
    UnitCategory.MASS -> Icons.Default.Scale
    UnitCategory.TEMPERATURE -> Icons.Default.Thermostat
    UnitCategory.AREA -> Icons.Default.CropFree
    UnitCategory.VOLUME -> Icons.Default.Science
    UnitCategory.SPEED -> Icons.Default.Speed
    UnitCategory.DIGITAL -> Icons.Default.Storage
    UnitCategory.TIME -> Icons.Default.Schedule
    UnitCategory.ENERGY -> Icons.Default.Bolt
    UnitCategory.PRESSURE -> Icons.Default.Compress
    UnitCategory.FUEL -> Icons.Default.LocalGasStation
}
