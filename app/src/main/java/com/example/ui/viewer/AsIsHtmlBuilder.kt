package com.example.ui.viewer

import com.example.office.DocBlock
import com.example.office.OoxmlDocument
import com.example.office.OoxmlSlideDeck
import com.example.viewer.MdBlock
import com.example.viewer.MarkdownParser
import com.example.viewer.TexSegment
import com.example.viewer.LatexSourceParser
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * v1.8.0 (#4): HTML builders for the top-bar Show-as-is single-WebView mode.
 * Offline-safe: only appassets asset paths (KaTeX) and data: images.
 */
object AsIsHtmlBuilder {
    const val VIRTUAL_ORIGIN = "https://appassets.androidplatform.net"

    private val mdParser: Parser by lazy {
        Parser.builder().extensions(listOf(TablesExtension.create())).build()
    }
    private val mdHtml: HtmlRenderer by lazy {
        HtmlRenderer.builder().extensions(listOf(TablesExtension.create())).build()
    }

    fun markdownToHtml(raw: String, dark: Boolean): String {
        val blocks = runCatching { MarkdownParser.parse(raw) }.getOrDefault(emptyList())
        val sb = StringBuilder()
        sb.append("<div class=\"md\">")
        for (b in blocks) {
            when (b) {
                is MdBlock.Heading -> {
                    val lvl = b.level.coerceIn(1, 6)
                    sb.append("<h").append(lvl).append(">")
                    sb.append(escapeForHtml(b.inline.toText()))
                    sb.append("</h").append(lvl).append(">")
                }
                is MdBlock.Paragraph -> {
                    sb.append("<p>")
                    sb.append(escapeForHtml(b.inline.toText()))
                    sb.append("</p>")
                }
                is MdBlock.MathBlock -> {
                    sb.append("<div class=\"katex-display\" data-tex=\"")
                    sb.append(escapeAttr(b.latex))
                    sb.append("\"></div>")
                }
                is MdBlock.CodeBlock -> {
                    sb.append("<pre><code>")
                    sb.append(escapeForHtml(b.code))
                    sb.append("</code></pre>")
                }
                is MdBlock.BulletListBlock -> {
                    sb.append("<ul>")
                    b.items.forEach { sb.append("<li>item</li>") }
                    sb.append("</ul>")
                }
                is MdBlock.OrderedListBlock -> {
                    sb.append("<ol>")
                    b.items.forEach { sb.append("<li>item</li>") }
                    sb.append("</ol>")
                }
                is MdBlock.Quote -> sb.append("<blockquote>quote</blockquote>")
                is MdBlock.Rule -> sb.append("<hr/>")
                is MdBlock.ImageBlock -> {
                    val alt = if (b.alt.isBlank()) b.url else b.alt
                    sb.append("<p class=\"img-alt\">[")
                    sb.append(escapeForHtml(alt))
                    sb.append("]</p>")
                }
                is MdBlock.Table -> {
                    sb.append("<table><thead><tr>")
                    b.headers.forEach { sb.append("<th>").append(escapeForHtml(it.toText())).append("</th>") }
                    sb.append("</tr></thead><tbody>")
                    b.rows.forEach { row ->
                        sb.append("<tr>")
                        row.forEach { sb.append("<td>").append(escapeForHtml(it.toText())).append("</td>") }
                        sb.append("</tr>")
                    }
                    sb.append("</tbody></table>")
                }
                is MdBlock.ChartBlock -> {
                    val chart = runCatching { com.example.viewer.ChartParser.parse(b.raw) }.getOrNull()
                    if (chart == null) {
                        sb.append("<pre><code>")
                        sb.append(escapeForHtml(b.raw))
                        sb.append("</code></pre>")
                    } else {
                        sb.append("<table class=\"chart\"><thead><tr><th>Label</th><th>Value</th></tr></thead><tbody>")
                        chart.entries.forEach {
                            sb.append("<tr><td>")
                            sb.append(escapeForHtml(it.label))
                            sb.append("</td><td>")
                            sb.append(it.value.toString())
                            sb.append("</td></tr>")
                        }
                        sb.append("</tbody></table>")
                    }
                }
                is MdBlock.MermaidBlock -> {
                    sb.append("<pre class=\"mermaid\"><code>")
                    sb.append(escapeForHtml(b.code))
                    sb.append("</code></pre>")
                }
            }
        }
        sb.append("</div>")
        val body = sb.toString().takeIf { it.length > 24 } ?: runCatching {
            val doc = mdParser.parse(raw)
            mdHtml.render(doc)
        }.getOrDefault("<pre>" + escapeForHtml(raw.take(20000)) + "</pre>")
        return wrap(body, dark, withKatex = true)
    }

    fun texToHtml(raw: String, dark: Boolean): String {
        val segments = runCatching { LatexSourceParser.parse(raw) }.getOrDefault(emptyList())
        val sb = StringBuilder("<div class=\"tex\">")
        for (s in segments) {
            when (s) {
                is TexSegment.Math -> {
                    if (s.displayMode) {
                        sb.append("<div class=\"katex-display\" data-tex=\"")
                        sb.append(escapeAttr(s.latex))
                        sb.append("\"></div>")
                    } else {
                        sb.append("<span class=\"katex-inline\" data-tex=\"")
                        sb.append(escapeAttr(s.latex))
                        sb.append("\"></span>")
                    }
                }
                is TexSegment.PlainText -> {
                    val line = s.text.trim()
                    if (line.isEmpty()) continue
                    sb.append("<pre>")
                    sb.append(escapeForHtml(s.text.take(20000)))
                    sb.append("</pre>")
                    break
                }
            }
        }
        sb.append("</div>")
        return wrap(sb.toString(), dark, withKatex = true)
    }

    fun docxToHtml(doc: OoxmlDocument, dark: Boolean): String {
        val sb = StringBuilder("<div class=\"docx\">")
        for (block in doc.blocks) {
            when (block) {
                is DocBlock.Paragraph -> {
                    val text = block.runs.joinToString("") { it.text }
                    if (text.isBlank() && block.headingLevel == null) continue
                    val inner = block.runs.joinToString("") { run ->
                        var t = escapeForHtml(run.text)
                        if (run.bold) t = "<b>" + t + "</b>"
                        if (run.italic) t = "<i>" + t + "</i>"
                        t
                    }
                    val tag = when (block.headingLevel) {
                        1 -> "h1"
                        2 -> "h2"
                        else -> if (block.headingLevel != null) "h3" else "p"
                    }
                    sb.append("<").append(tag).append(">")
                    sb.append(inner)
                    sb.append("</").append(tag).append(">")
                }
                is DocBlock.ImageRef -> {
                    val bytes = doc.imageBytesByPath[block.mediaPath]
                    if (bytes != null) {
                        sb.append("<img src=\"")
                        sb.append(dataUri(bytes))
                        sb.append("\"/>")
                    }
                }
            }
        }
        sb.append("</div>")
        return wrap(sb.toString(), dark, withKatex = false)
    }

    fun pptxToHtml(deck: OoxmlSlideDeck, dark: Boolean): String {
        val sb = StringBuilder("<div class=\"pptx\">")
        deck.slides.forEachIndexed { idx, slide ->
            sb.append("<section class=\"slide\"><h2>Slide ")
            sb.append((idx + 1).toString())
            sb.append("</h2>")
            slide.images.forEach { path ->
                deck.imageBytesByPath[path]?.let {
                    sb.append("<img src=\"")
                    sb.append(dataUri(it))
                    sb.append("\"/>")
                }
            }
            slide.paragraphs.forEach { runs ->
                val inner = runs.joinToString("") { r ->
                    var t = escapeForHtml(r.text)
                    if (r.bold) t = "<b>" + t + "</b>"
                    t
                }
                sb.append("<p>")
                sb.append(inner)
                sb.append("</p>")
            }
            sb.append("</section>")
        }
        sb.append("</div>")
        return wrap(sb.toString(), dark, withKatex = false)
    }

    private fun wrap(body: String, dark: Boolean, withKatex: Boolean): String {
        val bg = if (dark) "#17130B" else "#FFF9EE"
        val fg = if (dark) "#EBE1D0" else "#1F1B13"
        val pre = if (dark) "#231F17" else "#F7ECDB"
        val line = if (dark) "#4D4639" else "#D0C5B4"
        val head = if (dark) "#2E2921" else "#F1E7D5"
        val scheme = if (dark) "dark" else "light"
        val katexHead = if (withKatex) {
            "<link rel=\"stylesheet\" href=\"" + VIRTUAL_ORIGIN + "/assets/katex/katex.min.css\"/>" +
                "<script src=\"" + VIRTUAL_ORIGIN + "/assets/katex/katex.min.js\"></script>"
        } else ""
        // NOTE: no backslash literals in the inline script on purpose —
        // math is rendered via data-tex attributes + katex.render, so no
        // delimiter strings are needed and Kotlin escaping stays trivial.
        val katexScript = if (withKatex) {
            "<script>(function(){try{" +
                "var els=document.querySelectorAll('.katex-display');for(var i=0;i<els.length;i++){" +
                "try{katex.render(els[i].getAttribute('data-tex'),els[i],{displayMode:true,throwOnError:false});}catch(e){els[i].textContent=els[i].getAttribute('data-tex');}}" +
                "var inl=document.querySelectorAll('.katex-inline');for(var j=0;j<inl.length;j++){" +
                "try{katex.render(inl[j].getAttribute('data-tex'),inl[j],{displayMode:false,throwOnError:false});}catch(e){inl[j].textContent=inl[j].getAttribute('data-tex');}}" +
                "}catch(e){}})();</script>"
        } else ""
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src data:; font-src 'self' data:\"/>" +
            katexHead +
            "<style>" +
            ":root{color-scheme:" + scheme + "}" +
            "body{margin:0;padding:16px;font-family:system-ui,sans-serif;line-height:1.6;background:" + bg + ";color:" + fg + "}" +
            "table{border-collapse:collapse;width:100%;margin:12px 0;display:block;overflow-x:auto}" +
            "th,td{border:1px solid " + line + ";padding:8px;text-align:left}" +
            "th{font-weight:700;background:" + head + "}" +
            "pre{background:" + pre + ";padding:12px;border-radius:8px;overflow-x:auto}" +
            "code{font-family:monospace}" +
            "img{max-width:100%;height:auto;border-radius:8px;margin:8px 0}" +
            ".katex-display{overflow-x:auto;padding:8px 0;text-align:center}" +
            ".slide{border:1px solid " + line + ";border-radius:12px;padding:16px;margin:0 0 16px}" +
            "blockquote{border-left:4px solid " + line + ";margin:8px 0;padding:8px 12px}" +
            "</style></head>" +
            "<body>" + body + katexScript + "</body></html>"
    }

    private fun escapeForHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeAttr(s: String): String =
        escapeForHtml(s).replace("\"", "&quot;")

    private fun List<com.example.viewer.MdInline>.toText(): String {
        val out = StringBuilder()
        fun appendNodes(nodes: List<com.example.viewer.MdInline>) {
            nodes.forEach {
                when (it) {
                    is com.example.viewer.MdInline.PlainText -> out.append(it.text)
                    is com.example.viewer.MdInline.Bold -> appendNodes(it.children)
                    is com.example.viewer.MdInline.Italic -> appendNodes(it.children)
                    is com.example.viewer.MdInline.InlineCode -> out.append(it.text)
                    is com.example.viewer.MdInline.LinkText -> appendNodes(it.children)
                    com.example.viewer.MdInline.LineBreak -> out.append(" ")
                    is com.example.viewer.MdInline.Math -> out.append(it.latex)
                }
            }
        }
        appendNodes(this)
        return out.toString()
    }

    private fun dataUri(bytes: ByteArray): String {
        val b64 = runCatching {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }.getOrNull() ?: return ""
        val mime = if (bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            "image/jpeg"
        } else {
            "image/png"
        }
        return "data:" + mime + ";base64," + b64
    }
}
