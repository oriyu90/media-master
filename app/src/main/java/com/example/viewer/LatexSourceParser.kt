package com.example.viewer

/**
 * Splits raw `.tex` source into plain-text runs and math runs.
 *
 * A real LaTeX document is not "compiled" on-device (impractical on
 * Android) — this is a deliberately simple viewer: everything outside a
 * recognised math delimiter is shown verbatim as monospace source text, and
 * each math region (`$...$`, `$$...$$`, `\(...\)`, `\[...\]`, and the
 * `equation`/`align`/`gather`/`eqnarray`/`multline` environments, each with
 * their `*`-starred, unnumbered variant) is typeset with KaTeX.
 */
sealed class TexSegment {
    data class PlainText(val text: String) : TexSegment()
    data class Math(val latex: String, val displayMode: Boolean) : TexSegment()
}

object LatexSourceParser {

    // NOTE: literal '}' must be escaped as '\}' — Android's ICU regex engine (unlike desktop
    // JVM regex, where unit tests run) rejects an unescaped '}' with PatternSyntaxException.
    // Found via on-device testing; the JVM unit tests could not have caught this.
    private val environmentPattern =
        Regex("\\\\begin\\{(equation\\*?|align\\*?|gather\\*?|eqnarray\\*?|multline\\*?)\\}([\\s\\S]*?)\\\\end\\{\\1\\}")
    private val dollarBlockPattern = Regex("(?<!\\\\)\\$\\$([\\s\\S]+?)(?<!\\\\)\\$\\$")
    private val bracketBlockPattern = Regex("\\\\\\[([\\s\\S]+?)\\\\]")
    private val dollarInlinePattern = Regex("(?<!\\\\)\\$(?!\\$)([^$\\n]+?)(?<!\\\\)\\$(?!\\$)")
    private val parenInlinePattern = Regex("\\\\\\(([^\\n]+?)\\\\\\)")

    private data class Candidate(val range: IntRange, val latex: String, val displayMode: Boolean)

    fun parse(source: String): List<TexSegment> {
        val segments = mutableListOf<TexSegment>()
        var index = 0
        while (index < source.length) {
            val candidate = findEarliestCandidate(source, index) ?: run {
                segments.add(TexSegment.PlainText(source.substring(index)))
                return segments
            }
            if (candidate.range.first > index) {
                segments.add(TexSegment.PlainText(source.substring(index, candidate.range.first)))
            }
            segments.add(TexSegment.Math(candidate.latex, candidate.displayMode))
            index = (candidate.range.last + 1).coerceAtLeast(candidate.range.first + 1)
        }
        return segments
    }

    private fun findEarliestCandidate(source: String, from: Int): Candidate? {
        val candidates = listOfNotNull(
            environmentPattern.find(source, from)?.let { Candidate(it.range, it.groupValues[2].trim(), true) },
            dollarBlockPattern.find(source, from)?.let { Candidate(it.range, it.groupValues[1].trim(), true) },
            bracketBlockPattern.find(source, from)?.let { Candidate(it.range, it.groupValues[1].trim(), true) },
            dollarInlinePattern.find(source, from)?.let { Candidate(it.range, it.groupValues[1].trim(), false) },
            parenInlinePattern.find(source, from)?.let { Candidate(it.range, it.groupValues[1].trim(), false) },
        )
        return candidates.minByOrNull { it.range.first }
    }
}
