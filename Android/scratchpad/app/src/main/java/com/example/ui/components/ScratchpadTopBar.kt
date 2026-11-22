package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScratchpadUiState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CanvasDark
import com.example.ui.theme.ScratchpadMonoFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ScratchpadTopBar(
    uiState: ScratchpadUiState,
    onOpenSavedNotes: () -> Unit,
    onSaveNote: () -> Unit,
    onSaveToFile: () -> Unit,
    onOpenFile: () -> Unit,
    onNewScratchpad: () -> Unit,
    onShareText: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenFontSettings: () -> Unit,
    onClearScratchpad: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CanvasDark)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Open saved notes drawer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onOpenSavedNotes() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("saved_notes_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Saved Notes Library",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.activeNoteTitle.ifBlank { "Scratchpad" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (uiState.isModified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentCyan, CircleShape)
                            )
                        }
                    }
                    Text(
                        text = "${uiState.savedNotes.size} saved • ${uiState.wordCount} words",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = ScratchpadMonoFontFamily
                        ),
                        color = TextMuted
                    )
                }
            }

            // Right Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // New Scratchpad
                IconButton(
                    onClick = onNewScratchpad,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("new_scratchpad_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Scratchpad",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Quick Save
                IconButton(
                    onClick = onSaveNote,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("save_note_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save Note",
                        tint = if (uiState.isModified) AccentCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Overflow Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("overflow_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save to File (.txt)...", color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onSaveToFile()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = AccentCyan)
                            },
                            modifier = Modifier.testTag("menu_save_to_file")
                        )

                        DropdownMenuItem(
                            text = { Text("Open File (.txt / .md)...", color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onOpenFile()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FileUpload, contentDescription = null, tint = AccentCyan)
                            },
                            modifier = Modifier.testTag("menu_open_file")
                        )

                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = { Text("Share Note", color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onShareText()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondary)
                            },
                            modifier = Modifier.testTag("menu_share")
                        )

                        DropdownMenuItem(
                            text = { Text("Copy All Text", color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onCopyAll()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary)
                            },
                            modifier = Modifier.testTag("menu_copy_all")
                        )

                        DropdownMenuItem(
                            text = { Text("Theme & Typography", color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onOpenFontSettings()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FormatSize, contentDescription = null, tint = TextSecondary)
                            },
                            modifier = Modifier.testTag("menu_font_settings")
                        )

                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = { Text("Clear / Wipe Scratchpad", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onClearScratchpad()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            modifier = Modifier.testTag("menu_clear_scratchpad")
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = BorderSubtle)
    }
}
