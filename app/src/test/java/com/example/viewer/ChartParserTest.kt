package com.example.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartParserTest {

    @Test
    fun `csv lines parse to entries`() {
        val data = ChartParser.parse("Jan,120\nFeb,80\nMar,200")
        assertNotNull(data)
        assertEquals(3, data!!.entries.size)
        assertEquals("Jan", data.entries[0].label)
        assertEquals(120f, data.entries[0].value)
    }

    @Test
    fun `colon separated lines parse`() {
        val data = ChartParser.parse("Apples: 30\nOranges: 45")
        assertNotNull(data)
        assertEquals(2, data!!.entries.size)
    }

    @Test
    fun `json array parses`() {
        val data = ChartParser.parse("""[{"label":"A","value":10},{"label":"B","value":20}]""")
        assertNotNull(data)
        assertEquals(2, data!!.entries.size)
        assertEquals(20f, data.entries[1].value)
    }

    @Test
    fun `empty and garbage return null`() {
        assertNull(ChartParser.parse(""))
        assertNull(ChartParser.parse("hello world no numbers here xyz"))
    }

    @Test
    fun `entry cap prevents OOM`() {
        val raw = (1..100).joinToString("\n") { "L$it,$it" }
        val data = ChartParser.parse(raw)
        assertNotNull(data)
        assertTrue(data!!.entries.size <= 24)
    }
}
