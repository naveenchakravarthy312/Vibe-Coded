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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.RallyPresets
import com.example.data.model.TimeFormatter
import com.example.data.preferences.StageSessionConfig
import com.example.ui.theme.ScratchpadMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickRecordCard(
    activeSession: StageSessionConfig = StageSessionConfig(),
    onUpdateSession: (StageSessionConfig) -> Unit = {},
    onNextStage: () -> Unit = {},
    onPrevStage: () -> Unit = {},
    onRecordTime: (
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
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stage and rally state synced with activeSession
    val currentRally = RallyPresets.RALLIES.firstOrNull { it.id == activeSession.rallyId } ?: RallyPresets.RALLIES.first()
    val currentStage = currentRally.stages.firstOrNull { it.name == activeSession.stageName } ?: currentRally.stages.first()

    // Time input fields (empty by default to prevent accidental fake time logging)
    var minutesText by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("") }
    var millisText by remember { mutableStateOf("") }

    // Toggle for more options (change rally country, notes)
    var showDetails by remember { mutableStateOf(false) }

    val mins = minutesText.toIntOrNull() ?: 0
    val secs = (secondsText.toIntOrNull() ?: 0).coerceIn(0, 59)
    val ms = (millisText.toIntOrNull() ?: 0).coerceIn(0, 999)
    val calculatedMillis = TimeFormatter.parseToMillis(mins, secs, ms)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Active Session Indicator + Rally Flag + Prev/Next Steppers
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
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STAGE TELEMETRY",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Stage Stepper: Prev & Next buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clickable { onPrevStage() }
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
                        modifier = Modifier.clickable { onNextStage() }
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

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "${currentRally.flag} ${currentRally.country}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ScratchpadMonoFontFamily,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time Display & Direct Number Fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = TimeFormatter.formatMillis(calculatedMillis),
                    fontSize = 32.sp,
                    fontFamily = ScratchpadMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minutes Input
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) minutesText = it },
                        label = { Text("MIN") },
                        placeholder = { Text("00") },
                        modifier = Modifier
                            .width(76.dp)
                            .testTag("quick_min_input"),
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ScratchpadMonoFontFamily,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    // Seconds Input
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) secondsText = it },
                        label = { Text("SEC") },
                        placeholder = { Text("00") },
                        modifier = Modifier
                            .width(76.dp)
                            .testTag("quick_sec_input"),
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ScratchpadMonoFontFamily,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    // Milliseconds Input
                    OutlinedTextField(
                        value = millisText,
                        onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) millisText = it },
                        label = { Text("MS") },
                        placeholder = { Text("000") },
                        modifier = Modifier
                            .width(86.dp)
                            .testTag("quick_ms_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ScratchpadMonoFontFamily),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            minutesText = ""
                            secondsText = ""
                            millisText = ""
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Time",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1-Tap Stage Selector for current rally (4 stages)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE STAGE (1-TAP SWITCH):",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ScratchpadMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                if (currentStage.isEpicStage) {
                    Text(
                        text = "★ EPIC",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (currentStage.isSuperSpecialStage) {
                    Text(
                        text = "⚡ SSS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                currentRally.stages.forEach { stage ->
                    val isSel = stage.name == activeSession.stageName
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.clickable {
                            onUpdateSession(
                                activeSession.copy(
                                    stageName = stage.name,
                                    stageDistanceKm = stage.distanceKm
                                )
                            )
                        }
                    ) {
                        Text(
                            text = "${stage.name} (%.1fkm)".format(stage.distanceKm),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ScratchpadMonoFontFamily,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conditions & Setup Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONDITIONS: ${activeSession.weather} · ${activeSession.carName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ScratchpadMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showDetails = !showDetails }
                ) {
                    Text(
                        text = if (showDetails) "Less Options" else "Change Setup",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expandable details for Rally selection, weather, and camera
            AnimatedVisibility(visible = showDetails) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "CHANGE RALLY COUNTRY",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RallyPresets.RALLIES.forEach { r ->
                            val isSel = r.id == activeSession.rallyId
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable {
                                    val fStage = r.stages.first()
                                    onUpdateSession(
                                        activeSession.copy(
                                            rallyId = r.id,
                                            rallyName = r.name,
                                            stageName = fStage.name,
                                            stageDistanceKm = fStage.distanceKm
                                        )
                                    )
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(text = r.flag, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = r.country,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "WEATHER CONDITION",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RallyPresets.WEATHER_OPTIONS.forEach { (name, icon) ->
                            val isSel = activeSession.weather == name
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.clickable {
                                    onUpdateSession(activeSession.copy(weather = name))
                                }
                            ) {
                                Text(
                                    text = "$icon $name",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = ScratchpadMonoFontFamily,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "CAMERA VIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RallyPresets.CAMERA_OPTIONS.forEach { (name, icon) ->
                            val isSel = activeSession.camera == name
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.clickable {
                                    onUpdateSession(activeSession.copy(camera = name))
                                }
                            ) {
                                Text(
                                    text = "$icon $name",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = ScratchpadMonoFontFamily,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "CAR SPEC",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ScratchpadMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val allCars = RallyPresets.CAR_CLASSES.flatMap { it.second }
                        allCars.take(6).forEach { car ->
                            val isSel = activeSession.carName == car
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable {
                                    onUpdateSession(activeSession.copy(carName = car))
                                }
                            ) {
                                Text(
                                    text = car,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = ScratchpadMonoFontFamily,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Big Save Button (Keeps stage settings locked for next run)
            Button(
                onClick = {
                    if (calculatedMillis > 0) {
                        onRecordTime(
                            activeSession.rallyId,
                            activeSession.rallyName,
                            activeSession.stageName,
                            activeSession.stageDistanceKm,
                            calculatedMillis,
                            activeSession.weather,
                            activeSession.camera,
                            activeSession.carName,
                            activeSession.carClass,
                            activeSession.transmission,
                            activeSession.notes
                        )
                        minutesText = ""
                        secondsText = ""
                        millisText = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("quick_save_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = calculatedMillis > 0
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RECORD TIME (${TimeFormatter.formatMillis(calculatedMillis)})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
