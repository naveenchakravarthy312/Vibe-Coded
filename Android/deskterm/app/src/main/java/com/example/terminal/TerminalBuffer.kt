package com.example.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TerminalProgress(
    val percentage: Float,
    val label: String
)

class TerminalBuffer(private val maxLines: Int = 1500) {

    private data class SgrState(
        val fg: String = "",
        val bg: String = "",
        val bold: Boolean = false,
        val underline: Boolean = false
    ) {
        val isDefault: Boolean
            get() = fg.isEmpty() && bg.isEmpty() && !bold && !underline

        fun toEscapeSequence(): String {
            if (isDefault) return ""
            val parts = ArrayList<String>(4)
            if (bold) parts.add("1")
            if (underline) parts.add("4")
            if (fg.isNotEmpty()) parts.add(fg)
            if (bg.isNotEmpty()) parts.add(bg)
            return "\u001B[${parts.joinToString(";")}m"
        }
    }

    private data class Cell(
        val char: Char,
        val sgr: SgrState = SgrState()
    )

    private enum class ParserState {
        NORMAL,
        ESC,
        ESC_INTERMEDIATE,
        CSI,
        OSC,
        OSC_ESC
    }

    private val _rawTextFlow = MutableStateFlow("")
    val rawTextFlow: StateFlow<String> = _rawTextFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow<TerminalProgress?>(null)
    val progressFlow: StateFlow<TerminalProgress?> = _progressFlow.asStateFlow()

    private val lines = ArrayDeque<ArrayList<Cell>>()
    private var cursorRow = 0
    private var cursorCol = 0
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var pendingCrOverwrite = false
    private var currentLineInPlaceModified = false

    private var parserState = ParserState.NORMAL
    private val csiParams = StringBuilder()
    private var activeSgr = SgrState()

    init {
        lines.addLast(ArrayList())
    }

    @Synchronized
    fun append(chunk: String) {
        if (chunk.isEmpty()) return

        for (ch in chunk) {
            processChar(ch)
        }

        updateProgressDetection()
        _rawTextFlow.value = buildSnapshot()
    }

    private fun processChar(ch: Char) {
        when (parserState) {
            ParserState.NORMAL -> processNormalChar(ch)
            ParserState.ESC -> processEscChar(ch)
            ParserState.ESC_INTERMEDIATE -> {
                // Consume single parameter byte after ESC (, ESC ), ESC #, ESC %
                parserState = ParserState.NORMAL
            }
            ParserState.CSI -> processCsiChar(ch)
            ParserState.OSC -> {
                when (ch) {
                    '\u0007', '\u009C' -> parserState = ParserState.NORMAL
                    '\u001B' -> parserState = ParserState.OSC_ESC
                    '\r', '\n' -> {
                        parserState = ParserState.NORMAL
                        processNormalChar(ch)
                    }
                }
            }
            ParserState.OSC_ESC -> {
                if (ch == '\\') {
                    parserState = ParserState.NORMAL
                } else {
                    processEscChar(ch)
                }
            }
        }
    }

    private fun processNormalChar(ch: Char) {
        when (ch) {
            '\u001B' -> {
                parserState = ParserState.ESC
            }
            '\r' -> {
                cursorCol = 0
                pendingCrOverwrite = true
                currentLineInPlaceModified = true
            }
            '\n' -> {
                pendingCrOverwrite = false
                currentLineInPlaceModified = false
                cursorCol = 0
                cursorRow++
                ensureRowExists(cursorRow)
                trimExcessLines()
            }
            '\b' -> {
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
                if (cursorCol > 0) {
                    cursorCol--
                    val row = currentRowCells()
                    if (cursorCol == row.size - 1 && row[cursorCol].char == ' ' && row[cursorCol].sgr.isDefault) {
                        row.removeAt(cursorCol)
                    }
                }
            }
            '\t' -> {
                val nextTab = ((cursorCol / 8) + 1) * 8
                while (cursorCol < nextTab) {
                    writePrintableChar(' ')
                }
            }
            '\u0007', '\u000E', '\u000F' -> {
                // Ignore BEL, SO, SI
            }
            else -> {
                if (ch >= ' ') {
                    writePrintableChar(ch)
                }
            }
        }
    }

