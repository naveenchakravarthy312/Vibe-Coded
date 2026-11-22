package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.ScratchpadUiState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CanvasDark
import com.example.ui.theme.ScratchpadMonoFontFamily
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

@Composable
fun ScratchpadEditor(
    uiState: ScratchpadUiState,
    onTextChanged: (TextFieldValue) -> Unit,
    onZoomFont: (Int) -> Unit,
    onExitZenMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var statDisplayMode by remember { mutableIntStateOf(0) } // 0: words, 1: words + chars, 2: all details
    var zoomAccumulator by remember { mutableFloatStateOf(1f) }

    val editorFontFamily = ScratchpadMonoFontFamily
    val currentFontSize = uiState.fontSizeSp.sp
    val currentLineHeight = (uiState.fontSizeSp * 1.5).sp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasDark)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    zoomAccumulator = 1f
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoom = event.calculateZoom()
                            if (abs(zoom - 1f) > 0.01f) {
                                zoomAccumulator *= zoom
                                if (zoomAccumulator > 1.25f) {
                                    onZoomFont(1)
                                    zoomAccumulator = 1f
                                } else if (zoomAccumulator < 0.8f) {
                                    onZoomFont(-1)
                                    zoomAccumulator = 1f
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        // Full-bleed editor with 24dp internal padding (aligned with Linux script PADDING_PX = 24)
        BasicTextField(
            value = uiState.textFieldValue,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .imePadding()
                .testTag("scratchpad_editor_input"),
            textStyle = TextStyle(
                color = TextPrimary,
                fontFamily = editorFontFamily,
                fontSize = currentFontSize,
                lineHeight = currentLineHeight,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.2.sp
            ),
            cursorBrush = SolidColor(AccentCyan),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (uiState.textFieldValue.text.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.scratchpad_placeholder),
                            style = TextStyle(
                                color = TextMuted,
                                fontFamily = editorFontFamily,
                                fontSize = currentFontSize,
                                lineHeight = currentLineHeight
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Floating subtle word count indicator in bottom-right corner
        // Exactly replicating the Linux Ephemeral Pad floating word count label
        val statText = when (statDisplayMode) {
            0 -> if (uiState.wordCount == 1) "1 word" else "${uiState.wordCount} words"
            1 -> "${uiState.wordCount} words • ${uiState.charCount} chars"
            else -> "${uiState.wordCount}w • ${uiState.charCount}c • ${uiState.lineCount}L • ${uiState.fontSizeSp}sp"
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 12.dp)
                .imePadding()
        ) {
            Surface(
                color = CanvasDark.copy(alpha = 0.88f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        statDisplayMode = (statDisplayMode + 1) % 3
                    }
                    .testTag("word_count_indicator")
            ) {
                Text(
                    text = statText,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = ScratchpadMonoFontFamily,
                        fontSize = (uiState.fontSizeSp - 4).coerceIn(9, 12).sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Zen Mode exit button
        AnimatedVisibility(
            visible = uiState.isZenMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onExitZenMode,
                modifier = Modifier
                    .background(SurfaceVariantDark.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .testTag("exit_zen_mode_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FullscreenExit,
                    contentDescription = "Exit Zen Mode",
                    tint = TextSecondary
                )
            }
        }
    }
}
