package com.example.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses a heading and a paragraph`() {
        val blocks = MarkdownParser.parse("# Title\n\nHello world.")
        assertEquals(2, blocks.size)
        val heading = blocks[0] as MdBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("Title", (heading.inline.single() as MdInline.PlainText).text)
        val paragraph = blocks[1] as MdBlock.Paragraph
        assertEquals("Hello world.", (paragraph.inline.single() as MdInline.PlainText).text)
    }

    @Test
    fun `parses bold and italic emphasis`() {
        val blocks = MarkdownParser.parse("**bold** and *italic*")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertTrue(paragraph.inline.any { it is MdInline.Bold })
        assertTrue(paragraph.inline.any { it is MdInline.Italic })
    }

    @Test
    fun `parses fenced code blocks with language info`() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = 1\n```")
        val code = blocks.single() as MdBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("val x = 1"))
    }

    @Test
    fun `parses bullet lists`() {
        val blocks = MarkdownParser.parse("- one\n- two\n")
        val list = blocks.single() as MdBlock.BulletListBlock
        assertEquals(2, list.items.size)
    }

    @Test
    fun `parses ordered lists with a start number`() {
        val blocks = MarkdownParser.parse("3. three\n4. four\n")
        val list = blocks.single() as MdBlock.OrderedListBlock
        assertEquals(3, list.startNumber)
        assertEquals(2, list.items.size)
    }

    @Test
    fun `malformed input never throws`() {
        val blocks = MarkdownParser.parse("### unterminated **bold\n\n> quote\n\n---")
        // Should parse to *something* without throwing; exact shape isn't asserted.
        assertTrue(blocks.isNotEmpty())
    }

    @Test
    fun `empty input yields an empty block list`() {
        assertTrue(MarkdownParser.parse("").isEmpty())
    }
}
