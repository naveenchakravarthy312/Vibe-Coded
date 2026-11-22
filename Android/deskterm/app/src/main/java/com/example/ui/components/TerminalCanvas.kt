package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.terminal.AnsiTerminalParser
import com.example.ui.theme.ScratchpadMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TerminalCanvas(
    rawTextFlow: StateFlow<String>,
    fontSizeSp: Int,
    autoScrollEnabled: Boolean,
    onTerminalSizeChanged: (cols: Int, rows: Int, widthPx: Int, heightPx: Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Determine if terminal should treat as dark background
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // Parse ANSI codes off the main thread, cancelling superseded parses during rapid stream bursts
    val annotatedOutput by produceState(initialValue = AnnotatedString(""), rawTextFlow, isDark) {
        rawTextFlow.collectLatest { raw ->
            value = withContext(Dispatchers.Default) {
                AnsiTerminalParser.parse(raw, isDark)
            }
        }
    }

    // Auto-scroll logic when new output arrives (use scrollTo to avoid cancelling animations on rapid output)
    var previousLength by remember { mutableIntStateOf(0) }
    LaunchedEffect(annotatedOutput, scrollState.maxValue) {
        if (autoScrollEnabled && annotatedOutput.isNotEmpty()) {
            val isNearBottom = (scrollState.maxValue - scrollState.value) < 150
            if (isNearBottom || previousLength == 0) {
                scrollState.scrollTo(scrollState.maxValue)
            }
        }
        previousLength = annotatedOutput.length
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            scrollState.maxValue > 300 && (scrollState.maxValue - scrollState.value) > 200
        }
    }

    val terminalTextStyle = remember(fontSizeSp) {
        TextStyle(
            fontFamily = ScratchpadMonoFontFamily,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 6).sp,
            fontWeight = FontWeight.Normal
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val horizontalPaddingPx = with(density) { 24.dp.roundToPx() }
        val verticalPaddingPx = with(density) { 16.dp.roundToPx() }
        val contentWidthPx = (constraints.maxWidth - horizontalPaddingPx).coerceAtLeast(1)
        val contentHeightPx = (constraints.maxHeight - verticalPaddingPx).coerceAtLeast(1)

        val charMetrics = remember(terminalTextStyle, density) {
            textMeasurer.measure("M", style = terminalTextStyle).size
        }

        val cols = if (charMetrics.width > 0) (contentWidthPx / charMetrics.width).coerceIn(20, 300) else 80
        val rows = if (charMetrics.height > 0) (contentHeightPx / charMetrics.height).coerceIn(8, 200) else 24

        LaunchedEffect(cols, rows, contentWidthPx, contentHeightPx) {
            onTerminalSizeChanged(cols, rows, contentWidthPx, contentHeightPx)
        }

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
                    style = terminalTextStyle,
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
