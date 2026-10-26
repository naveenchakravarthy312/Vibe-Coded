package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScratchpadUiState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CanvasDark
import com.example.ui.theme.ScratchpadMonoFontFamily
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FindReplaceBar(
    uiState: ScratchpadUiState,
    onFindQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Find row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = CanvasDark,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = uiState.findQuery,
                        onValueChange = onFindQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("find_query_input"),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontFamily = ScratchpadMonoFontFamily,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(AccentCyan),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (uiState.findQuery.isEmpty()) {
                                Text("Find…", color = TextMuted, fontSize = 13.sp, fontFamily = ScratchpadMonoFontFamily)
                            }
                            innerTextField()
                        }
                    )
                    if (uiState.findQuery.isNotEmpty()) {
                        Text(
                            text = if (uiState.findMatchesCount > 0) {
                                "${uiState.currentMatchIndex}/${uiState.findMatchesCount}"
                            } else {
                                "0/0"
                            },
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = ScratchpadMonoFontFamily,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onFindPrevious,
                enabled = uiState.findMatchesCount > 0,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("find_previous_button")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Find Previous",
                    tint = if (uiState.findMatchesCount > 0) TextPrimary else TextMuted
                )
            }

            IconButton(
                onClick = onFindNext,
                enabled = uiState.findMatchesCount > 0,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("find_next_button")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Find Next",
                    tint = if (uiState.findMatchesCount > 0) TextPrimary else TextMuted
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("close_find_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Find",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Replace row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = CanvasDark,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = uiState.replaceQuery,
                        onValueChange = onReplaceQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("replace_query_input"),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontFamily = ScratchpadMonoFontFamily,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(AccentCyan),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (uiState.replaceQuery.isEmpty()) {
                                Text("Replace with…", color = TextMuted, fontSize = 13.sp, fontFamily = ScratchpadMonoFontFamily)
                            }
                            innerTextField()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            TextButton(
                onClick = onReplaceCurrent,
                enabled = uiState.findMatchesCount > 0,
                colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan),
                modifier = Modifier.testTag("replace_button")
            ) {
                Text("Replace", fontSize = 12.sp)
            }

            TextButton(
                onClick = onReplaceAll,
                enabled = uiState.findMatchesCount > 0,
                colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan),
                modifier = Modifier.testTag("replace_all_button")
            ) {
                Text("All", fontSize = 12.sp)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = BorderSubtle, modifier = Modifier.padding(top = 8.dp))
    }
}
