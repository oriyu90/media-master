package com.example.viewer

import com.example.MediaFile

/**
 * v1.13.0: resolves the pager order for the media viewer from the list the
 * user is actually looking at (Library grid, Album, Category, Explorer...).
 *
 * Before this, [com.example.ViewerScreen] always paged through the global
 * MediaStore list sorted by the global sort option, so swiping moved through a
 * different set/order than the visible grid. The resolver is pure (no Compose,
 * no Android) so the ordering contract is unit-testable.
 *
 * Callers pass the exact visible order through [com.example.FileViewModel.prepareViewer].
 * When no launch is prepared (deep links, process restore, unknown callers) the
 * resolver reproduces the v1.12.2 global-list behavior exactly.
 */
object ViewerOrder {

    /**
     * Returns the ordered media list for [targetPath].
     *
     * - [orderedPaths] non-null: map each path through the [allFiles] index,
     *   skip missing entries, keep only the target's media family (so an album
     *   order that mixes documents never leaks them into the pager), and use it
     *   only when the target survived.
     * - Otherwise: v1.12.2 fallback — all image/video files, all audio files,
     *   or the single target for anything else.
     *
     * The given order is preserved verbatim; nothing is sorted here.
     */
    fun resolve(
        allFiles: List<MediaFile>,
        orderedPaths: List<String>?,
        targetPath: String,
    ): List<MediaFile> {
        val target = allFiles.firstOrNull { it.path == targetPath } ?: return emptyList()
        val family = familyOf(target)
        // Non-media targets are a single page: documents never page through
        // arbitrary siblings (they use DocumentViewerScreen instead).
        if (family == MediaFamily.SINGLE) return listOf(target)

        if (orderedPaths != null) {
            val byPath = HashMap<String, MediaFile>(allFiles.size)
            allFiles.forEach { byPath[it.path] = it }
            val resolved = ArrayList<MediaFile>(orderedPaths.size)
            orderedPaths.forEach { path ->
                val file = byPath[path] ?: return@forEach
                if (familyOf(file) == family) resolved.add(file)
            }
            // A launch prepared for another list (or a deleted target) must not
            // shrink the pager: only trust the order when the target is in it.
            if (resolved.any { it.path == targetPath }) return resolved
        }

        return when (family) {
            MediaFamily.IMAGE_VIDEO -> allFiles.filter {
                it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")
            }
            MediaFamily.AUDIO -> allFiles.filter { it.mimeType.startsWith("audio/") }
            MediaFamily.SINGLE -> listOf(target)
        }
    }

    private enum class MediaFamily { IMAGE_VIDEO, AUDIO, SINGLE }

    private fun familyOf(file: MediaFile): MediaFamily = when {
        file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/") -> MediaFamily.IMAGE_VIDEO
        file.mimeType.startsWith("audio/") -> MediaFamily.AUDIO
        else -> MediaFamily.SINGLE
    }
}
