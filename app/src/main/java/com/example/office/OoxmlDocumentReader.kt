package com.example.office

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** One text run inside a paragraph, with the minimal formatting the viewer renders. */
data class DocRun(val text: String, val bold: Boolean, val italic: Boolean)

sealed class DocBlock {
    data class Paragraph(val runs: List<DocRun>, val headingLevel: Int?) : DocBlock()
    data class ImageRef(val mediaPath: String) : DocBlock()
}

data class OoxmlDocument(val blocks: List<DocBlock>, val imageBytesByPath: Map<String, ByteArray>)

/**
 * Minimal, dependency-free `.docx` (Office Open XML WordprocessingML) reader.
 *
 * Reads the zip package directly (`java.util.zip` + the platform XmlPullParser)
 * instead of a full office library: extracts paragraph text with bold/italic/
 * heading-level plus inline images, which is enough to display a document
 * cleanly without needing pixel-perfect page layout. Every failure mode
 * (corrupt zip, malformed XML, huge file) is caught and degrades to a null/
 * partial result rather than crashing the caller.
 */
object OoxmlDocumentReader {

    private const val MAX_ENTRY_BYTES = 10 * 1024 * 1024 // per zip entry (guards decompression bombs)
    private const val MAX_IMAGES = 30
    private const val MAX_TOTAL_TEXT_CHARS = 2_000_000

    fun read(context: Context, uri: Uri): OoxmlDocument? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { readZip(it) }
    }.getOrNull()

    private fun readZip(input: InputStream): OoxmlDocument {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory &&
                    (name == "word/document.xml" || name == "word/_rels/document.xml.rels" || name.startsWith("word/media/"))
                ) {
                    entries[name] = readCapped(zip, MAX_ENTRY_BYTES)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val relMap = entries["word/_rels/document.xml.rels"]?.let(::parseRelationships).orEmpty()
        val documentXml = entries["word/document.xml"] ?: return OoxmlDocument(emptyList(), emptyMap())
        val blocks = runCatching { parseDocumentXml(documentXml, relMap) }.getOrDefault(emptyList())
        val images = entries.filterKeys { it.startsWith("word/media/") }
            .entries.take(MAX_IMAGES)
            .associate { it.key to it.value }
        return OoxmlDocument(blocks, images)
    }

    private fun parseDocumentXml(bytes: ByteArray, relMap: Map<String, String>): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        val parser = newParser(bytes)

        var runs = mutableListOf<DocRun>()
        var headingLevel: Int? = null
        var bold = false
        var italic = false
        var textBuffer: StringBuilder? = null
        var totalChars = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "p" -> {
                        runs = mutableListOf()
                        headingLevel = null
                    }
                    "pStyle" -> headingLevel = parser.attrByLocalName("val")?.let(::parseHeadingLevel)
                    "r" -> {
                        bold = false
                        italic = false
                    }
                    "b" -> bold = parser.attrByLocalName("val").let { it == null || it == "true" || it == "1" }
                    "i" -> italic = parser.attrByLocalName("val").let { it == null || it == "true" || it == "1" }
                    "t" -> textBuffer = StringBuilder()
                    "tab" -> runs.add(DocRun("\t", bold, italic))
                    "br" -> runs.add(DocRun("\n", bold, italic))
                    "blip" -> {
                        val target = parser.attrByLocalName("embed")?.let { relMap[it] }
                        if (target != null) blocks.add(DocBlock.ImageRef(normalizeMediaPath(target)))
                    }
                }
                XmlPullParser.TEXT -> {
                    if (textBuffer != null && totalChars < MAX_TOTAL_TEXT_CHARS) {
                        val text = parser.text.orEmpty()
                        textBuffer.append(text)
                        totalChars += text.length
                    }
                }
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "t" -> {
                        textBuffer?.let { runs.add(DocRun(it.toString(), bold, italic)) }
                        textBuffer = null
                    }
                    "p" -> if (runs.isNotEmpty() || headingLevel != null) {
                        blocks.add(DocBlock.Paragraph(runs, headingLevel))
                    }
                }
            }
            if (totalChars >= MAX_TOTAL_TEXT_CHARS) break
            event = parser.next()
        }
        return blocks
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val parser = newParser(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localName(parser.name) == "Relationship") {
                val id = parser.attrByLocalName("Id")
                val target = parser.attrByLocalName("Target")
                if (id != null && target != null) map[id] = target
            }
            event = parser.next()
        }
        return map
    }

    private fun parseHeadingLevel(styleVal: String): Int? {
        if (styleVal.equals("Title", ignoreCase = true)) return 1
        val match = Regex("(?i)heading\\s*([1-9])").find(styleVal) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /** Relationship targets are relative to the referencing part's own directory (`word/`). */
    private fun normalizeMediaPath(target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val segments = mutableListOf("word")
        for (seg in target.split("/")) {
            when (seg) {
                "", "." -> {}
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                else -> segments.add(seg)
            }
        }
        return segments.joinToString("/")
    }

    private fun newParser(bytes: ByteArray): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), null)
        }

    private fun localName(qualifiedName: String): String = qualifiedName.substringAfterLast(':')

    private fun XmlPullParser.attrByLocalName(local: String): String? {
        for (i in 0 until attributeCount) {
            if (localName(getAttributeName(i)) == local) return getAttributeValue(i)
        }
        return null
    }

    private fun readCapped(stream: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val toRead = minOf(buffer.size, limit - total)
            val n = stream.read(buffer, 0, toRead)
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
