package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RallyPresets
import com.example.ui.components.ExportImportSheet
import com.example.ui.components.LeaderboardSheet
import com.example.ui.components.QuickRecordCard
import com.example.ui.components.RecordTimeSheet
import com.example.ui.components.StageRecordCard
import com.example.ui.components.ThemeSheet
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ScratchpadMonoFontFamily
import com.example.ui.theme.ScratchpadSansFontFamily
import com.example.ui.viewmodel.WrcViewModel

@Composable
fun WrcApp(
    viewModel: WrcViewModel,
    modifier: Modifier = Modifier
) {
    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
    val filteredRecords by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val bestTimesMap by viewModel.bestTimesMap.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()

    val isRecordSheetOpen by viewModel.isRecordSheetOpen.collectAsStateWithLifecycle()
    val editingRecord by viewModel.editingRecord.collectAsStateWithLifecycle()
    val prefillTimeMillis by viewModel.prefillTimeMillis.collectAsStateWithLifecycle()
    val isThemeSheetOpen by viewModel.isThemeSheetOpen.collectAsStateWithLifecycle()
    val isExportImportSheetOpen by viewModel.isExportImportSheetOpen.collectAsStateWithLifecycle()
    val leaderboardTarget by viewModel.leaderboardTarget.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Low-Profile Top Bar (48dp height as prescribed in ANDROID-STYLE.md Section 1 & 5)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Brand / Title (no game mentions)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "RALLY TELEMETRY",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = ScratchpadSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Top Bar Action Strip: Search, Data Export, Theme Switcher
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isSearchExpanded = !isSearchExpanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search Runs",
                                tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.openExportImportSheet() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "Data Interchange",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.openThemeSheet() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Brightness4,
                                contentDescription = "Theme Engine",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                // Single 1dp bottom divider using outline token (ANDROID-STYLE.md Section 5)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
        },
        bottomBar = {
            // Accessory Bar: Action Chips, Counters & Metrics (ANDROID-STYLE.md Section 5)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Action: Record Time chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { viewModel.openNewRecordSheet() }
                            .testTag("log_time_accessory_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Log Stage Time",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LOG TIME",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Metrics Counter
                    Text(
                        text = "${filteredRecords.size} / ${allRecords.size} RUNS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Quick Toggle Chips: PB Only & Favorites
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (filterState.personalBestsOnly) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (filterState.personalBestsOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable { viewModel.togglePersonalBestsOnly() }
                        ) {
                            Text(
                                text = "⚡ PB",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (filterState.personalBestsOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (filterState.favoritesOnly) Color(0xFFE5C07B).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (filterState.favoritesOnly) Color(0xFFE5C07B) else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable { viewModel.toggleFavoritesOnly() }
                        ) {
                            Text(
                                text = "★ FAV",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (filterState.favoritesOnly) Color(0xFFE5C07B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        val isAmoled = themeConfig.themeMode == AppThemeMode.AMOLED
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAmoled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isAmoled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable { viewModel.toggleAmoled() }
                        ) {
                            Text(
                                text = "OLED",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isAmoled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        // Sticky Session Settings Toggle (Locks stage settings between runs)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (activeSession.stickyEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (activeSession.stickyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable { viewModel.toggleStickySession(!activeSession.stickyEnabled) }
                        ) {
                            Text(
                                text = if (activeSession.stickyEnabled) "🔒 STICKY" else "🔓 STICKY",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (activeSession.stickyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Optional Search Input Bar
            if (isSearchExpanded) {
                item {
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Filter by stage, rally, car, notes...") },
                        trailingIcon = {
                            if (filterState.searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Quick Manual Time Entry Card (Uses persistent active stage session)
            item {
                QuickRecordCard(
                    activeSession = activeSession,
                    onUpdateSession = { viewModel.updateActiveSession(it) },
                    onNextStage = { viewModel.advanceToNextStage() },
                    onPrevStage = { viewModel.advanceToPreviousStage() },
                    onRecordTime = { rId, rName, sName, dist, timeMs, weather, camera, car, carClass, trans, notes ->
                        viewModel.saveOrUpdateRecord(
                            rallyId = rId,
                            rallyName = rName,
                            stageName = sName,
                            distanceKm = dist,
                            timeMillis = timeMs,
                            weather = weather,
                            camera = camera,
                            carName = car,
                            carClass = carClass,
                            transmission = trans,
                            notes = notes
                        )
                    }
                )
            }

            // Rallies Horizontal Scroll Filter
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RALLIES & COUNTRIES",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ScratchpadMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        if (filterState.selectedRallyId != null) {
                            Text(
                                text = "Show All",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.setRallyFilter(null) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "All" chip
                        val isAllSelected = filterState.selectedRallyId == null
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable { viewModel.setRallyFilter(null) }
                        ) {
                            Text(
                                text = "ALL RALLIES",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAllSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        RallyPresets.RALLIES.forEach { rally ->
                            val isSel = filterState.selectedRallyId == rally.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.clickable { viewModel.setRallyFilter(rally.id) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(text = rally.flag, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = rally.country,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section Header: Logged Stage Times
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECORDED STAGE RUNS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sorted by Date (Newest)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Records List or Empty State
            if (filteredRecords.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "NO STAGE RUNS LOGGED YET",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = ScratchpadSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter your stage completion time using the form above or tap 'LOG TIME' below to record a run.",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredRecords,
                    key = { it.id }
                ) { record ->
                    val stageKey = "${record.rallyId}__${record.stageName}"
                    val bestTime = bestTimesMap[stageKey]

                    val rallyObj = RallyPresets.RALLIES.firstOrNull { it.id == record.rallyId }
                        ?: RallyPresets.RALLIES.first()
                    val stagePreset = rallyObj.stages.firstOrNull { it.name == record.stageName }
                        ?: com.example.data.model.StagePreset(
                            id = record.stageName,
                            name = record.stageName,
                            distanceKm = record.stageDistanceKm,
                            surfaceDetail = rallyObj.surface
                        )

                    StageRecordCard(
                        record = record,
                        bestTimeForStage = bestTime,
                        onToggleFavorite = { viewModel.toggleFavorite(record) },
                        onEdit = { viewModel.openEditRecordSheet(record) },
                        onDelete = { viewModel.deleteRecord(record) },
                        onViewLeaderboard = { viewModel.openLeaderboard(rallyObj, stagePreset) },
                        onRepeatRun = { viewModel.repeatStageRun(record) }
                    )
                }
            }

            // Bottom space
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Bottom Sheets
    if (isRecordSheetOpen) {
        RecordTimeSheet(
            editingRecord = editingRecord,
            prefillTimeMillis = prefillTimeMillis,
            activeSession = activeSession,
            onDismiss = { viewModel.closeRecordSheet() },
            onSave = { rId, rName, sName, dist, timeMs, weather, camera, car, carClass, trans, notes ->
                viewModel.saveOrUpdateRecord(
                    rallyId = rId,
                    rallyName = rName,
                    stageName = sName,
                    distanceKm = dist,
                    timeMillis = timeMs,
                    weather = weather,
                    camera = camera,
                    carName = car,
                    carClass = carClass,
                    transmission = trans,
                    notes = notes
                )
            }
        )
    }

    if (isThemeSheetOpen) {
        ThemeSheet(
            currentMode = themeConfig.themeMode,
            dynamicColor = themeConfig.dynamicColor,
            onSelectMode = { viewModel.setThemeMode(it) },
            onToggleDynamicColor = { viewModel.setDynamicColor(it) },
            onDismiss = { viewModel.closeThemeSheet() }
        )
    }

    if (isExportImportSheetOpen) {
        ExportImportSheet(
            onExportJson = { viewModel.exportRecordsToJson() },
            onExportCsv = { viewModel.exportRecordsToCsv() },
            onImportJson = { viewModel.importRecordsFromJson(it) },
            onShowToast = { viewModel.showToast(it) },
            onDismiss = { viewModel.closeExportImportSheet() }
        )
    }

    leaderboardTarget?.let { (rally, stage) ->
        LeaderboardSheet(
            rally = rally,
            stage = stage,
            records = allRecords,
            onDismiss = { viewModel.closeLeaderboard() },
            onLogTimeForStage = { viewModel.openLeaderboardStageRecord(rally, stage) }
        )
    }
}
