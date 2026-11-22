package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ExportImportTab {
    EXPORT,
    IMPORT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportSheet(
    sheetState: SheetState,
    initialTab: ExportImportTab = ExportImportTab.EXPORT,
    exportJsonString: String,
    onDismiss: () -> Unit,
    onImportJson: (jsonString: String, replaceAll: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var pastedJson by remember { mutableStateOf("") }
    var replaceAllOnImport by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }

    val todayStr = remember {
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val defaultBackupFileName = "habits_backup_$todayStr.json"

    // SAF Document Creators and Openers
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(exportJsonString.toByteArray(Charsets.UTF_8))
                }
                statusMessage = "Export saved to: $defaultBackupFileName"
                isErrorStatus = false
                Toast.makeText(context, "Backup file saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                statusMessage = "Failed to write file: ${e.message}"
                isErrorStatus = true
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                    pastedJson = text
                    statusMessage = "Loaded file from storage (${text.length} bytes). Click Import below to apply."
                    isErrorStatus = false
                }
            } catch (e: Exception) {
                statusMessage = "Failed to read file: ${e.message}"
                isErrorStatus = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = SurfaceDark,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderSubtle)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UNIVERSAL BACKUP ENGINE",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_backup_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Tabs: Export / Import
            TabRow(
                selectedTabIndex = if (selectedTab == ExportImportTab.EXPORT) 0 else 1,
                containerColor = SurfaceVariantDark,
                contentColor = AccentCyan,
                indicator = { tabPositions ->
                    val index = if (selectedTab == ExportImportTab.EXPORT) 0 else 1
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[index]),
                        color = AccentCyan
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = selectedTab == ExportImportTab.EXPORT,
                    onClick = {
                        selectedTab = ExportImportTab.EXPORT
                        statusMessage = null
                    },
                    text = {
                        Text(
                            text = "EXPORT BACKUP",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedTab == ExportImportTab.EXPORT) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == ExportImportTab.EXPORT) AccentCyan else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_export")
                )
                Tab(
                    selected = selectedTab == ExportImportTab.IMPORT,
                    onClick = {
                        selectedTab = ExportImportTab.IMPORT
                        statusMessage = null
                    },
                    text = {
                        Text(
                            text = "IMPORT BACKUP",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedTab == ExportImportTab.IMPORT) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == ExportImportTab.IMPORT) AccentCyan else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_import")
                )
            }

            // Status notification if any
            statusMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isErrorStatus) ErrorRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f))
                        .border(1.dp, if (isErrorStatus) ErrorRed else SuccessGreen, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isErrorStatus) ErrorRed else SuccessGreen
                    )
                }
            }

            // TAB 1: EXPORT
            if (selectedTab == ExportImportTab.EXPORT) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Standard 2-space indented JSON format compatible across Linux, Windows, macOS, Android, and iOS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    // Action buttons: Save to File, Share, Copy
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                createDocumentLauncher.launch(defaultBackupFileName)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = OnAccentCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_save_backup_file")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save File",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, exportJsonString)
                                    putExtra(Intent.EXTRA_TITLE, defaultBackupFileName)
                                    type = "application/json"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Habits Backup")
                                context.startActivity(shareIntent)
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_share_backup")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Habits JSON", exportJsonString)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                                statusMessage = "Backup JSON copied to clipboard."
                                isErrorStatus = false
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .size(44.dp)
                                .testTag("btn_copy_json")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy JSON",
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // JSON Live Preview
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "JSON PREVIEW ($defaultBackupFileName)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = "${exportJsonString.length} chars",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = exportJsonString,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                // TAB 2: IMPORT
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Import a universal VibeHabits backup JSON file from your device, or paste the JSON text directly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    // Choose File Button
                    Button(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceVariantDark,
                            contentColor = AccentCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .testTag("btn_open_backup_file")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Select JSON File from Device",
                            style = MaterialTheme.typography.titleSmall,
                            color = AccentCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Paste JSON Text Area
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OR PASTE BACKUP JSON",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            if (pastedJson.isNotEmpty()) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentCyan,
                                    modifier = Modifier.clickable { pastedJson = "" }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            if (pastedJson.isEmpty()) {
                                Text(
                                    text = "Paste your JSON backup payload here...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            BasicTextField(
                                value = pastedJson,
                                onValueChange = { pastedJson = it },
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                cursorBrush = SolidColor(AccentCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .testTag("input_import_json")
                            )
                        }
                    }

                    // Replace All Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replace all existing habits",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (replaceAllOnImport) ErrorRed else TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (replaceAllOnImport) "Warning: Existing habits will be cleared." else "Merge: Non-destructive import.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Switch(
                            checked = replaceAllOnImport,
                            onCheckedChange = { replaceAllOnImport = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ErrorRed,
                                checkedTrackColor = ErrorRed.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = CanvasDark
                            ),
                            modifier = Modifier.testTag("switch_replace_all")
                        )
                    }

                    // Execute Import Button
                    Button(
                        onClick = {
                            if (pastedJson.isNotBlank()) {
                                onImportJson(pastedJson, replaceAllOnImport)
                            }
                        },
                        enabled = pastedJson.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (replaceAllOnImport) ErrorRed else AccentCyan,
                            contentColor = if (replaceAllOnImport) OnErrorRed else OnAccentCyan,
                            disabledContainerColor = SurfaceVariantDark,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_execute_import")
                    ) {
                        Text(
                            text = if (replaceAllOnImport) "RESTORE & REPLACE ALL" else "IMPORT & MERGE HABITS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
