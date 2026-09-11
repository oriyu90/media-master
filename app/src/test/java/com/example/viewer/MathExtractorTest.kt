package com.example.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathExtractorTest {

    @Test
    fun `extracts a single inline expression`() {
        val result = MathExtractor.extract("The value \$x^2 + 1\$ is always positive.")
        assertEquals(listOf("x^2 + 1"), result.inlineMath)
        assertTrue(result.blockMath.isEmpty())
        assertTrue(MathExtractor.inlinePlaceholderRegex.containsMatchIn(result.text))
        assertFalse(result.text.contains('$'))
    }

    @Test
    fun `extracts a block expression`() {
        val result = MathExtractor.extract("Consider:\n\n\$\$\\int_0^1 x\\,dx = \\frac{1}{2}\$\$\n\nDone.")
        assertEquals(listOf("\\int_0^1 x\\,dx = \\frac{1}{2}"), result.blockMath)
        assertTrue(result.inlineMath.isEmpty())
    }

    @Test
    fun `does not treat an escaped dollar sign as math`() {
        val result = MathExtractor.extract("It costs \\\$5 today.")
        assertTrue(result.inlineMath.isEmpty())
        assertTrue(result.blockMath.isEmpty())
        assertTrue(result.text.contains("\\\$5"))
    }

    @Test
    fun `extracts multiple inline expressions in order`() {
        val result = MathExtractor.extract("We have \$a\$ and \$b\$ and \$c\$.")
        assertEquals(listOf("a", "b", "c"), result.inlineMath)
    }

    @Test
    fun `block math is consumed before the inline pass sees its dollars`() {
        val result = MathExtractor.extract("\$\$a + b\$\$")
        assertEquals(listOf("a + b"), result.blockMath)
        assertTrue(result.inlineMath.isEmpty())
    }

    @Test
    fun `plain text with no math is left untouched`() {
        val result = MathExtractor.extract("Nothing special here.")
        assertEquals("Nothing special here.", result.text)
        assertTrue(result.blockMath.isEmpty())
        assertTrue(result.inlineMath.isEmpty())
    }
}
