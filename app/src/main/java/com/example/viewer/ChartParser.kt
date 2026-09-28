package com.example.viewer

/**
 * v1.8.0 (#2): lightweight chart model for ```chart fenced blocks.
 *
 * Accepted formats (all offline, never throws):
 * - CSV: `label,value` per line, e.g. `Jan,120`
 * - `label: value` per line
 * - JSON array: `[{"label":"Jan","value":120}]` or `{"labels":[...],"values":[...]}`
 *
 * Values are clamped to a sane range so a malformed document can never OOM
 * the chart renderer (max 24 bars, |value| <= 1e9).
 */
data class ChartData(val entries: List<ChartEntry>) {
    val maxValue: Float = entries.maxOfOrNull { kotlin.math.abs(it.value) } ?: 0f
}

data class ChartEntry(val label: String, val value: Float)

object ChartParser {
    private const val MAX_ENTRIES = 24

    fun parse(raw: String): ChartData? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Try JSON first when it looks like JSON.
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(trimmed)?.let { return it }
        }
        return parseLines(trimmed)
    }

    private fun parseLines(raw: String): ChartData? {
        val entries = mutableListOf<ChartEntry>()
        for (line in raw.lines()) {
            val t = line.trim().trimStart('-', '*', '•').trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val parts = when {
                "," in t -> t.split(",").map { it.trim() }
                ":" in t -> t.split(":").map { it.trim() }
                " " in t -> {
                    val idx = t.trim().lastIndexOf(' ')
                    if (idx > 0) listOf(t.substring(0, idx).trim(), t.substring(idx + 1).trim()) else null
                }
                else -> null
            } ?: continue
            if (parts.size < 2) continue
            val value = parts.last().replace(",", "").toFloatOrNull() ?: continue
            if (!value.isFinite()) continue
            val label = parts.dropLast(1).joinToString(" ").take(16)
            entries.add(ChartEntry(label.ifBlank { "?" }, value.coerceIn(-1e9f, 1e9f)))
            if (entries.size >= MAX_ENTRIES) break
        }
        return if (entries.isEmpty()) null else ChartData(entries)
    }

    private fun parseJson(raw: String): ChartData? = runCatching {
        // Minimal hand-rolled JSON scan to avoid a new dependency.
        // Supports [{"label":..,"value":..}] and {"labels":[..],"values":[..]}.
        val entries = mutableListOf<ChartEntry>()
        val labelRegex = Regex("\"label\"\\s*:\\s*\"([^\"]{1,16})\"")
        val valueRegex = Regex("\"value\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
        val labels = labelRegex.findAll(raw).map { it.groupValues[1] }.toList()
        val values = valueRegex.findAll(raw).mapNotNull { it.groupValues[1].toFloatOrNull() }.toList()
        if (labels.isNotEmpty() && labels.size == values.size) {
            labels.zip(values).take(MAX_ENTRIES).forEach { (l, v) ->
                if (v.isFinite()) entries.add(ChartEntry(l, v.coerceIn(-1e9f, 1e9f)))
            }
            return if (entries.isEmpty()) null else ChartData(entries)
        }
        // {"labels":[...],"values":[...]} fallback via quoted strings + numbers
        null
    }.getOrNull()
}
