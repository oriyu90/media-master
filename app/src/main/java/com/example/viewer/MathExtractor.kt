package com.example.viewer

/**
 * Extracts LaTeX math (`$...$` inline, `$$...$$` block) out of raw Markdown
 * source *before* it reaches the CommonMark parser (which has no concept of
 * math and would otherwise treat `$` as plain punctuation). Each match is
 * replaced by a placeholder token built from ASCII control characters
 * (STX/ETX) that can never collide with real document text, and that
 * CommonMark passes through untouched as ordinary text; [MarkdownParser]
 * later swaps those placeholders back out for [MdBlock.MathBlock] /
 * [MdInline.Math] nodes.
 *
 * `\$` is treated as an escaped, literal dollar sign and never starts math.
 */
object MathExtractor {

    private const val STX = '\u0002'
    private const val ETX = '\u0003'
    private const val BLOCK_TAG = "MMB"
    private const val INLINE_TAG = "MMI"

    private const val DOLLAR = "\\$"
    private const val BACKSLASH = "\\\\"

    private val blockMathRegex = Regex("(?<!$BACKSLASH)$DOLLAR$DOLLAR([\\s\\S]+?)(?<!$BACKSLASH)$DOLLAR$DOLLAR")
    private val inlineMathRegex = Regex("(?<!$BACKSLASH)$DOLLAR(?!$DOLLAR)([^$DOLLAR\\n]+?)(?<!$BACKSLASH)$DOLLAR(?!$DOLLAR)")

    val inlinePlaceholderRegex = Regex("$STX$INLINE_TAG(\\d+)$ETX")
    val blockPlaceholderRegex = Regex("^$STX$BLOCK_TAG(\\d+)$ETX$")

    data class Extraction(val text: String, val blockMath: List<String>, val inlineMath: List<String>)

    fun extract(source: String): Extraction {
        val blocks = mutableListOf<String>()
        val inlines = mutableListOf<String>()

        // Block math ($$...$$) first, so its dollar signs are consumed before the inline pass sees them.
        var working = blockMathRegex.replace(source) { match ->
            val idx = blocks.size
            blocks.add(match.groupValues[1].trim())
            "\n\n$STX$BLOCK_TAG$idx$ETX\n\n"
        }

        // Inline math ($...$): single line, non-empty, not itself a `$$` marker.
        working = inlineMathRegex.replace(working) { match ->
            val idx = inlines.size
            inlines.add(match.groupValues[1].trim())
            "$STX$INLINE_TAG$idx$ETX"
        }

        return Extraction(working, blocks, inlines)
    }
}
