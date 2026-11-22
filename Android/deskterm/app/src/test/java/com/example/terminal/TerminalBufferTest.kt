package com.example.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalBufferTest {

    @Test
    fun testCarriageReturnOverwritesCurrentLineInPlace() {
        val buffer = TerminalBuffer()
        buffer.append("[===>     ] 30%\r")
        assertEquals("[===>     ] 30%", buffer.getFullText())
        assertEquals(0.30f, buffer.progressFlow.value?.percentage ?: -1f, 0.01f)

        buffer.append("[======>  ] 60%\r")
        assertEquals("[======>  ] 60%", buffer.getFullText())
        assertEquals(0.60f, buffer.progressFlow.value?.percentage ?: -1f, 0.01f)

        buffer.append("[=========] 100%\nDone\nnaveen@hphouse:~$ ")
        assertEquals("[=========] 100%\nDone\nnaveen@hphouse:~$ ", buffer.getFullText())
        assertNull(buffer.progressFlow.value)
    }

    @Test
    fun testZshPromptSequencesFromScreenshotAreCleanlyProcessed() {
        val buffer = TerminalBuffer()
        // Simulate Zsh PROMPT_SP + OSC 2 + OSC 1 + 2-line prompt + DEC modes (?1h, =, ?2004h) split across packets
        buffer.append("\u001B[1m\u001B[7m%\u001B[27m\u001B[1m\u001B[0m                    \r \r\u001B]2;naveen@hph")
        buffer.append("ouse:~\u0007\u001B]1;~\u0007\r\u001B[0m\u001B[27m\u001B[24m\u001B[J\u001B[32m┌─naveen@hphouse ~ \u001B[0m\r\n")
        buffer.append("└─$ \u001B[?1h\u001B=\u001B[?2004h")

        assertEquals("┌─naveen@hphouse ~ \n└─$ ", buffer.getPlainText())
    }

    @Test
    fun testBackspaceAndCursorMovementProgressBars() {
        val buffer = TerminalBuffer()
        buffer.append("Working: 10%")
        buffer.append("\b\b\b25%")
        assertEquals("Working: 25%", buffer.getPlainText())
        assertEquals(0.25f, buffer.progressFlow.value?.percentage ?: -1f, 0.01f)

        // CHA (ESC[10G) to jump directly to column 10 (1-based) and overwrite
        buffer.append("\u001B[10G80%\u001B[K")
        assertEquals("Working: 80%", buffer.getPlainText())
        assertEquals(0.80f, buffer.progressFlow.value?.percentage ?: -1f, 0.01f)
    }

    @Test
    fun testMultiLineDockerStyleProgressBarWithCursorUp() {
        val buffer = TerminalBuffer()
        buffer.append("layer1: Downloading [==>    ] 30%\n")
        buffer.append("layer2: Downloading [====>  ] 60%\n")
        // Move up 2 lines, update layer1, move down to layer2, update layer2
        buffer.append("\u001B[2A\rlayer1: Pull complete\u001B[K\n")
        buffer.append("\rlayer2: Downloading [=====> ] 85%\u001B[K\n")

        assertEquals(
            "layer1: Pull complete\nlayer2: Downloading [=====> ] 85%\n",
            buffer.getPlainText()
        )
        assertNotNull(buffer.progressFlow.value)
        assertEquals(0.85f, buffer.progressFlow.value?.percentage ?: -1f, 0.01f)
    }

    @Test
    fun testUnicodeBlockProgressBarDetection() {
        val buffer = TerminalBuffer()
        buffer.append("██████░░░░ 60%\r")
        assertNotNull(buffer.progressFlow.value)
        assertEquals(0.60f, buffer.progressFlow.value?.percentage ?: -1f, 0.01f)
    }

    @Test
    fun testDfTableDoesNotFalseTriggerProgressBar() {
        val buffer = TerminalBuffer()
        buffer.append("Filesystem Size Used Avail Use% Mounted on\n/dev/sda1 100G 45G 55G 45% /\n")
        assertNull(buffer.progressFlow.value)
    }

    @Test
    fun testCarriageReturnWithinSingleChunk() {
        val buffer = TerminalBuffer()
        buffer.append("prefix\nfoo\rbar")
        assertEquals("prefix\nbar", buffer.getFullText())
    }

    @Test
    fun testSplitCrLfAcrossChunksPreservesLine() {
        val buffer = TerminalBuffer()
        buffer.append("Line 1\r")
        buffer.append("\nLine 2\r\n")
        assertEquals("Line 1\nLine 2\n", buffer.getFullText())
    }

    @Test
    fun testClearScreenSequenceResetsBufferAndProcessesRemainder() {
        val buffer = TerminalBuffer()
        buffer.append("Old line 1\nOld line 2\n")
        buffer.append("ignored\u001B[2JFresh\rUpdated\r\n")
        assertEquals("Updated\n", buffer.getFullText())
    }

    @Test
    fun testMaxLinesBoundsBufferSize() {
        val buffer = TerminalBuffer(maxLines = 3)
        buffer.append("1\n2\n3\n4\n5\n")
        assertEquals("3\n4\n5\n", buffer.getFullText())
    }
}
