package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.terminal.AnsiTerminalParser
import com.example.ui.theme.ScratchpadMonoFontFamily
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun TerminalCanvas(
    rawTextFlow: StateFlow<String>,
    fontSizeSp: Int,
    autoScrollEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val rawText by rawTextFlow.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val isSystemDark = isSystemInDarkTheme()

    // Determine if terminal should treat as dark background
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // Parse ANSI codes to styled AnnotatedString
    val annotatedOutput = remember(rawText, isDark) {
        AnsiTerminalParser.parse(rawText, isDark)
    }

    // Auto-scroll logic when new output arrives
    var previousLength by remember { mutableIntStateOf(0) }
    LaunchedEffect(rawText.length) {
        if (autoScrollEnabled && rawText.length > previousLength) {
            // Only auto-scroll if near the bottom or at start
            val isNearBottom = (scrollState.maxValue - scrollState.value) < 150
            if (isNearBottom || previousLength == 0) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
        previousLength = rawText.length
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            scrollState.maxValue > 300 && (scrollState.maxValue - scrollState.value) > 200
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("terminal_text_canvas")
            ) {
                Text(
                    text = annotatedOutput,
                    fontFamily = ScratchpadMonoFontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp + 6).sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                )

                // Bottom padding inside scroll view
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Floating Scroll-to-Bottom Button
        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp).testTag("scroll_to_bottom_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
