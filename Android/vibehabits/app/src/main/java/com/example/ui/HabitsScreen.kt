package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Habit
import com.example.ui.components.AddHabitSheet
import com.example.ui.components.ExportImportSheet
import com.example.ui.components.ExportImportTab
import com.example.ui.components.HabitCard
import com.example.ui.components.HabitDetailSheet
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CanvasDark
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.OnAccentCyan
import com.example.ui.theme.OnErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val habits by viewModel.filteredHabits.collectAsStateWithLifecycle()
    val allHabits by viewModel.habits.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val exportJsonString by viewModel.exportJsonString.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Bottom sheet controls
    var showAddSheet by remember { mutableStateOf(false) }
    var habitToEdit by remember { mutableStateOf<Habit?>(null) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedHabitForDetail by remember { mutableStateOf<Habit?>(null) }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showExportImportSheet by remember { mutableStateOf(false) }
    var exportImportInitialTab by remember { mutableStateOf(ExportImportTab.EXPORT) }
    val exportImportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiMessage()
        }
    }

    // Categories available
    val categories = remember(allHabits) {
        listOf("All") + allHabits.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = CanvasDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Fixed 48dp height Top Bar with flat #141414 background and single bottom divider in #2C2C30
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(49.dp)
                    .background(CanvasDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App title: "HABITS" in titleLarge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "HABITS",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Habit count badge
                        if (allHabits.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceVariantDark)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${allHabits.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Top Bar Action Buttons: Export and Import in AccentCyan
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                exportImportInitialTab = ExportImportTab.EXPORT
                                showExportImportSheet = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("top_bar_export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Export",
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(
                            onClick = {
                                exportImportInitialTab = ExportImportTab.IMPORT
                                showExportImportSheet = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("top_bar_import_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Import",
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Single bottom divider in #2C2C30
                HorizontalDivider(
                    thickness = 1.dp,
                    color = BorderSubtle
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    habitToEdit = null
                    showAddSheet = true
                },
                containerColor = AccentCyan,
                contentColor = OnAccentCyan,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_habit_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Habit",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "NEW HABIT",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnAccentCyan
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CanvasDark)
        ) {
            // Category filter row (if more than 1 category)
            if (categories.size > 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = (cat == "All" && selectedCategory == null) ||
                                (selectedCategory.equals(cat, ignoreCase = true))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) SurfaceVariantDark else CanvasDark)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentCyan else BorderSubtle,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    viewModel.setCategoryFilter(if (cat == "All") null else cat)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("filter_chip_${cat.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) AccentCyan else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Habit List: Scrollable column with 16dp padding and 10dp spacing between cards
            if (habits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "NO HABITS YET",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Start by tracking your daily rituals, or import your existing habits backup JSON.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    habitToEdit = null
                                    showAddSheet = true
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("btn_empty_add_habit")
                        ) {
                            Text(
                                text = "+ ADD FIRST HABIT",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = habits,
                        key = { it.id }
                    ) { habit ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteHabit(habit.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ErrorRed.copy(alpha = 0.85f))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "DELETE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OnErrorRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = OnErrorRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        ) {
                            HabitCard(
                                habit = habit,
                                onToggleToday = { viewModel.toggleToday(habit.id) },
                                onToggleDay = { dateStr, currentStatus ->
                                    viewModel.toggleDay(habit.id, dateStr, currentStatus)
                                },
                                onClick = { selectedHabitForDetail = habit }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Add or Edit Habit
    if (showAddSheet) {
        AddHabitSheet(
            sheetState = addSheetState,
            habitToEdit = habitToEdit,
            onDismiss = {
                scope.launch { addSheetState.hide() }.invokeOnCompletion {
                    showAddSheet = false
                    habitToEdit = null
                }
            },
            onSaveHabit = { title, category ->
                if (habitToEdit == null) {
                    viewModel.addHabit(title, category)
                } else {
                    viewModel.updateHabit(habitToEdit!!.id, title, category)
                }
                scope.launch { addSheetState.hide() }.invokeOnCompletion {
                    showAddSheet = false
                    habitToEdit = null
                }
            }
        )
    }

    // Modal Bottom Sheet: Inspect Habit Details & History
    selectedHabitForDetail?.let { activeHabit ->
        // Keep updated habit from state in sync
        val freshHabit = allHabits.find { it.id == activeHabit.id } ?: activeHabit

        HabitDetailSheet(
            habit = freshHabit,
            sheetState = detailSheetState,
            onDismiss = {
                scope.launch { detailSheetState.hide() }.invokeOnCompletion {
                    selectedHabitForDetail = null
                }
            },
            onToggleDate = { dateStr, currentStatus ->
                viewModel.toggleDay(freshHabit.id, dateStr, currentStatus)
            },
            onEditHabit = {
                habitToEdit = freshHabit
                scope.launch { detailSheetState.hide() }.invokeOnCompletion {
                    selectedHabitForDetail = null
                    showAddSheet = true
                }
            },
            onDeleteHabit = {
                viewModel.deleteHabit(freshHabit.id)
                scope.launch { detailSheetState.hide() }.invokeOnCompletion {
                    selectedHabitForDetail = null
                }
            }
        )
    }

    // Modal Bottom Sheet: Universal Export & Import Engine
    if (showExportImportSheet) {
        ExportImportSheet(
            sheetState = exportImportSheetState,
            initialTab = exportImportInitialTab,
            exportJsonString = exportJsonString,
            onDismiss = {
                scope.launch { exportImportSheetState.hide() }.invokeOnCompletion {
                    showExportImportSheet = false
                }
            },
            onImportJson = { jsonStr, replaceAll ->
                viewModel.importBackup(jsonStr, replaceAll) { success, _ ->
                    if (success) {
                        scope.launch { exportImportSheetState.hide() }.invokeOnCompletion {
                            showExportImportSheet = false
                        }
                    }
                }
            }
        )
    }
}
