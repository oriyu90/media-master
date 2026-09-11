package com.example.viewer

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class TextCharsetReaderTest {

    @Test
    fun `detects UTF-8 BOM and skips it`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hello".toByteArray(Charsets.UTF_8)
        val (charset, offset) = TextCharsetReader.detectCharset(bytes)
        assertEquals(Charsets.UTF_8, charset)
        assertEquals(3, offset)
    }

    @Test
    fun `detects UTF-16LE BOM`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(Charsets.UTF_16LE)
        val (charset, offset) = TextCharsetReader.detectCharset(bytes)
        assertEquals(Charsets.UTF_16LE, charset)
        assertEquals(2, offset)
    }

    @Test
    fun `plain ASCII is detected as UTF-8 with no offset`() {
        val bytes = "hello world".toByteArray(Charsets.US_ASCII)
        val (charset, offset) = TextCharsetReader.detectCharset(bytes)
        assertEquals(Charsets.UTF_8, charset)
        assertEquals(0, offset)
    }

    @Test
    fun `valid UTF-8 Japanese text is detected as UTF-8`() {
        val bytes = "こんにちは世界".toByteArray(Charsets.UTF_8)
        val (charset, _) = TextCharsetReader.detectCharset(bytes)
        assertEquals(Charsets.UTF_8, charset)
    }

    @Test
    fun `Shift_JIS Japanese text is detected as Shift_JIS, not mis-decoded as UTF-8`() {
        val shiftJis = Charset.forName("Shift_JIS")
        val original = "こんにちは"
        val bytes = original.toByteArray(shiftJis)
        val (charset, offset) = TextCharsetReader.detectCharset(bytes)
        assertEquals(shiftJis, charset)
        // Round-tripping through the detected charset must reproduce the original text
        // (this is the "no mojibake" guarantee the viewer depends on).
        assertEquals(original, String(bytes, offset, bytes.size - offset, charset))
    }
}
