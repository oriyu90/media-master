package com.example.deeplink

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

/**
 * WP3 "standard file manager" helpers for files handed over by another app
 * (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`).
 *
 * Everything here is deliberately free of UI, so the naming and path rules
 * are pinned by plain unit tests ([com.example.deeplink.IncomingFilesTest]).
 * Only [defaultDestination] and [displayName] touch Android APIs (and both
 * swallow every failure — a broken provider must never crash the save flow).
 */
object IncomingFiles {

    /** Hard cap on how many shared files one hand-off may carry. */
    const val MAX_FILES = 50

    /** Provider names above this length are truncated (plus extension kept). */
    private const val MAX_STEM_LENGTH = 100

    /** Fallback base name when the sender exposes no usable name at all. */
    const val FALLBACK_NAME = "shared_file"

    /**
     * Default save folder: the shared `Download` directory when it exists,
     * otherwise the top of the shared external storage, otherwise the app's
     * own external files directory. Never throws.
     */
    fun defaultDestination(context: Context): String {
        runCatching {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory()
            if (root != null) {
                val download = File(root, Environment.DIRECTORY_DOWNLOADS)
                if (download.isDirectory) return download.absolutePath
                if (root.isDirectory) return root.absolutePath
            }
        }
        return runCatching { context.getExternalFilesDir(null)?.absolutePath }.getOrNull()
            ?: runCatching { context.filesDir.absolutePath }.getOrDefault("/")
    }

    /**
     * Best-effort display name for a shared [uri] (`OpenableColumns.DISPLAY_NAME`,
     * then the last path segment). Returns `null` when nothing usable is found;
     * callers fall back to [FALLBACK_NAME]. Never throws.
     */
    fun displayName(context: Context, uri: Uri): String? {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        val candidate = fromProvider?.takeIf { it.isNotBlank() }
            ?: runCatching { uri.lastPathSegment }.getOrNull()?.takeIf { it.isNotBlank() }
        return candidate?.let(::sanitizeName)
    }

    /**
     * Reduces an arbitrary sender-supplied name to a safe single path segment:
     * drops directory components (path traversal), control characters and
     * Windows/Android-reserved characters, trims trailing dots/spaces, and caps
     * the length while keeping the extension. Returns `null` when nothing is
     * left (e.g. `".."`, `"/"`, `""`).
     */
    fun sanitizeName(raw: String?): String? {
        if (raw == null) return null
        // Both separators: a provider may hand over a Windows-style path.
        val basename = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString {
            for (ch in basename) {
                when {
                    ch.code < 0x20 || ch.code == 0x7F -> Unit
                    ch in "\\/:*?\"<>|" -> append('_')
                    else -> append(ch)
                }
            }
        }.trim().trimEnd('.', ' ').trimStart()
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return null
        return truncateKeepingExtension(cleaned)
    }

    /**
     * Pure duplicate-name resolver: `name.ext`, then `name (1).ext`,
     * `name (2).ext`, … until [exists] reports the candidate is free.
     *
     * - No extension (`README`) → `README (1)`.
     * - Leading-dot names (`.nomedia`) are treated as extension-less, so the
     *   result is `.nomedia (1)` and never ` (1).nomedia`.
     * - Multi-part extensions (`archive.tar.gz`) keep only `.gz` as the
     *   extension — the same rule as `File.nameWithoutExtension`.
     * - The desired name is sanitized first (see [sanitizeName]); an unusable
     *   name falls back to [FALLBACK_NAME].
     * - [exists] is consulted with the *candidate* name only (never a path),
     *   so the caller stays in charge of the destination directory.
     */
    fun uniqueName(desired: String, exists: (String) -> Boolean): String {
        val base = sanitizeName(desired) ?: FALLBACK_NAME
        if (!exists(base)) return base
        // Extension split on the *raw* name: sanitizeName trims a trailing dot,
        // so splitting the cleaned name would lose the extensionless case
        // (a sender's "weird." collides as "weird (1)", not a second probe of
        // "weird."). Fall back to the cleaned name when the raw one has no
        // usable stem (e.g. a name made only of reserved characters).
        val raw = desired.substringAfterLast('/').substringAfterLast('\\').trim()
        val rawDot = raw.lastIndexOf('.')
        val rawHasExtension = rawDot > 0 && rawDot < raw.length - 1
        val baseDot = base.lastIndexOf('.')
        val baseHasExtension = baseDot > 0 && baseDot < base.length - 1
        val hasExtension = rawHasExtension && baseHasExtension
        val stem = if (hasExtension) base.substring(0, baseDot) else base
        val extension = if (hasExtension) base.substring(baseDot) else ""
        var counter = 1
        while (true) {
            val candidate = "$stem ($counter)$extension"
            if (!exists(candidate)) return candidate
            counter++
        }
    }

    /**
     * Resolves [name] inside [destDir] without ever escaping it: the name is
     * sanitized to a single segment first, so `../evil.txt` lands as
     * `evil.txt` inside [destDir].
     */
    fun resolveDestination(destDir: File, name: String): File {
        val safe = sanitizeName(name) ?: FALLBACK_NAME
        return File(destDir, safe)
    }

    /** Caps the name length, keeping the extension when there is one. */
    private fun truncateKeepingExtension(name: String): String {
        if (name.length <= MAX_STEM_LENGTH) return name
        val dot = name.lastIndexOf('.')
        val hasExtension = dot > 0 && dot < name.length - 1
        val extension = if (hasExtension) name.substring(dot) else ""
        val stem = if (hasExtension) name.substring(0, dot) else name
        val keep = (MAX_STEM_LENGTH - extension.length).coerceAtLeast(1)
        return stem.take(keep) + extension
    }

    /** Lower-case extension (without the dot) or `""`; used for MIME guessing. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }
}
