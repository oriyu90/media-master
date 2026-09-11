package com.example.viewer.latex

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KatexHtmlTest {

    @Test
    fun `references only the bundled offline assets`() {
        val html = KatexHtml.build("x^2", displayMode = true, textColorHex = "#000000")
        assertTrue(html.contains("katex.min.css"))
        assertTrue(html.contains("katex.min.js"))
        assertTrue(html.contains("auto-render.min.js"))
        assertFalse(html.contains("http://"))
    }

    @Test
    fun `embeds the displayMode flag correctly`() {
        val displayHtml = KatexHtml.build("x", displayMode = true, textColorHex = "#000000")
        val inlineHtml = KatexHtml.build("x", displayMode = false, textColorHex = "#000000")
        assertTrue(displayHtml.contains("displayMode: true"))
        assertTrue(inlineHtml.contains("displayMode: false"))
    }

    @Test
    fun `leaves trust and throwOnError at their safe defaults`() {
        val html = KatexHtml.build("\\includegraphics{x}", displayMode = false, textColorHex = "#000000")
        assertTrue(html.contains("trust: false"))
        assertTrue(html.contains("throwOnError: false"))
    }

    @Test
    fun `a literal closing script tag in the formula cannot break out of our script block`() {
        val malicious = "</script><script>alert(1)</script>"
        val html = KatexHtml.build(malicious, displayMode = false, textColorHex = "#000000")
        assertFalse(html.contains("</script><script>alert(1)"))
        // The escaped form must still be present (content preserved, just neutralised).
        assertTrue(html.contains("<\\/script>"))
    }

    @Test
    fun `never throws regardless of input`() {
        val inputs = listOf("", "\\", " ", "a".repeat(10_000), "$$$$", "😀", "\"quoted\"", "\n\t")
        for (input in inputs) {
            KatexHtml.build(input, displayMode = true, textColorHex = "#123456")
        }
    }
}
