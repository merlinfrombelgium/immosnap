package com.immosnap.ocr

import org.junit.Assert.*
import org.junit.Test

class SignInfoParserTest {

    @Test
    fun `extracts phone number from sign text`() {
        val text = "Century 21\nTe Koop\n03 123 45 67\nRef: AB-1234"
        val result = SignInfoParser.parse(text)
        assertEquals("03 123 45 67", result.phoneNumber)
    }

    @Test
    fun `extracts reference number`() {
        val text = "Immobilien Schmidt\nA Vendre\nRef: 12345\n0476 12 34 56"
        val result = SignInfoParser.parse(text)
        assertEquals("12345", result.referenceNumber)
    }

    @Test
    fun `extracts agency name as first non-keyword line`() {
        val text = "ERA Vastgoed\nTe Koop\n09 222 33 44"
        val result = SignInfoParser.parse(text)
        assertEquals("ERA Vastgoed", result.agencyName)
    }

    @Test
    fun `handles text with no recognizable fields`() {
        val text = "some random text"
        val result = SignInfoParser.parse(text)
        assertNull(result.phoneNumber)
        assertNull(result.referenceNumber)
        assertEquals("some random text", result.rawText)
    }
}
