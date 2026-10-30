package com.example.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalBuffer(private val maxLines: Int = 1500) {

    private val _rawTextFlow = MutableStateFlow("")
    val rawTextFlow: StateFlow<String> = _rawTextFlow.asStateFlow()

    private val builder = StringBuilder()

    @Synchronized
    fun append(chunk: String) {
        // Handle clear screen ANSI code \u001B[2J
        if (chunk.contains("\u001B[2J")) {
            val parts = chunk.split("\u001B[2J")
            builder.clear()
            builder.append(parts.last())
        } else {
            // Normalize CRLF to LF, handle bare CR if used as carriage return without LF
            var processed = chunk
            // If text contains \r not followed by \n (e.g. progress bar updating in place)
            if (processed.contains("\r") && !processed.contains("\r\n")) {
                val lines = processed.split("\r")
                processed = lines.lastOrNull() ?: ""
            } else {
                processed = processed.replace("\r\n", "\n")
            }

            builder.append(processed)

            // Trim if buffer exceeds maxLines
            val current = builder.toString()
            val lineCount = current.count { it == '\n' }
            if (lineCount > maxLines) {
                val excess = lineCount - maxLines
                var cutIndex = 0
                var found = 0
                for (i in current.indices) {
                    if (current[i] == '\n') {
                        found++
                        if (found >= excess) {
                            cutIndex = i + 1
                            break
                        }
                    }
                }
                builder.delete(0, cutIndex)
            }
        }

        _rawTextFlow.value = builder.toString()
    }

    @Synchronized
    fun clear() {
        builder.clear()
        _rawTextFlow.value = ""
    }

    @Synchronized
    fun getFullText(): String = builder.toString()
}
