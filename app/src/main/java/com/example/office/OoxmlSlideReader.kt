package com.example.office

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class SlideRun(val text: String, val bold: Boolean)
data class Slide(val paragraphs: List<List<SlideRun>>, val images: List<String>)
data class OoxmlSlideDeck(val slides: List<Slide>, val imageBytesByPath: Map<String, ByteArray>)

/**
 * Minimal, dependency-free `.pptx` (Office Open XML PresentationML) reader.
 *
 * Extracts each slide's text (with bold) and embedded images in reading
 * order. Slide order follows the numeric suffix of `ppt/slides/slideN.xml`
 * (PowerPoint's own naming), which matches presentation order for the large
 * majority of files; a deck that was heavily reordered without renumbering
 * is a known, documented limitation rather than a silent-corruption risk —
 * text is never dropped, only possibly shown in a different slide order.
 */
object OoxmlSlideReader {

    private const val MAX_ENTRY_BYTES = 10 * 1024 * 1024
    private const val MAX_IMAGES = 60
    private const val MAX_SLIDES = 500

    fun read(context: Context, uri: Uri): OoxmlSlideDeck? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { readZip(it) }
    }.getOrNull()

    private fun readZip(input: InputStream): OoxmlSlideDeck {
        val slideXmls = mutableMapOf<String, ByteArray>()
        val slideRels = mutableMapOf<String, ByteArray>()
        val media = mutableMapOf<String, ByteArray>()

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    entry.isDirectory -> {}
                    Regex("ppt/slides/slide\\d+\\.xml").matches(name) -> slideXmls[name] = readCapped(zip, MAX_ENTRY_BYTES)
                    Regex("ppt/slides/_rels/slide\\d+\\.xml\\.rels").matches(name) -> slideRels[name] = readCapped(zip, MAX_ENTRY_BYTES)
                    name.startsWith("ppt/media/") -> media[name] = readCapped(zip, MAX_ENTRY_BYTES)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val orderedNames = slideXmls.keys.sortedBy { name ->
            Regex("(\\d+)").findAll(name).lastOrNull()?.value?.toIntOrNull() ?: Int.MAX_VALUE
        }.take(MAX_SLIDES)

        val usedImagePaths = linkedSetOf<String>()
        val slides = orderedNames.map { slideName ->
            val relName = "ppt/slides/_rels/${slideName.substringAfterLast('/')}.rels"
            val relMap = slideRels[relName]?.let(::parseRelationships).orEmpty()
            val slide = runCatching { parseSlideXml(slideXmls.getValue(slideName), relMap) }
                .getOrDefault(Slide(emptyList(), emptyList()))
            slide.images.forEach(usedImagePaths::add)
            slide
        }

        val images = media.filterKeys { it in usedImagePaths }
            .entries.take(MAX_IMAGES)
            .associate { it.key to it.value }
        return OoxmlSlideDeck(slides, images)
    }

    private fun parseSlideXml(bytes: ByteArray, relMap: Map<String, String>): Slide {
        val paragraphs = mutableListOf<List<SlideRun>>()
        val images = mutableListOf<String>()
        var currentParagraph = mutableListOf<SlideRun>()
        var bold = false
        var textBuffer: StringBuilder? = null
        var inTextBody = false

        val parser = newParser(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "txBody" -> inTextBody = true
                    "p" -> if (inTextBody) currentParagraph = mutableListOf()
                    "rPr", "defRPr" -> bold = parser.attrByLocalName("b") == "1"
                    "t" -> if (inTextBody) textBuffer = StringBuilder()
                    "blip" -> {
                        val target = parser.attrByLocalName("embed")?.let { relMap[it] }
                        if (target != null) images.add(normalizeMediaPath(target))
                    }
                }
                XmlPullParser.TEXT -> textBuffer?.let { it.append(parser.text.orEmpty()) }
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "t" -> {
                        textBuffer?.let { currentParagraph.add(SlideRun(it.toString(), bold)) }
                        textBuffer = null
                    }
                    "p" -> if (inTextBody) {
                        if (currentParagraph.isNotEmpty()) paragraphs.add(currentParagraph)
                    }
                    "txBody" -> inTextBody = false
                }
            }
            event = parser.next()
        }
        return Slide(paragraphs, images)
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

    /** Relationship targets are relative to the referencing part's own directory (`ppt/slides/`). */
    private fun normalizeMediaPath(target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val segments = mutableListOf("ppt", "slides")
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
