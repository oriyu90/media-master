package com.example.viewer

/** Minimal RFC4180-ish CSV/TSV parser: quoted fields, escaped quotes, delimiter sniffing. */
object CsvParser {

    data class Table(val headers: List<String>, val rows: List<List<String>>, val delimiter: Char, val rowsTruncated: Boolean)

    /** Parses [text] into a header row + up to [maxRows] body rows. Never throws. */
    fun parse(text: String, maxRows: Int = 10_000): Table {
        val delimiter = sniffDelimiter(text)
        val allRows = parseRows(text, delimiter)
        val headers = allRows.firstOrNull().orEmpty()
        val bodyAll = allRows.drop(1).filter { it.isNotEmpty() && !(it.size == 1 && it[0].isEmpty()) }
        val truncated = bodyAll.size > maxRows
        return Table(headers, bodyAll.take(maxRows), delimiter, truncated)
    }

    private fun sniffDelimiter(text: String): Char {
        val sample = text.lineSequence().take(10).joinToString("\n")
        val candidates = listOf(',', '\t', ';', '|')
        return candidates.associateWith { d -> sample.count { it == d } }
            .maxByOrNull { it.value }
            ?.takeIf { it.value > 0 }
            ?.key ?: ','
    }

    private fun parseRows(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < n && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        row.add(field.toString())
                        field = StringBuilder()
                    }
                    '\r' -> { /* normalized via \n handling below */ }
                    '\n' -> {
                        row.add(field.toString())
                        field = StringBuilder()
                        rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
