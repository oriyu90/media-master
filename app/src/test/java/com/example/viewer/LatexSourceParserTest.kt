package com.example.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexSourceParserTest {

    @Test
    fun `plain text with no math is a single segment`() {
        val segments = LatexSourceParser.parse("\\documentclass{article}\n\\begin{document}\nHello\n\\end{document}")
        assertEquals(1, segments.size)
        assertTrue(segments.single() is TexSegment.PlainText)
    }

    @Test
    fun `dollar inline math is extracted between text segments`() {
        val segments = LatexSourceParser.parse("Let \$x\$ be a number.")
        assertEquals(3, segments.size)
        assertEquals(TexSegment.Math("x", false), segments[1])
    }

    @Test
    fun `bracket display math is block mode`() {
        val segments = LatexSourceParser.parse("Before\n\\[ E = mc^2 \\]\nAfter")
        val math = segments.filterIsInstance<TexSegment.Math>().single()
        assertEquals("E = mc^2", math.latex)
        assertTrue(math.displayMode)
    }

    @Test
    fun `paren inline math is inline mode`() {
        val segments = LatexSourceParser.parse("Note \\(a + b\\) here.")
        val math = segments.filterIsInstance<TexSegment.Math>().single()
        assertEquals("a + b", math.latex)
        assertTrue(!math.displayMode)
    }

    @Test
    fun `equation environment is block mode and its own delimiters are not double-matched`() {
        val segments = LatexSourceParser.parse("\\begin{equation}\n  y = mx + b\n\\end{equation}")
        val mathSegments = segments.filterIsInstance<TexSegment.Math>()
        assertEquals(1, mathSegments.size)
        assertEquals("y = mx + b", mathSegments.single().latex)
        assertTrue(mathSegments.single().displayMode)
    }

    @Test
    fun `starred align environment is recognised`() {
        val segments = LatexSourceParser.parse("\\begin{align*}\na &= b \\\\\nc &= d\n\\end{align*}")
        val math = segments.filterIsInstance<TexSegment.Math>().single()
        assertTrue(math.latex.contains("a &= b"))
    }

    @Test
    fun `multiple math regions preserve surrounding text order`() {
        val segments = LatexSourceParser.parse("A \$x\$ B \$y\$ C")
        val texts = segments.filterIsInstance<TexSegment.PlainText>().map { it.text }
        assertEquals(listOf("A ", " B ", " C"), texts)
    }

    @Test
    fun `empty input yields no segments`() {
        assertTrue(LatexSourceParser.parse("").isEmpty())
    }
}
