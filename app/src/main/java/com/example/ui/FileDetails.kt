package com.example.ui

import com.example.MediaFile
import com.example.SortOption
import com.example.ViewMode

/**
 * File-list details (v1.10.0, Windows 11 Explorer reference).
 *
 * Phone lists historically showed only "size • date modified". Desktop
 * (DeX) lists now mirror Explorer's Details view: Name / Size /
 * Date modified / Date created / Type columns with per-column show/hide.
 * Name is always shown; the other four are user-togglable and persisted
 * in DataStore (see SettingsRepository list keys).
 *
 * All helpers here are pure (no Android APIs) so unit tests pin them.
 */
object FileDetails {

    /**
     * Human type label for [name]/[mimeType]: extension uppercase when
     * present ("PDF", "ZIP"), else MIME subtype uppercase ("JPEG"),
     * else empty. Directories are handled by callers (folder label).
     */
    fun typeLabel(name: String, mimeType: String): String {
        val ext = name.substringAfterLast('.', "").trim()
        if (ext.isNotEmpty() && ext.length <= 10 && '/' !in ext && '\\' !in ext) {
            return ext.uppercase()
        }
        val sub = mimeType.substringAfter('/', "").trim()
        if (sub.isNotEmpty() && sub != "*" && sub.length <= 32) {
            // Strip MIME parameters ("text/plain; charset=...").
            return sub.substringBefore(';').trim().uppercase().ifBlank { "" }
        }
        return ""
    }

    /**
     * Ordered detail parts for a list row, honouring visibility flags.
     * Each part is already formatted (size/date strings); this only
     * decides order and filtering. Order mirrors Explorer:
     * Size, Date modified, Date created, Type.
     */
    fun detailParts(
        sizeText: String?,
        modifiedText: String?,
        createdText: String?,
        typeText: String?,
        showSize: Boolean,
        showModified: Boolean,
        showCreated: Boolean,
        showType: Boolean,
        isDirectory: Boolean,
    ): List<String> {
        if (isDirectory) return emptyList()
        val out = ArrayList<String>(4)
        if (showSize && !sizeText.isNullOrBlank()) out.add(sizeText)
        if (showModified && !modifiedText.isNullOrBlank()) out.add(modifiedText)
        if (showCreated && !createdText.isNullOrBlank() && createdText != modifiedText) out.add(createdText)
        if (showType && !typeText.isNullOrBlank()) out.add(typeText)
        return out
    }

    /** Join [parts] the same way every list row does (" • " separator). */
    fun joinParts(parts: List<String>): String = parts.joinToString(" • ")
}

/**
 * Desktop-aware default view modes (v1.10.0).
 *
 * - Phone file browser defaults to GRID (touch-friendly thumbnails).
 * - Desktop (DeX / PC mode, wide window) defaults to LIST, mirroring
 *   Windows 11 Explorer's Details default on desktop.
 * - Category screens keep their per-category phone defaults (photos/videos
 *   GRID, others LIST) but also default to LIST on desktop.
 *
 * Callers must respect an explicit user choice afterwards (see
 * FileViewModel user-set guards) so toggling is never clobbered.
 */
object DesktopViewModes {

    fun fileBrowserDefault(isDesktop: Boolean): ViewMode =
        if (isDesktop) ViewMode.LIST else ViewMode.GRID

    fun categoryDefault(isDesktop: Boolean, isMediaGallery: Boolean): ViewMode =
        if (isDesktop) ViewMode.LIST else if (isMediaGallery) ViewMode.GRID else ViewMode.LIST

    /** Map a list-header column to the sort it drives (Explorer parity). */
    fun sortForHeaderColumn(column: HeaderColumn): SortOption = when (column) {
        HeaderColumn.NAME -> SortOption.NAME
        HeaderColumn.SIZE -> SortOption.SIZE
        HeaderColumn.TYPE -> SortOption.TYPE
        HeaderColumn.MODIFIED, HeaderColumn.CREATED -> SortOption.DATE_CREATED
    }

    enum class HeaderColumn { NAME, SIZE, MODIFIED, CREATED, TYPE }
}
