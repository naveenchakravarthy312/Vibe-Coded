package com.example.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlKeyTranslationTest {

    @Test
    fun testControlByteTranslation() {
        assertEquals(0x01.toByte(), TerminalViewModel.toControlByte('a'))
        assertEquals(0x03.toByte(), TerminalViewModel.toControlByte('c'))
        assertEquals(0x03.toByte(), TerminalViewModel.toControlByte('C'))
        assertEquals(0x04.toByte(), TerminalViewModel.toControlByte('d'))
        assertEquals(0x0C.toByte(), TerminalViewModel.toControlByte('l'))
        assertEquals(0x1A.toByte(), TerminalViewModel.toControlByte('z'))
        assertEquals(0x1B.toByte(), TerminalViewModel.toControlByte('['))
        assertNull(TerminalViewModel.toControlByte('5'))
    }
}
