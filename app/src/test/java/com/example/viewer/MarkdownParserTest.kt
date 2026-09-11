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

    @Test
    fun `a standalone display-math block becomes a MathBlock`() {
        val blocks = MarkdownParser.parse("Intro\n\n\$\$\\sum_{i=1}^n i\$\$\n\nOutro")
        assertEquals(3, blocks.size)
        assertEquals(MdBlock.MathBlock("\\sum_{i=1}^n i"), blocks[1])
    }

    @Test
    fun `inline math inside a sentence becomes a Math inline node between text`() {
        val blocks = MarkdownParser.parse("The value \$x^2\$ is positive.")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertEquals(
            listOf(
                MdInline.PlainText("The value "),
                MdInline.Math("x^2"),
                MdInline.PlainText(" is positive."),
            ),
            paragraph.inline,
        )
    }

    @Test
    fun `inline math survives inside bold text`() {
        val blocks = MarkdownParser.parse("**important: \$x\$**")
        val paragraph = blocks.single() as MdBlock.Paragraph
        val bold = paragraph.inline.single() as MdInline.Bold
        assertTrue(bold.children.any { it is MdInline.Math })
    }

    @Test
    fun `a document with no math is unaffected by the math pass`() {
        val blocks = MarkdownParser.parse("# Title\n\nJust text, no formulas.")
        assertTrue(blocks.none { it is MdBlock.MathBlock })
    }
}
