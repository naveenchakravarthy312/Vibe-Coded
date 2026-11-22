package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.StageTimeRecord
import com.example.data.model.RallyPresets
import com.example.data.model.TimeFormatter
import com.example.data.preferences.StageSessionConfig
import com.example.ui.theme.ScratchpadMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordTimeSheet(
    editingRecord: StageTimeRecord?,
    prefillTimeMillis: Long?,
    activeSession: StageSessionConfig = StageSessionConfig(),
    onDismiss: () -> Unit,
    onSave: (
        rallyId: String,
        rallyName: String,
        stageName: String,
        distanceKm: Double,
        timeMillis: Long,
        weather: String,
        camera: String,
        carName: String,
        carClass: String,
        transmission: String,
        notes: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Initial state: uses editingRecord if editing, otherwise defaults to activeSession (remembered from last run)
    val initialRallyId = editingRecord?.rallyId ?: activeSession.rallyId
    val initialRally = RallyPresets.RALLIES.firstOrNull { it.id == initialRallyId } ?: RallyPresets.RALLIES.first()

    var selectedRallyId by remember(editingRecord, activeSession) { mutableStateOf(initialRallyId) }
    var selectedRallyName by remember(editingRecord, activeSession) { mutableStateOf(editingRecord?.rallyName ?: activeSession.rallyName) }
    var selectedStageName by remember(editingRecord, activeSession) {
        mutableStateOf(editingRecord?.stageName ?: activeSession.stageName)
    }
    var stageDistanceKm by remember(editingRecord, activeSession) {
        mutableDoubleStateOf(editingRecord?.stageDistanceKm ?: activeSession.stageDistanceKm)
    }

    // Time input fields (Minutes, Seconds, Milliseconds)
    val initialTimeMs = editingRecord?.timeMillis ?: prefillTimeMillis ?: 0L
    var minutesText by remember(editingRecord, prefillTimeMillis) {
        mutableStateOf(if (initialTimeMs > 0) "%02d".format(initialTimeMs / 60000) else "")
    }
    var secondsText by remember(editingRecord, prefillTimeMillis) {
        mutableStateOf(if (initialTimeMs > 0) "%02d".format((initialTimeMs % 60000) / 1000) else "")
    }
    var millisText by remember(editingRecord, prefillTimeMillis) {
        mutableStateOf(if (initialTimeMs > 0) "%03d".format(initialTimeMs % 1000) else "")
    }

    // Weather, Camera, Car
    var selectedWeather by remember(editingRecord, activeSession) {
        mutableStateOf(editingRecord?.weather ?: activeSession.weather)
    }
    var selectedCamera by remember(editingRecord, activeSession) {
        mutableStateOf(editingRecord?.camera ?: activeSession.camera)
    }
    var selectedCar by remember(editingRecord, activeSession) {
        mutableStateOf(editingRecord?.carName ?: activeSession.carName)
    }
    var selectedCarClass by remember(editingRecord, activeSession) {
        mutableStateOf(editingRecord?.carClass ?: activeSession.carClass)
    }
    var selectedTransmission by remember(editingRecord, activeSession) {
        mutableStateOf(editingRecord?.transmission ?: activeSession.transmission)
    }
    var notesText by remember(editingRecord, activeSession) {
        mutableStateOf(editingRecord?.notes ?: activeSession.notes)
    }

    // Toggle for expanding full stage/rally picker if user wants to change from current session
    var showFullSettings by remember { mutableStateOf(editingRecord != null) }

    // Current Rally Object & Current Stage
    val currentRally = RallyPresets.RALLIES.firstOrNull { it.id == selectedRallyId } ?: initialRally
    val currentStage = currentRally.stages.firstOrNull { it.name == selectedStageName } ?: currentRally.stages.first()

    // Helper to step through stages
    fun stepStage(delta: Int) {
        val stages = currentRally.stages
        val currentIndex = stages.indexOfFirst { it.name == selectedStageName }
        val newIndex = if (currentIndex != -1) {
            (currentIndex + delta).mod(stages.size)
        } else {
            0
        }
        val targetStage = stages[newIndex]
        selectedStageName = targetStage.name
        stageDistanceKm = targetStage.distanceKm
    }

    // Compute preview time
    val mins = minutesText.toIntOrNull() ?: 0
    val secs = (secondsText.toIntOrNull() ?: 0).coerceIn(0, 59)
    val ms = (millisText.toIntOrNull() ?: 0).coerceIn(0, 999)
    val calculatedMillis = TimeFormatter.parseToMillis(mins, secs, ms)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (editingRecord != null) "EDIT STAGE TIME" else "LOG STAGE RUN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (editingRecord != null) "Modify existing telemetry record" else "Settings remembered from your last run",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close Sheet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sticky Session Active Banner & Quick Stage Switcher
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACTIVE STAGE SESSION",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ScratchpadMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Fast Stage Stepper (Prev / Next)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable { stepStage(-1) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ChevronLeft,
                                        contentDescription = "Previous Stage",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "PREV",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable { stepStage(1) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "NEXT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        fontSize = 10.sp
                                    )
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Next Stage",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Rally & Stage Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = currentRally.flag, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${currentRally.name} · $selectedStageName",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "%.1f km · %s".format(stageDistanceKm, currentStage.surfaceDetail),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = ScratchpadMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (currentStage.isEpicStage) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "★ EPIC",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontFamily = ScratchpadMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (currentStage.isSuperSpecialStage) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "⚡ SSS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontFamily = ScratchpadMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Conditions pill summary
                    Text(
                        text = "$selectedWeather · $selectedCamera · $selectedCar",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Primary Time Input Box (Prominently Placed at the top for zero-friction entry)
            Text(
                text = "1. ENTER STAGE TIME (MM : SS . mmm)",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ScratchpadMonoFontFamily,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = TimeFormatter.formatMillis(calculatedMillis),
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minutes
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) minutesText = it },
                            label = { Text("MIN") },
                            placeholder = { Text("00") },
                            modifier = Modifier
                                .width(80.dp)
                                .testTag("time_input_min"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ScratchpadMonoFontFamily),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Text(
                            text = ":",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ScratchpadMonoFontFamily,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        // Seconds
                        OutlinedTextField(
                            value = secondsText,
                            onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) secondsText = it },
                            label = { Text("SEC") },
                            placeholder = { Text("00") },
                            modifier = Modifier
                                .width(80.dp)
                                .testTag("time_input_sec"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ScratchpadMonoFontFamily),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Text(
                            text = ".",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ScratchpadMonoFontFamily,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        // Milliseconds
                        OutlinedTextField(
                            value = millisText,
                            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) millisText = it },
                            label = { Text("MS") },
                            placeholder = { Text("000") },
                            modifier = Modifier
                                .width(90.dp)
                                .testTag("time_input_ms"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ScratchpadMonoFontFamily),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Toggle Expand for Modifying Rally, Stage, Conditions & Notes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFullSettings = !showFullSettings },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showFullSettings) "HIDE STAGE & VEHICLE SETTINGS" else "CHANGE STAGE, VEHICLE OR CONDITIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ScratchpadMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (showFullSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showFullSettings) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Rally Country Picker
                    Text(
                        text = "CHANGE RALLY",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RallyPresets.RALLIES.forEach { rally ->
                            val isSelected = rally.id == selectedRallyId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clickable {
                                        selectedRallyId = rally.id
                                        selectedRallyName = rally.name
                                        val firstStage = rally.stages.firstOrNull()
                                        if (firstStage != null) {
                                            selectedStageName = firstStage.name
                                            stageDistanceKm = firstStage.distanceKm
                                        }
                                    }
                                    .testTag("rally_chip_${rally.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(text = rally.flag, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = rally.country,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stages in selected rally
                    Text(
                        text = "STAGES IN ${currentRally.country.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        currentRally.stages.forEach { stage ->
                            val isSelected = stage.name == selectedStageName
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clickable {
                                        selectedStageName = stage.name
                                        stageDistanceKm = stage.distanceKm
                                    }
                                    .testTag("stage_chip_${stage.id}")
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = "${stage.name} · %.1fkm".format(stage.distanceKm),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (stage.isEpicStage) {
                                        Text(
                                            text = "★ EPIC STAGE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontFamily = ScratchpadMonoFontFamily,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (stage.isSuperSpecialStage) {
                                        Text(
                                            text = "⚡ SUPER SPECIAL STAGE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontFamily = ScratchpadMonoFontFamily,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Weather Condition
                    Text(
                        text = "WEATHER CONDITION",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RallyPresets.WEATHER_OPTIONS.forEach { (name, icon) ->
                            val isSelected = selectedWeather == name
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clickable { selectedWeather = name }
                                    .testTag("weather_chip_${name.replace("/", "_").trim()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(text = icon, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Camera View
                    Text(
                        text = "CAMERA VIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RallyPresets.CAMERA_OPTIONS.forEach { (name, icon) ->
                            val isSelected = selectedCamera == name
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clickable { selectedCamera = name }
                                    .testTag("camera_chip_${name.replace("/", "_").trim()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(text = icon, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Car & Transmission
                    Text(
                        text = "CAR & TRANSMISSION",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val allCars = RallyPresets.CAR_CLASSES.flatMap { it.second }
                        allCars.take(6).forEach { car ->
                            val isSelected = selectedCar == car
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.clickable { selectedCar = car }
                            ) {
                                Text(
                                    text = car,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = ScratchpadMonoFontFamily,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RallyPresets.TRANSMISSIONS.forEach { trans ->
                            val isSel = selectedTransmission == trans
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTransmission = trans }
                            ) {
                                Text(
                                    text = trans,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = ScratchpadMonoFontFamily,
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Notes
                    Text(
                        text = "PACENOTES & SETUP (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        placeholder = { Text("e.g. Cut corner on turn 5, wet compound tires, stiff suspension") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notes_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Action Button
            Button(
                onClick = {
                    if (calculatedMillis > 0) {
                        onSave(
                            selectedRallyId,
                            selectedRallyName,
                            selectedStageName,
                            stageDistanceKm,
                            calculatedMillis,
                            selectedWeather,
                            selectedCamera,
                            selectedCar,
                            selectedCarClass,
                            selectedTransmission,
                            notesText
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_record_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = calculatedMillis > 0
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (editingRecord != null) "UPDATE STAGE RECORD" else "SAVE STAGE RECORD (${TimeFormatter.formatMillis(calculatedMillis)})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
