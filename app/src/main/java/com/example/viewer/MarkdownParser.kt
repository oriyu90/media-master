package com.example.viewer

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser

/**
 * Pure-Kotlin Markdown block/inline model, decoupled from Compose so it can be
 * unit-tested on the JVM without Robolectric.
 */
sealed class MdBlock {
    data class Heading(val level: Int, val inline: List<MdInline>) : MdBlock()
    data class Paragraph(val inline: List<MdInline>) : MdBlock()
    data class CodeBlock(val code: String, val language: String?) : MdBlock()
    data class BulletListBlock(val items: List<List<MdBlock>>) : MdBlock()
    data class OrderedListBlock(val startNumber: Int, val items: List<List<MdBlock>>) : MdBlock()
    data class Quote(val blocks: List<MdBlock>) : MdBlock()
    data object Rule : MdBlock()
    data class ImageBlock(val url: String, val alt: String) : MdBlock()
    /** GFM table: header + body rows as plain inline runs (v1.8.0 #2). */
    data class Table(val headers: List<List<MdInline>>, val rows: List<List<List<MdInline>>>) : MdBlock()
    /** A `$$...$$` display-math block, rendered full-width via KaTeX. */
    data class MathBlock(val latex: String) : MdBlock()
    /** A ```chart fenced block: lightweight native bar/line chart (v1.8.0 #2). */
    data class ChartBlock(val raw: String) : MdBlock()
    /** A ```mermaid fenced block: diagram source, fully drawn in As-Is WebView. */
    data class MermaidBlock(val code: String) : MdBlock()
}

sealed class MdInline {
    data class PlainText(val text: String) : MdInline()
    data class Bold(val children: List<MdInline>) : MdInline()
    data class Italic(val children: List<MdInline>) : MdInline()
    data class InlineCode(val text: String) : MdInline()
    data class LinkText(val url: String, val children: List<MdInline>) : MdInline()
    data object LineBreak : MdInline()
    /** A `$...$` inline-math span, rendered via KaTeX. */
    data class Math(val latex: String) : MdInline()
}

/** Parses CommonMark source into [MdBlock] trees. Never throws — falls back to an empty document. */
object MarkdownParser {

    private val tablesExt = listOf(TablesExtension.create())
    private val parser: Parser = Parser.builder().extensions(tablesExt).build()

    fun parse(markdown: String): List<MdBlock> {
        val extraction = MathExtractor.extract(markdown)
        val document = runCatching { parser.parse(extraction.text) }.getOrNull() ?: return emptyList()
        val blocks = convertChildren(document)
        return if (extraction.blockMath.isEmpty() && extraction.inlineMath.isEmpty()) {
            blocks
        } else {
            substituteMath(blocks, extraction)
        }
    }

    private fun substituteMath(blocks: List<MdBlock>, extraction: MathExtractor.Extraction): List<MdBlock> =
        blocks.map { block ->
            when (block) {
                is MdBlock.Paragraph -> {
                    val onlyText = (block.inline.singleOrNull() as? MdInline.PlainText)?.text?.trim()
                    val blockMatch = onlyText?.let { MathExtractor.blockPlaceholderRegex.find(it) }
                    val latex = blockMatch?.groupValues?.get(1)?.toIntOrNull()?.let { extraction.blockMath.getOrNull(it) }
                    if (latex != null) MdBlock.MathBlock(latex) else MdBlock.Paragraph(substituteInlineMath(block.inline, extraction))
                }
                is MdBlock.Heading -> MdBlock.Heading(block.level, substituteInlineMath(block.inline, extraction))
                is MdBlock.BulletListBlock -> MdBlock.BulletListBlock(block.items.map { substituteMath(it, extraction) })
                is MdBlock.OrderedListBlock -> MdBlock.OrderedListBlock(block.startNumber, block.items.map { substituteMath(it, extraction) })
                is MdBlock.Quote -> MdBlock.Quote(substituteMath(block.blocks, extraction))
                is MdBlock.Table -> MdBlock.Table(
                    block.headers.map { substituteInlineMath(it, extraction) },
                    block.rows.map { row -> row.map { substituteInlineMath(it, extraction) } },
                )
                else -> block
            }
        }

    private fun substituteInlineMath(inline: List<MdInline>, extraction: MathExtractor.Extraction): List<MdInline> =
        inline.flatMap { node ->
            when (node) {
                is MdInline.PlainText -> splitPlainTextMath(node.text, extraction)
                is MdInline.Bold -> listOf(MdInline.Bold(substituteInlineMath(node.children, extraction)))
                is MdInline.Italic -> listOf(MdInline.Italic(substituteInlineMath(node.children, extraction)))
                is MdInline.LinkText -> listOf(MdInline.LinkText(node.url, substituteInlineMath(node.children, extraction)))
                else -> listOf(node)
            }
        }

