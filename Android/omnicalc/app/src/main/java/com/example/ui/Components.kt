package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ScratchpadMonoFontFamily

@Composable
fun OmniCalcTopBar(
    activeTab: ActiveTab,
    onTabSelected: (ActiveTab) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onOpenInfoDialog: () -> Unit,
    modifier: Modifier = Modifier,
    dynamicColor: Boolean = true,
    onDynamicColorChanged: (Boolean) -> Unit = {}
) {
    var showThemeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Compact Tab Selector Strip inside the 48dp top bar
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabSegmentItem(
                    label = "Calculator",
                    icon = Icons.Default.Calculate,
                    isSelected = activeTab == ActiveTab.CALCULATOR,
                    onClick = { onTabSelected(ActiveTab.CALCULATOR) },
                    modifier = Modifier.weight(1f)
                )
                TabSegmentItem(
                    label = "Converter",
                    icon = Icons.Default.SyncAlt,
                    isSelected = activeTab == ActiveTab.CONVERTER,
                    onClick = { onTabSelected(ActiveTab.CONVERTER) },
                    modifier = Modifier.weight(1f)
                )
                TabSegmentItem(
                    label = "History",
                    icon = Icons.Default.History,
                    isSelected = activeTab == ActiveTab.HISTORY,
                    onClick = { onTabSelected(ActiveTab.HISTORY) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Action icons: theme switcher, info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("theme_switch_button")
                ) {
                    val icon = when (themeMode) {
                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                        AppThemeMode.DARK -> Icons.Default.DarkMode
                        AppThemeMode.AMOLED -> Icons.Default.Brightness2
                        AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Switch theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onOpenInfoDialog,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("info_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "App info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = themeMode,
            dynamicColor = dynamicColor,
            onSelectMode = {
                onThemeModeSelected(it)
                showThemeDialog = false
            },
            onDynamicColorChanged = onDynamicColorChanged,
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun TabSegmentItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val bg = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 7.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CalcKeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    isAccent: Boolean = false,
    fontSize: Int = 20,
    testTag: String = "calc_key_$text"
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(
                    if (isAccent) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                )
                onClick()
            },
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isAccent) 4.dp else 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = ScratchpadMonoFontFamily,
                    fontWeight = if (isAccent) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = fontSize.sp
                ),
                color = contentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentMode: AppThemeMode,
    dynamicColor: Boolean = true,
    onSelectMode: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Appearance",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOptionRow("System Auto", "Follow OS dark/light mode", currentMode == AppThemeMode.SYSTEM) {
                    onSelectMode(AppThemeMode.SYSTEM)
                }
                ThemeOptionRow("Crisp Light", "Clean high-contrast neutral theme", currentMode == AppThemeMode.LIGHT) {
                    onSelectMode(AppThemeMode.LIGHT)
                }
                ThemeOptionRow("Matte Dark", "Linux #141414 charcoal aesthetic", currentMode == AppThemeMode.DARK) {
                    onSelectMode(AppThemeMode.DARK)
                }
                ThemeOptionRow("Pure AMOLED", "Pitch black #000000 for battery saving", currentMode == AppThemeMode.AMOLED) {
                    onSelectMode(AppThemeMode.AMOLED)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Material You Dynamic Color",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Android 12+ wallpaper accents",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChanged,
                        modifier = Modifier.testTag("dynamic_color_switch")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
