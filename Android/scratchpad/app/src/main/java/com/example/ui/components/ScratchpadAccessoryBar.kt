package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CanvasDark
import com.example.ui.theme.ScratchpadMonoFontFamily
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ScratchpadAccessoryBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInsertTab: () -> Unit,
    onInsertTimestamp: () -> Unit,
    onInsertChecklist: () -> Unit,
    onInsertBullet: () -> Unit,
    onToggleFind: () -> Unit,
    onToggleZenMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .navigationBarsPadding()
            .imePadding()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = BorderSubtle)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Undo
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("accessory_undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) TextPrimary else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Redo
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("accessory_redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) TextPrimary else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Tab (4 spaces)
            Surface(
                onClick = onInsertTab,
                color = CanvasDark,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .testTag("accessory_tab_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardTab,
                        contentDescription = "Insert Tab (4 spaces)",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Tab",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = ScratchpadMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            // Timestamp
            IconButton(
                onClick = onInsertTimestamp,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("accessory_timestamp_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Insert Timestamp",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Checklist
            IconButton(
                onClick = onInsertChecklist,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("accessory_checklist_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckBox,
                    contentDescription = "Insert Checklist item",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Bullet list
            IconButton(
                onClick = onInsertBullet,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("accessory_bullet_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Insert Bullet item",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Find & Replace
            IconButton(
                onClick = onToggleFind,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("accessory_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Find & Replace",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Fullscreen Zen mode
            IconButton(
                onClick = onToggleZenMode,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("accessory_zen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Toggle Zen Mode",
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
