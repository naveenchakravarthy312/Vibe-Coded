package com.example.terminal

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.theme.ErrorRedDark
import com.example.ui.theme.TextPrimaryDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun testOscWindowTitleSequencesAreStripped() {
        val belTerminated = "\u001B]0;alex@desktop: ~/work\u0007alex@desktop:~$ "
        assertEquals("alex@desktop:~$ ", AnsiTerminalParser.parse(belTerminated, isDark = true).text)

        val stTerminated = "\u001B]2;DeskTerm Window\u001B\\ready"
        assertEquals("ready", AnsiTerminalParser.parse(stTerminated, isDark = true).text)
    }

    @Test
    fun testPrivateCsiAndCharsetSequencesAreStripped() {
        val raw = "\u001B[?2004h\u001B[?25l\u001B(Bprompt$ \u001B[?2004l"
        val result = AnsiTerminalParser.parse(raw, isDark = true)
        assertEquals("prompt$ ", result.text)
    }

    @Test
    fun testBoldAndUnderlineRendering() {
        val raw = "\u001B[1;4mImportant\u001B[22;24mNormal"
        val result = AnsiTerminalParser.parse(raw, isDark = true)
        assertEquals("ImportantNormal", result.text)
        val firstSpan = result.spanStyles[0].item
        val secondSpan = result.spanStyles[1].item
        assertEquals(FontWeight.Bold, firstSpan.fontWeight)
        assertEquals(TextDecoration.Underline, firstSpan.textDecoration)
        assertEquals(FontWeight.Normal, secondSpan.fontWeight)
        assertEquals(null, secondSpan.textDecoration)
    }

    @Test
    fun testBackground256ColorDoesNotCorruptForeground() {
        val raw = "\u001B[48;5;31mTagged\u001B[49m"
        val result = AnsiTerminalParser.parse(raw, isDark = true)
        assertEquals("Tagged", result.text)
        val span = result.spanStyles[0].item
        assertEquals(TextPrimaryDark, span.color)
        assertNotEquals(ErrorRedDark, span.color)
    }
}
