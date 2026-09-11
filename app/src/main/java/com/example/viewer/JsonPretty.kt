package com.example.viewer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Pretty-prints JSON text using the project's existing kotlinx.serialization dependency. */
@OptIn(ExperimentalSerializationApi::class)
object JsonPretty {

    private val lenient = Json { isLenient = true; ignoreUnknownKeys = true }
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** Returns pretty-printed JSON, or null if [raw] doesn't parse (caller should show the raw text instead). */
    fun format(raw: String): String? = runCatching {
        val element = lenient.parseToJsonElement(raw)
        pretty.encodeToString(JsonElement.serializer(), element)
    }.getOrNull()
}
