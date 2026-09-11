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
}

sealed class MdInline {
    data class PlainText(val text: String) : MdInline()
    data class Bold(val children: List<MdInline>) : MdInline()
    data class Italic(val children: List<MdInline>) : MdInline()
    data class InlineCode(val text: String) : MdInline()
    data class LinkText(val url: String, val children: List<MdInline>) : MdInline()
    data object LineBreak : MdInline()
}

/** Parses CommonMark source into [MdBlock] trees. Never throws — falls back to an empty document. */
object MarkdownParser {

    private val parser: Parser = Parser.builder().build()

    fun parse(markdown: String): List<MdBlock> {
        val document = runCatching { parser.parse(markdown) }.getOrNull() ?: return emptyList()
        return convertChildren(document)
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
        is FencedCodeBlock -> MdBlock.CodeBlock(node.literal.orEmpty(), node.info?.takeIf { it.isNotBlank() })
        is IndentedCodeBlock -> MdBlock.CodeBlock(node.literal.orEmpty(), null)
        is BulletList -> MdBlock.BulletListBlock(convertListItems(node))
        is OrderedList -> MdBlock.OrderedListBlock(node.markerStartNumber, convertListItems(node))
        is BlockQuote -> MdBlock.Quote(convertChildren(node))
        is ThematicBreak -> MdBlock.Rule
        else -> null // Unrecognised block (e.g. HTML block): omit rather than mis-render.
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
