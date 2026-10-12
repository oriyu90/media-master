package com.example.zip

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * ZIP listing for the universal viewer (v1.10.0).
 *
 * Pure JVM helpers so unit tests pin behaviour without Android:
 * - streaming read (never holds whole archive in memory)
 * - entry-count / per-entry / total-size caps (decompression-bomb guard)
 * - corrupt archives degrade to empty list, never throw.
 *
 * Extraction itself reuses [com.example.backup.BackupPaths.safeResolve]
 * (zip-slip guard) at the call site so this file stays Android-free.
 */
data class ZipEntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val time: Long,
)

object ZipListing {

    /** Hard caps: large enough for real archives, small enough to stay safe. */
    const val MAX_ENTRIES = 2000
    const val MAX_ENTRY_BYTES = 100 * 1024 * 1024L
    const val MAX_TOTAL_BYTES = 500 * 1024 * 1024L
    const val MAX_NAME_LENGTH = 512

    /**
     * List entries from [input] without extracting file contents.
     * Returns entries in archive order; directories kept (isDirectory=true).
     * Any failure (corrupt/empty/unsupported) returns emptyList().
     */
    fun listEntries(input: InputStream): List<ZipEntryInfo> = runCatching {
        val out = ArrayList<ZipEntryInfo>(64)
        var total = 0L
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (out.size >= MAX_ENTRIES) break
                val rawName = entry.name.orEmpty()
                // Skip blank / overlong names (never crash on hostile headers).
                if (rawName.isNotBlank() && rawName.length <= MAX_NAME_LENGTH) {
                    val size = entry.size.takeIf { it >= 0 } ?: -1L
                    val compressed = entry.compressedSize.takeIf { it >= 0 } ?: -1L
                    out.add(
                        ZipEntryInfo(
                            name = rawName,
                            size = size,
                            compressedSize = compressed,
                            isDirectory = entry.isDirectory || rawName.endsWith("/"),
                            time = entry.time,
                        ),
                    )
                    if (size > 0) {
                        total += size
                        if (total > MAX_TOTAL_BYTES) break
                    }
                }
                runCatching { zip.closeEntry() }
                entry = runCatching { zip.nextEntry }.getOrNull()
            }
        }
        out.toList()
    }.getOrDefault(emptyList())

    /** True when [name] looks like a ZIP path (extension check, case-insensitive). */
    fun isZipName(name: String): Boolean =
        name.trim().lowercase().endsWith(".zip")

    /** True when [mimeType] is a known ZIP MIME (case-insensitive). */
    fun isZipMime(mimeType: String?): Boolean {
        if (mimeType == null) return false
        return when (mimeType.trim().lowercase()) {
            "application/zip",
            "application/x-zip-compressed",
            "application/x-zip",
            -> true
            else -> false
        }
    }

    /**
     * Whether [entryName] is safe to extract (no absolute path, no `..`
     * traversal, non-blank). Full canonical check stays in
     * [com.example.backup.BackupPaths.safeResolve] at extraction time;
     * this is the cheap pre-filter for listing UI.
     */
    fun isSafeEntryName(entryName: String): Boolean {
        if (entryName.isBlank()) return false
        if (entryName.startsWith("/") || entryName.startsWith("\\")) return false
        // Split on both separators; any ".." segment is unsafe.
        val parts = entryName.split('/', '\\')
        if (parts.any { it == ".." }) return false
        // Windows drive-letter ("C:...") and NUL are never valid in a safe entry.
        if (entryName.length >= 2 && entryName[1] == ':') return false
        if ('\u0000' in entryName) return false
        return true
    }
}
