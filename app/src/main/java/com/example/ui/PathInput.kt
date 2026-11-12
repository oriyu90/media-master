package com.example.ui

import java.io.File

/**
 * v2.1.0 Explorer: pure helpers for the "move/copy to path" dialog and for
 * classifying a user-typed path. Kept free of Android types so the whole
 * accept/reject decision is unit-tested on the JVM.
 *
 * Accepted inputs (after trimming):
 *  - absolute paths (`/storage/emulated/0/Download`, `/sdcard/DCIM`)
 *  - `~` or `~/…` (expanded to the primary storage root)
 *  - `file://` URIs (the path part is used)
 *
 * The result is always an *existing directory* inside one of the storage
 * roots, never the source itself and never a descendant of it — moving a
 * folder into itself would otherwise delete the data.
 */
object PathInput {

    /** User-facing failure reasons; the UI maps these to localized strings. */
    enum class Error {
        EMPTY,
        NOT_ABSOLUTE,
        NOT_FOUND,
        NOT_A_DIRECTORY,
        OUTSIDE_STORAGE,
        SAME_AS_SOURCE,
        INSIDE_SOURCE,
        NOT_WRITABLE,
    }

    sealed class Result {
        data class Ok(val path: String) : Result()
        data class Invalid(val error: Error) : Result()
    }

    /** Normalizes user input into an absolute path, or null when unusable. */
    fun normalize(raw: String, primaryRoot: String?): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("file://")) {
            text = text.removePrefix("file://")
            // file:///storage/... -> /storage/...
            if (!text.startsWith("/")) return null
        }
        if (text == "~") {
            text = primaryRoot ?: return null
        } else if (text.startsWith("~/")) {
            text = (primaryRoot ?: return null) + text.removePrefix("~")
        }
        if (!text.startsWith("/")) return null
        // Collapse duplicate separators, then drop trailing separators (the
        // root keeps its single "/").
        val collapsed = text.replace(Regex("/{2,}"), "/").trimEnd('/')
        val trimmed = if (collapsed.isEmpty()) "/" else collapsed
        // Resolve "." / ".." so the later string comparisons cannot be
        // bypassed with "/storage/emulated/0/Download/../Download".
        return canonicalize(trimmed)
    }

    /**
     * Lexically resolves `.` and `..` segments without touching the disk
     * (`File.canonicalPath` would also resolve symlinks but can throw and
     * needs I/O). `/a/b/../../..` collapses to `/`.
     */
    fun canonicalize(path: String): String {
        if (!path.startsWith("/")) return path
        val parts = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        return "/" + parts.joinToString("/")
    }

    /** True when [child] is [parent] or lives below it (path-segment safe). */
    fun isSameOrInside(child: String, parent: String): Boolean {
        if (child == parent) return true
        val p = parent.trimEnd('/')
        return child.startsWith("$p/")
    }

    /**
     * Validates [raw] as a move/copy destination for [sources].
     *
     * @param exists directory probe (injectable for tests)
     * @param writable write probe (injectable for tests)
     */
    fun validate(
        raw: String,
        primaryRoot: String?,
        storageRoots: List<String>,
        sources: List<String>,
        exists: (String) -> Boolean = { File(it).isDirectory },
        writable: (String) -> Boolean = { File(it).canWrite() },
    ): Result {
        if (raw.isBlank()) return Result.Invalid(Error.EMPTY)
        val normalized = normalize(raw, primaryRoot) ?: return Result.Invalid(Error.NOT_ABSOLUTE)
        if (!exists(normalized)) return Result.Invalid(Error.NOT_FOUND)
        val canonicalRoots = storageRoots.map { canonicalize(it) }
        if (canonicalRoots.isNotEmpty() && canonicalRoots.none { isSameOrInside(normalized, it) }) {
            return Result.Invalid(Error.OUTSIDE_STORAGE)
        }
        // Sources are already absolute paths from the file browser; normalizing
        // them through the *destination* rules would drop anything outside a
        // known root, so only separators are collapsed here.
        val normalizedSources = sources.mapNotNull { source ->
            if (!source.startsWith("/")) null else canonicalize(source.replace(Regex("/{2,}"), "/"))
        }
        if (normalizedSources.any { it == normalized }) return Result.Invalid(Error.SAME_AS_SOURCE)
        if (normalizedSources.any { isSameOrInside(normalized, it) }) return Result.Invalid(Error.INSIDE_SOURCE)
        if (!writable(normalized)) return Result.Invalid(Error.NOT_WRITABLE)
        return Result.Ok(normalized)
    }

    /**
     * Destination file path for moving/copying [sourcePath] into [destDir],
     * preserving the file name. Returns null when the name is unusable.
     */
    fun destinationPath(destDir: String, sourcePath: String): String? {
        // Only a trailing separator is dropped: a root path like "/" has no
        // name to move, and trimEnd('/') on it would yield an empty segment.
        val trimmed = if (sourcePath.length > 1) sourcePath.trimEnd('/') else sourcePath
        if (trimmed == "/") return null
        val name = trimmed.substringAfterLast('/')
        if (name.isBlank() || name == "." || name == "..") return null
        return if (destDir.endsWith("/")) "$destDir$name" else "$destDir/$name"
    }
}
