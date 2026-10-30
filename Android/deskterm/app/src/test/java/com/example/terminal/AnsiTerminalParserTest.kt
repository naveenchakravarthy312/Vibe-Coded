package com.example.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiTerminalParserTest {

    @Test
    fun testPlainStringParsing() {
        val result = AnsiTerminalParser.parse("Hello Desktop", isDark = true)
        assertEquals("Hello Desktop", result.text)
    }

    @Test
    fun testAnsiColorStrippingAndText() {
        val raw = "\u001B[32mDesktop Connected\u001B[0m"
        val result = AnsiTerminalParser.parse(raw, isDark = true)
        assertEquals("Desktop Connected", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }
}