    private fun processEscChar(ch: Char) {
        when (ch) {
            '[' -> {
                csiParams.setLength(0)
                parserState = ParserState.CSI
            }
            ']', 'P', 'X', '^', '_' -> {
                parserState = ParserState.OSC
            }
            '(', ')', '*', '+', '-', '.', '/', '#', '%' -> {
                parserState = ParserState.ESC_INTERMEDIATE
            }
            '7' -> {
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
                parserState = ParserState.NORMAL
            }
            '8' -> {
                cursorRow = savedCursorRow.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                cursorCol = savedCursorCol.coerceAtLeast(0)
                pendingCrOverwrite = false
                parserState = ParserState.NORMAL
            }
            'c' -> {
                resetScreen()
                parserState = ParserState.NORMAL
            }
            else -> {
                // Includes '=', '>', 'M', 'D', 'E', 'H', 'N', 'O', 'Z'
                parserState = ParserState.NORMAL
            }
        }
    }

    private fun processCsiChar(ch: Char) {
        when {
            ch == '\u001B' -> {
                parserState = ParserState.ESC
            }
            ch == '\r' || ch == '\n' || ch == '\b' -> {
                processNormalChar(ch)
            }
            ch in '\u0030'..'\u003F' -> {
                // Parameter bytes: 0-9 ; : < = > ?
                csiParams.append(ch)
            }
            ch in '\u0020'..'\u002F' -> {
                // Intermediate bytes (space .. /)
                csiParams.append(ch)
            }
            ch in '\u0040'..'\u007E' -> {
                // Final byte executes CSI sequence
                handleCsiSequence(csiParams.toString(), ch)
                parserState = ParserState.NORMAL
            }
            else -> {
                // Ignore unexpected C0 controls inside CSI
            }
        }
    }