    private fun splitPlainTextMath(text: String, extraction: MathExtractor.Extraction): List<MdInline> {
        if (!MathExtractor.inlinePlaceholderRegex.containsMatchIn(text)) return listOf(MdInline.PlainText(text))
        val result = mutableListOf<MdInline>()
        var lastEnd = 0
        for (match in MathExtractor.inlinePlaceholderRegex.findAll(text)) {
            if (match.range.first > lastEnd) result.add(MdInline.PlainText(text.substring(lastEnd, match.range.first)))
            val latex = match.groupValues[1].toIntOrNull()?.let { extraction.inlineMath.getOrNull(it) }
            result.add(if (latex != null) MdInline.Math(latex) else MdInline.PlainText(match.value))
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) result.add(MdInline.PlainText(text.substring(lastEnd)))
        return result
    }

    private fun convertChildren(parent: Node): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        var node = parent.firstChild
        while (node != null) {
            runCatching { convertBlock(node!!) }.getOrNull()?.let(blocks::add)
            node = node.next
        }
        return blocks
    }

    private fun convertBlock(node: Node): MdBlock? = when (node) {
        is Heading -> MdBlock.Heading(node.level, convertInline(node))
        is Paragraph -> {
            val onlyChild = node.firstChild
            if (onlyChild is Image && onlyChild.next == null) {
                MdBlock.ImageBlock(onlyChild.destination.orEmpty(), extractPlainText(onlyChild))
            } else {
                MdBlock.Paragraph(convertInline(node))
            }
        }
        is FencedCodeBlock -> {
            val lang = node.info?.trim()?.lowercase()
            val literal = node.literal.orEmpty()
            when (lang) {
                "chart" -> MdBlock.ChartBlock(literal)
                "mermaid" -> MdBlock.MermaidBlock(literal)
                else -> MdBlock.CodeBlock(literal, node.info?.takeIf { it.isNotBlank() })
            }
        }
        is TableBlock -> convertTable(node)
        is IndentedCodeBlock -> MdBlock.CodeBlock(node.literal.orEmpty(), null)
        is BulletList -> MdBlock.BulletListBlock(convertListItems(node))
        is OrderedList -> MdBlock.OrderedListBlock(node.markerStartNumber, convertListItems(node))
        is BlockQuote -> MdBlock.Quote(convertChildren(node))
        is ThematicBreak -> MdBlock.Rule
        else -> null // Unrecognised block (e.g. HTML block): omit rather than mis-render.
    }

    private fun convertTable(node: TableBlock): MdBlock.Table {
        var headers: List<List<MdInline>> = emptyList()
        val rows = mutableListOf<List<List<MdInline>>>()
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is TableHead -> {
                    val headRow = child.firstChild as? TableRow
                    if (headRow != null) headers = convertTableRow(headRow)
                }
                is TableBody -> {
                    var rowNode = child.firstChild
                    while (rowNode != null) {
                        if (rowNode is TableRow) rows.add(convertTableRow(rowNode))
                        rowNode = rowNode.next
                    }
                }
            }
            child = child.next
        }
        return MdBlock.Table(headers, rows)
    }

    private fun convertTableRow(row: TableRow): List<List<MdInline>> {
        val cells = mutableListOf<List<MdInline>>()
        var cell = row.firstChild
        while (cell != null) {
            if (cell is TableCell) cells.add(convertInline(cell))
            cell = cell.next
        }
        return cells
    }

    private fun convertListItems(listNode: Node): List<List<MdBlock>> {
        val items = mutableListOf<List<MdBlock>>()
        var item = listNode.firstChild
        while (item != null) {
            if (item is ListItem) items.add(convertChildren(item))
            item = item.next
        }
        return items
    }

    private fun convertInline(parent: Node): List<MdInline> {
        val result = mutableListOf<MdInline>()
        var node = parent.firstChild
        while (node != null) {
            runCatching { convertInlineNode(node!!) }.getOrNull()?.let(result::add)
            node = node.next
        }
        return result
    }

    private fun convertInlineNode(node: Node): MdInline = when (node) {
        is Text -> MdInline.PlainText(node.literal.orEmpty())
        is StrongEmphasis -> MdInline.Bold(convertInline(node))
        is Emphasis -> MdInline.Italic(convertInline(node))
        is Code -> MdInline.InlineCode(node.literal.orEmpty())
        is Link -> MdInline.LinkText(node.destination.orEmpty(), convertInline(node))
        is SoftLineBreak, is HardLineBreak -> MdInline.LineBreak
        is Image -> MdInline.PlainText("[${extractPlainText(node)}]")
        else -> MdInline.PlainText(extractPlainText(node))
    }

    private fun extractPlainText(node: Node): String {
        val sb = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            if (child is Text) sb.append(child.literal)
            child = child.next
        }
        return sb.toString()
    }
}
