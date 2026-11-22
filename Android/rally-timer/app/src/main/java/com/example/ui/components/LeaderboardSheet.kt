package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.StageTimeRecord
import com.example.data.model.Rally
import com.example.data.model.StagePreset
import com.example.data.model.TimeFormatter
import com.example.data.model.RallyPresets
import com.example.ui.theme.ScratchpadMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardSheet(
    rally: Rally,
    stage: StagePreset,
    records: List<StageTimeRecord>,
    onDismiss: () -> Unit,
    onLogTimeForStage: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stageRecords = records
        .filter { it.rallyId == rally.id && it.stageName == stage.name }
        .sortedBy { it.timeMillis }

    val fastestTime = stageRecords.firstOrNull()?.timeMillis ?: 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = rally.flag, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stage.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val stageTypeSuffix = when {
                            stage.isEpicStage -> " · ★ Epic Stage"
                            stage.isSuperSpecialStage -> " · ⚡ Super Special"
                            else -> ""
                        }
                        Text(
                            text = "${rally.name} · ${stage.distanceKm} km · ${stage.surfaceDetail}$stageTypeSuffix",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ScratchpadMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close Sheet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (stageRecords.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No runs recorded for this stage yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = ScratchpadMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "STAGE LEADERBOARD (${stageRecords.size} RUNS)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ScratchpadMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(stageRecords) { index, rec ->
                        val rank = index + 1
                        val delta = if (index == 0) 0L else rec.timeMillis - fastestTime

                        val medalColor = when (rank) {
                            1 -> Color(0xFFFFD700) // Gold
                            2 -> Color(0xFFC0C0C0) // Silver
                            3 -> Color(0xFFCD7F32) // Bronze
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        val weatherIcon = RallyPresets.WEATHER_OPTIONS.firstOrNull { it.first == rec.weather }?.second ?: "🌤️"
                        val cameraIcon = RallyPresets.CAMERA_OPTIONS.firstOrNull { it.first == rec.camera }?.second ?: "📹"

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (rank == 1) 1.5.dp else 1.dp,
                                color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rank & Medal
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (rank <= 3) medalColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (rank == 1) {
                                                Icon(
                                                    Icons.Default.EmojiEvents,
                                                    contentDescription = null,
                                                    tint = medalColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "#$rank",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = ScratchpadMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = medalColor
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Text(
                                            text = TimeFormatter.formatMillis(rec.timeMillis),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = ScratchpadMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$weatherIcon ${rec.weather} · $cameraIcon ${rec.camera}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = ScratchpadMonoFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Delta & Car info
                                Column(horizontalAlignment = Alignment.End) {
                                    if (rank == 1) {
                                        Text(
                                            text = "LEADER",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = ScratchpadMonoFontFamily,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = TimeFormatter.formatDelta(delta),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = ScratchpadMonoFontFamily,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        text = rec.carName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = ScratchpadMonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    onDismiss()
                    onLogTimeForStage()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LOG TIME FOR ${stage.name.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ScratchpadMonoFontFamily
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
