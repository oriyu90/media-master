package com.example.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {

    @Test
    fun `parses simple comma separated rows`() {
        val table = CsvParser.parse("name,age\nAlice,30\nBob,25\n")
        assertEquals(listOf("name", "age"), table.headers)
        assertEquals(listOf(listOf("Alice", "30"), listOf("Bob", "25")), table.rows)
        assertEquals(',', table.delimiter)
    }

    @Test
    fun `sniffs tab delimiter when it dominates`() {
        val table = CsvParser.parse("name\tage\nAlice\t30\n")
        assertEquals('\t', table.delimiter)
        assertEquals(listOf("Alice", "30"), table.rows.single())
    }

    @Test
    fun `handles quoted fields with embedded commas and escaped quotes`() {
        val table = CsvParser.parse("name,note\n\"Doe, John\",\"He said \"\"hi\"\"\"\n")
        val row = table.rows.single()
        assertEquals("Doe, John", row[0])
        assertEquals("He said \"hi\"", row[1])
    }

    @Test
    fun `handles quoted field containing a newline`() {
        val table = CsvParser.parse("a,b\n\"line1\nline2\",x\n")
        val row = table.rows.single()
        assertEquals("line1\nline2", row[0])
        assertEquals("x", row[1])
    }

    @Test
    fun `truncates body rows beyond maxRows but keeps header`() {
        val text = buildString {
            append("h1,h2\n")
            repeat(20) { append("$it,v$it\n") }
        }
        val table = CsvParser.parse(text, maxRows = 5)
        assertEquals(5, table.rows.size)
        assertTrue(table.rowsTruncated)
    }

    @Test
    fun `does not report truncation when under the limit`() {
        val table = CsvParser.parse("h\n1\n2\n", maxRows = 10)
        assertFalse(table.rowsTruncated)
    }

    @Test
    fun `empty input yields no headers or rows without throwing`() {
        val table = CsvParser.parse("")
        assertTrue(table.headers.isEmpty())
        assertTrue(table.rows.isEmpty())
    }
}
