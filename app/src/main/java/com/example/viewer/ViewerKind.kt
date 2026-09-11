package com.example.viewer

/** Which built-in renderer [com.example.ui.viewer.DocumentViewerScreen] should use for a file. */
enum class ViewerKind {
    TEXT, CSV, JSON, MARKDOWN, LATEX_SOURCE, PDF, DOCX, PPTX, HEX, EXTERNAL_ONLY
}

/**
 * Extension/MIME -> [ViewerKind] classification for the universal document viewer.
 *
 * Anything that isn't a recognised text/office/PDF format falls back to
 * [ViewerKind.HEX] rather than being refused, so every ordinary file can be
 * inspected inside the app. The one deliberate exception is legacy binary
 * Office formats (.doc/.ppt, pre-2007): rendering them would need Apache POI,
 * which fails to dex below minSdk 26 (see IMPLEMENTATION_AND_MAINTENANCE.md) —
 * they map to [ViewerKind.EXTERNAL_ONLY] so callers keep the existing
 * "open in another app" behaviour instead of a broken in-app attempt.
 */
object ViewerKindClassifier {

    private val PLAIN_TEXT_EXTENSIONS = setOf(
        "txt", "log", "ini", "conf", "cfg", "yaml", "yml", "properties", "xml",
        "srt", "vtt", "gitignore", "gradle", "kt", "kts", "java", "py", "js", "ts", "sh"
    )

    fun classify(name: String, mimeType: String?): ViewerKind {
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = mimeType.orEmpty()
        return when {
            ext == "csv" || ext == "tsv" || mime == "text/csv" -> ViewerKind.CSV
            ext == "json" || mime == "application/json" -> ViewerKind.JSON
            ext == "md" || ext == "markdown" || mime == "text/markdown" -> ViewerKind.MARKDOWN
            ext == "tex" || ext == "ltx" || mime == "text/x-tex" -> ViewerKind.LATEX_SOURCE
            ext == "pdf" || mime == "application/pdf" -> ViewerKind.PDF
            ext == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ViewerKind.DOCX
            ext == "pptx" || mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ViewerKind.PPTX
            ext == "doc" || mime == "application/msword" -> ViewerKind.EXTERNAL_ONLY
            ext == "ppt" || mime == "application/vnd.ms-powerpoint" -> ViewerKind.EXTERNAL_ONLY
            ext in PLAIN_TEXT_EXTENSIONS || mime.startsWith("text/") -> ViewerKind.TEXT
            else -> ViewerKind.HEX
        }
    }
}
