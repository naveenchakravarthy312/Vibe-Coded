package com.example.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.theme.*

object AnsiTerminalParser {

    private val ANSI_SEQUENCE_REGEX = Regex(
        "\u001B\\[([?><=!]?)([0-9;:]*)[ -/]*([@A-Za-z~`])" +
            "|" +
            "\u001B\\][^\u0007\u001B\u009C\r\n]*(?:\u0007|\u009C|\u001B\\\\)?" +
            "|" +
            "\u001B[()#%][0-9A-Za-z]" +
            "|" +
            "\u001B[=>78cDMEHMNOZ]" +
            "|" +
            "\u0007"
    )

    // Map ANSI standard color code (30-37, 90-97) to theme colors
    fun getForegroundColor(code: Int, isDark: Boolean): Color {
        return when (code) {
            30 -> if (isDark) TextMutedDark else TextMutedLight
            31 -> if (isDark) ErrorRedDark else ErrorRedLight
            32 -> if (isDark) SuccessGreenDark else SuccessGreenLight
            33 -> if (isDark) WarningYellowDark else WarningYellowLight
            34 -> if (isDark) AccentBlueDark else AccentBlueLight
            35 -> if (isDark) PurpleDark else PurpleLight
            36 -> if (isDark) AccentCyanDark else AccentCyanLight
            37 -> if (isDark) TextPrimaryDark else TextPrimaryLight
            90 -> if (isDark) TextSecondaryDark else TextSecondaryLight
            91 -> if (isDark) ErrorRedDark else ErrorRedLight
            92 -> if (isDark) SuccessGreenDark else SuccessGreenLight
            93 -> if (isDark) WarningYellowDark else WarningYellowLight
            94 -> if (isDark) AccentFrostDark else AccentFrostLight
            95 -> if (isDark) PurpleDark else PurpleLight
            96 -> if (isDark) AccentCyanDark else AccentCyanLight
            97 -> if (isDark) Color.White else TextPrimaryLight
            else -> if (isDark) TextPrimaryDark else TextPrimaryLight
        }
    }

    fun parse(rawText: String, isDark: Boolean): AnnotatedString {
        val defaultFg = if (isDark) TextPrimaryDark else TextPrimaryLight
        var currentFg = defaultFg
        var currentBg = Color.Unspecified
        var isBold = false
        var isUnderline = false

        return buildAnnotatedString {
            var lastIndex = 0

            for (match in ANSI_SEQUENCE_REGEX.findAll(rawText)) {
                // Append text preceding the escape sequence
                if (match.range.first > lastIndex) {
                    val chunk = rawText.substring(lastIndex, match.range.first)
                    val spanStyle = SpanStyle(
                        color = currentFg,
                        background = currentBg,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (isUnderline) TextDecoration.Underline else null
                    )
                    append(AnnotatedString(chunk, spanStyle))
                }

                val prefix = match.groupValues[1]
                val paramsStr = match.groupValues[2]
                val command = match.groupValues[3]

                if (prefix.isEmpty() && command == "m") { // SGR Graphic Rendition (color/style)
                    if (paramsStr.isEmpty() || paramsStr == "0") {
                        currentFg = defaultFg
                        currentBg = Color.Unspecified
                        isBold = false
                        isUnderline = false
                    } else {
                        val tokens = paramsStr.split(';').mapNotNull { it.toIntOrNull() }
                        var i = 0
                        while (i < tokens.size) {
                            when (val token = tokens[i]) {
                                0 -> {
                                    currentFg = defaultFg
                                    currentBg = Color.Unspecified
                                    isBold = false
                                    isUnderline = false
                                }
                                1 -> isBold = true
                                2, 22 -> isBold = false
                                4 -> isUnderline = true
                                24 -> isUnderline = false
                                in 30..37, in 90..97 -> {
                                    currentFg = getForegroundColor(token, isDark)
                                }
                                38 -> {
                                    // 256 colors: 38;5;<n> or 24-bit 38;2;<r>;<g>;<b>
                                    if (i + 2 < tokens.size && tokens[i + 1] == 5) {
                                        currentFg = parse256Color(tokens[i + 2], isDark)
                                        i += 2
                                    } else if (i + 4 < tokens.size && tokens[i + 1] == 2) {
                                        val r = tokens[i + 2].coerceIn(0, 255)
                                        val g = tokens[i + 3].coerceIn(0, 255)
                                        val b = tokens[i + 4].coerceIn(0, 255)
                                        currentFg = Color(r, g, b)
                                        i += 4
                                    }
                                }
                                39 -> currentFg = defaultFg
                                in 40..47 -> {
                                    currentBg = getForegroundColor(token - 10, isDark).copy(alpha = 0.25f)
                                }
                                in 100..107 -> {
                                    currentBg = getForegroundColor(token - 10, isDark).copy(alpha = 0.25f)
                                }
                                48 -> {
                                    if (i + 2 < tokens.size && tokens[i + 1] == 5) {
                                        currentBg = parse256Color(tokens[i + 2], isDark).copy(alpha = 0.25f)
                                        i += 2
                                    } else if (i + 4 < tokens.size && tokens[i + 1] == 2) {
                                        val r = tokens[i + 2].coerceIn(0, 255)
                                        val g = tokens[i + 3].coerceIn(0, 255)
                                        val b = tokens[i + 4].coerceIn(0, 255)
                                        currentBg = Color(r, g, b).copy(alpha = 0.25f)
                                        i += 4
                                    }
                                }
                                49 -> currentBg = Color.Unspecified
                            }
                            i++
                        }
                    }
                }
                lastIndex = match.range.last + 1
            }

            // Remaining text after last match
            if (lastIndex < rawText.length) {
                val remaining = rawText.substring(lastIndex)
                val spanStyle = SpanStyle(
                    color = currentFg,
                    background = currentBg,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (isUnderline) TextDecoration.Underline else null
                )
                append(AnnotatedString(remaining, spanStyle))
            }
        }
    }

    private fun parse256Color(index: Int, isDark: Boolean): Color {
        return when (index) {
            in 0..7 -> getForegroundColor(30 + index, isDark)
            in 8..15 -> getForegroundColor(90 + (index - 8), isDark)
            in 16..231 -> {
                val v = index - 16
                val r = (v / 36) * 51
                val g = ((v % 36) / 6) * 51
                val b = (v % 6) * 51
                Color(r, g, b)
            }
            in 232..255 -> {
                val gray = (index - 232) * 10 + 8
                Color(gray, gray, gray)
            }
            else -> if (isDark) TextPrimaryDark else TextPrimaryLight
        }
    }
}