    private fun handleCsiSequence(rawParams: String, finalByte: Char) {
        val hasPrivatePrefix = rawParams.isNotEmpty() && rawParams[0] in "?><=!"
        val cleanParams = if (hasPrivatePrefix) rawParams.substring(1) else rawParams

        if (hasPrivatePrefix) {
            // Private DEC/xterm mode sequences like [?1h, [?2004h, [?25l — consume silently
            return
        }

        when (finalByte) {
            'm' -> applySgrParams(cleanParams)
            'A' -> { // CUU - Cursor Up
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                cursorRow = (cursorRow - n).coerceAtLeast(0)
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            'B', 'e' -> { // CUD - Cursor Down
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                cursorRow += n
                ensureRowExists(cursorRow)
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            'C', 'a' -> { // CUF - Cursor Forward
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                cursorCol += n
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            'D' -> { // CUB - Cursor Back
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                cursorCol = (cursorCol - n).coerceAtLeast(0)
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            'E' -> { // CNL - Cursor Next Line
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                cursorRow += n
                cursorCol = 0
                ensureRowExists(cursorRow)
                pendingCrOverwrite = false
            }
            'F' -> { // CPL - Cursor Previous Line
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                cursorRow = (cursorRow - n).coerceAtLeast(0)
                cursorCol = 0
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            'G', '`' -> { // CHA - Cursor Horizontal Absolute (1-based)
                val col1 = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                cursorCol = col1 - 1
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            'K' -> { // EL - Erase in Line
                val mode = parseFirstParam(cleanParams, 0)
                val row = currentRowCells()
                currentLineInPlaceModified = true
                when (mode) {
                    0 -> { // Erase from cursor to end of line
                        if (pendingCrOverwrite && cursorCol == 0) {
                            row.clear()
                            pendingCrOverwrite = false
                        } else if (cursorCol < row.size) {
                            row.subList(cursorCol, row.size).clear()
                        }
                    }
                    1 -> { // Erase from start of line to cursor
                        val end = minOf(cursorCol, row.size - 1)
                        for (i in 0..end) {
                            row[i] = Cell(' ', activeSgr)
                        }
                        pendingCrOverwrite = false
                    }
                    2 -> { // Erase entire line
                        row.clear()
                        pendingCrOverwrite = false
                    }
                }
            }
            'J' -> { // ED - Erase in Display
                val mode = parseFirstParam(cleanParams, 0)
                when (mode) {
                    0 -> { // Erase from cursor to end of screen
                        val row = currentRowCells()
                        if (cursorCol < row.size) {
                            row.subList(cursorCol, row.size).clear()
                        }
                        while (lines.size - 1 > cursorRow) {
                            lines.removeLast()
                        }
                        pendingCrOverwrite = false
                    }
                    2, 3 -> { // Clear entire screen
                        resetScreen()
                    }
                }
            }
            'P' -> { // DCH - Delete Character(s)
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                val row = currentRowCells()
                if (cursorCol < row.size) {
                    val end = minOf(cursorCol + n, row.size)
                    row.subList(cursorCol, end).clear()
                }
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            'X' -> { // ECH - Erase Character(s)
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                val row = currentRowCells()
                for (i in cursorCol until (cursorCol + n)) {
                    if (i < row.size) {
                        row[i] = Cell(' ', activeSgr)
                    }
                }
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            '@' -> { // ICH - Insert Blank Character(s)
                val n = parseFirstParam(cleanParams, 1).coerceAtLeast(1)
                val row = currentRowCells()
                val insertAt = cursorCol.coerceAtMost(row.size)
                for (i in 0 until n) {
                    row.add(insertAt, Cell(' ', activeSgr))
                }
                pendingCrOverwrite = false
                currentLineInPlaceModified = true
            }
            's' -> {
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
            }
            'u' -> {
                cursorRow = savedCursorRow.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                cursorCol = savedCursorCol.coerceAtLeast(0)
                pendingCrOverwrite = false
            }
        }
    }

    private fun parseFirstParam(params: String, default: Int): Int {
        if (params.isEmpty()) return default
        val firstToken = params.substringBefore(';').trim()
        return firstToken.toIntOrNull() ?: default
    }

    private fun applySgrParams(paramsStr: String) {
        if (paramsStr.isEmpty() || paramsStr == "0") {
            activeSgr = SgrState()
            return
        }
        val tokens = paramsStr.split(';').map { it.toIntOrNull() ?: 0 }
        var i = 0
        while (i < tokens.size) {
            when (val token = tokens[i]) {
                0 -> activeSgr = SgrState()
                1 -> activeSgr = activeSgr.copy(bold = true)
                2, 22 -> activeSgr = activeSgr.copy(bold = false)
                4 -> activeSgr = activeSgr.copy(underline = true)
                24 -> activeSgr = activeSgr.copy(underline = false)
                in 30..37, in 90..97 -> activeSgr = activeSgr.copy(fg = token.toString())
                38 -> {
                    if (i + 2 < tokens.size && tokens[i + 1] == 5) {
                        activeSgr = activeSgr.copy(fg = "38;5;${tokens[i + 2]}")
                        i += 2
                    } else if (i + 4 < tokens.size && tokens[i + 1] == 2) {
                        activeSgr = activeSgr.copy(fg = "38;2;${tokens[i + 2]};${tokens[i + 3]};${tokens[i + 4]}")
                        i += 4
                    }
                }
                39 -> activeSgr = activeSgr.copy(fg = "")
                in 40..47, in 100..107 -> activeSgr = activeSgr.copy(bg = token.toString())
                48 -> {
                    if (i + 2 < tokens.size && tokens[i + 1] == 5) {
                        activeSgr = activeSgr.copy(bg = "48;5;${tokens[i + 2]}")
                        i += 2
                    } else if (i + 4 < tokens.size && tokens[i + 1] == 2) {
                        activeSgr = activeSgr.copy(bg = "48;2;${tokens[i + 2]};${tokens[i + 3]};${tokens[i + 4]}")
                        i += 4
                    }
                }
                49 -> activeSgr = activeSgr.copy(bg = "")
            }
            i++
        }
    }

    private fun writePrintableChar(ch: Char) {
        val row = currentRowCells()
        if (pendingCrOverwrite && cursorCol == 0) {
            row.clear()
            pendingCrOverwrite = false
        }
        while (row.size < cursorCol) {
            row.add(Cell(' ', SgrState()))
        }
        val cell = Cell(ch, activeSgr)
        if (cursorCol < row.size) {
            row[cursorCol] = cell
        } else {
            row.add(cell)
        }
        cursorCol++
    }

    private fun ensureRowExists(targetRow: Int) {
        while (lines.size <= targetRow) {
            lines.addLast(ArrayList())
        }
    }

    private fun currentRowCells(): ArrayList<Cell> {
        ensureRowExists(cursorRow)
        return lines[cursorRow]
    }

    private fun trimExcessLines() {
        while (lines.size - 1 > maxLines) {
            lines.removeFirst()
            cursorRow = (cursorRow - 1).coerceAtLeast(0)
            savedCursorRow = (savedCursorRow - 1).coerceAtLeast(0)
        }
    }

    private fun resetScreen() {
        lines.clear()
        lines.addLast(ArrayList())
        cursorRow = 0
        cursorCol = 0
        savedCursorRow = 0
        savedCursorCol = 0
        pendingCrOverwrite = false
        currentLineInPlaceModified = false
        _progressFlow.value = null
    }

    private fun updateProgressDetection() {
        val activeRowText = rowToPlainText(currentRowCells()).trim()
        if (activeRowText.isNotEmpty()) {
            val detected = parseProgressLine(activeRowText, currentLineInPlaceModified)
            if (detected != null) {
                _progressFlow.value = detected
                return
            }
            if (isLikelyShellPrompt(activeRowText)) {
                _progressFlow.value = null
                return
            }
        }
        // Check previous row if command just emitted a trailing newline or multi-line update
        if (cursorRow > 0 && activeRowText.isEmpty()) {
            val prevRowText = rowToPlainText(lines[cursorRow - 1]).trim()
            val prevDetected = parseProgressLine(prevRowText, isInPlace = false)
            if (prevDetected != null && prevDetected.percentage < 1f) {
                _progressFlow.value = prevDetected
            } else {
                _progressFlow.value = null
            }
        }
    }

    @Synchronized
    fun clear() {
        resetScreen()
        parserState = ParserState.NORMAL
        csiParams.setLength(0)
        activeSgr = SgrState()
        _rawTextFlow.value = ""
    }

    @Synchronized
    fun getFullText(): String = buildSnapshot()

    @Synchronized
    fun getPlainText(): String {
        val sb = StringBuilder(lines.size * 64)
        for (i in lines.indices) {
            for (cell in lines[i]) {
                sb.append(cell.char)
            }
            if (i < lines.lastIndex) {
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun rowToPlainText(row: List<Cell>): String {
        if (row.isEmpty()) return ""
        val sb = StringBuilder(row.size)
        for (cell in row) {
            sb.append(cell.char)
        }
        return sb.toString()
    }

    private fun buildSnapshot(): String {
        val sb = StringBuilder(lines.size * 64)
        for (i in lines.indices) {
            val row = lines[i]
            var currentSgr = SgrState()
            for (cell in row) {
                if (cell.sgr != currentSgr) {
                    if (!currentSgr.isDefault) {
                        sb.append("\u001B[0m")
                    }
                    if (!cell.sgr.isDefault) {
                        sb.append(cell.sgr.toEscapeSequence())
                    }
                    currentSgr = cell.sgr
                }
                sb.append(cell.char)
            }
            if (!currentSgr.isDefault) {
                sb.append("\u001B[0m")
            }
            if (i < lines.lastIndex) {
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    companion object {
        private val PERCENT_REGEX = Regex("""(?<!\d)(\d{1,3}(?:\.\d+)?)\s*%""")
        private val BRACKET_BAR_REGEX = Regex("""\[([=#*+█▓▒━>-]{1,100})([.░ -]{0,100})]""")
        private val BLOCK_BAR_REGEX = Regex("""([█▓▒━]{2,100})([░ -]{1,100})""")
        private val PROGRESS_KEYWORDS_REGEX = Regex(
            """(?i)\b(downloading|uploading|receiving|resolving|compressing|unpacking|extracting|preparing|progress|working|copying|eta|mb/s|kb/s|mib/s|kib/s|b/s|it/s|s/it)\b"""
        )

        internal fun parseProgressLine(line: String, isInPlace: Boolean): TerminalProgress? {
            if (line.isEmpty() || line.length > 300) return null

            val percentMatch = PERCENT_REGEX.find(line)
            val bracketMatch = BRACKET_BAR_REGEX.find(line)
            val blockMatch = BLOCK_BAR_REGEX.find(line)
            val hasVisualBar = bracketMatch != null || blockMatch != null
            val hasKeywords = PROGRESS_KEYWORDS_REGEX.containsMatchIn(line)

            if (percentMatch != null) {
                val pctVal = percentMatch.groupValues[1].toFloatOrNull()
                if (pctVal != null && pctVal in 0f..100f && (isInPlace || hasVisualBar || hasKeywords)) {
                    return TerminalProgress(
                        percentage = (pctVal / 100f).coerceIn(0f, 1f),
                        label = line
                    )
                }
            }

            if (bracketMatch != null) {
                val filled = bracketMatch.groupValues[1].length
                val empty = bracketMatch.groupValues[2].length
                val total = filled + empty
                if (total >= 5 && (isInPlace || empty > 0)) {
                    return TerminalProgress(
                        percentage = (filled.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        label = line
                    )
                }
            }

            if (blockMatch != null) {
                val filled = blockMatch.groupValues[1].length
                val empty = blockMatch.groupValues[2].length
                val total = filled + empty
                if (total >= 5) {
                    return TerminalProgress(
                        percentage = (filled.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        label = line
                    )
                }
            }

            return null
        }

        private fun isLikelyShellPrompt(line: String): Boolean {
            return (line.endsWith("$") || line.endsWith("#") || line.endsWith("%") || line.endsWith("❯")) &&
                !PERCENT_REGEX.containsMatchIn(line)
        }
    }
}
