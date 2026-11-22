package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.text.style.TextOverflow
import com.example.ssh.ConnectionMetrics
import com.example.terminal.TerminalProgress
import com.example.ui.theme.ScratchpadMonoFontFamily

@Composable
fun TerminalAccessoryBar(
    commandInput: String,
    onCommandInputChanged: (String) -> Unit,
    onSendCommand: () -> Unit,
    onSendKey: (String) -> Unit,
    ctrlActive: Boolean,
    metrics: ConnectionMetrics,
    hapticEnabled: Boolean,
    isAmoled: Boolean = false,
    onToggleAmoled: () -> Unit = {},
    activeProgress: TerminalProgress? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    fun triggerHaptic() {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Hairline top border
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

        // Live Command Progress Bar Banner
        AnimatedVisibility(visible = activeProgress != null) {
            activeProgress?.let { progress ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("terminal_progress_banner"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = progress.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(progress.percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.percentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Metrics & Quiet info strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "PTY: ${metrics.activePty}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (metrics.pingMs >= 0) {
                    Text(
                        text = "RTT: ${metrics.pingMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "↓ ${formatBytes(metrics.bytesReceived)}  ↑ ${formatBytes(metrics.bytesSent)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Horizontal Key Accessory Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AMOLED Quick Toggle Pill
            TerminalKeyChip(
                label = "AMOLED",
                isActive = isAmoled,
                onClick = {
                    triggerHaptic()
                    onToggleAmoled()
                },
                testTag = "key_amoled_toggle"
            )

            // CTRL key pill
            TerminalKeyChip(
                label = "CTRL",
                isActive = ctrlActive,
                onClick = {
                    triggerHaptic()
                    onSendKey("CTRL")
                },
                testTag = "key_ctrl"
            )

            // Common terminal interrupt & control keys
            TerminalKeyChip(
                label = "CTRL+C",
                onClick = {
                    triggerHaptic()
                    onSendKey("CTRL+C")
                },
                testTag = "key_ctrl_c"
            )

            TerminalKeyChip(
                label = "ESC",
                onClick = {
                    triggerHaptic()
                    onSendKey("ESC")
                },
                testTag = "key_esc"
            )

            TerminalKeyChip(
                label = "TAB",
                onClick = {
                    triggerHaptic()
                    onSendKey("TAB")
                },
                testTag = "key_tab"
            )

            // Arrow keys
            TerminalKeyChip(label = "↑", onClick = { triggerHaptic(); onSendKey("UP") }, testTag = "key_up")
            TerminalKeyChip(label = "↓", onClick = { triggerHaptic(); onSendKey("DOWN") }, testTag = "key_down")
            TerminalKeyChip(label = "←", onClick = { triggerHaptic(); onSendKey("LEFT") }, testTag = "key_left")
            TerminalKeyChip(label = "→", onClick = { triggerHaptic(); onSendKey("RIGHT") }, testTag = "key_right")

            // Shell syntax characters
            listOf("|", "~", "/", "-", "&", ";", "$", "sudo ").forEach { sym ->
                TerminalKeyChip(
                    label = sym.trim(),
                    onClick = {
                        triggerHaptic()
                        onCommandInputChanged(commandInput + sym)
                    },
                    testTag = "key_${sym.trim()}"
                )
            }

            TerminalKeyChip(
                label = "CTRL+D",
                onClick = {
                    triggerHaptic()
                    onSendKey("CTRL+D")
                },
                testTag = "key_ctrl_d"
            )

            TerminalKeyChip(
                label = "CTRL+L",
                onClick = {
                    triggerHaptic()
                    onSendKey("CTRL+L")
                },
                testTag = "key_ctrl_l"
            )
        }

        // Command Input Field Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = onCommandInputChanged,
                placeholder = {
                    Text(
                        text = "Enter terminal command...",
                        fontFamily = ScratchpadMonoFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = ScratchpadMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        triggerHaptic()
                        onSendCommand()
                    }
                )
            )

            FilledIconButton(
                onClick = {
                    triggerHaptic()
                    onSendCommand()
                },
                shape = RoundedCornerShape(8.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(46.dp)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TerminalKeyChip(
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    testTag: String = ""
) {
    val bg = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val border = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = ScratchpadMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
