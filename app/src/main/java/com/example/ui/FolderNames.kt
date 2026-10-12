package com.example.ui

import java.io.File

/**
 * Accurate folder / volume names (v1.10.0).
 *
 * v1.9.2 fixed only ManageDashboardScreen to show the real volume name
 * (e.g. "ABCD-1234" for SD cards). Other surfaces (file-browser title,
 * breadcrumb root, desktop sidebar, exclude-folder picker) still rendered
 * the same generic "External Storage" label for every external volume, and
 * Category tabs grouped by bare folder name (merging same-named folders
 * from different parents). This shared pure helper unifies the behaviour
 * so every screen shows the same accurate name.
 *
 * All functions are JVM-only (java.io only) so unit tests pin them
 * without Android.
 */
object FolderNames {

    /**
     * Last path segment for volume labels ("/storage/ABCD-1234" ->
     * "ABCD-1234"). Empty when the path has no segment (e.g. "/").
     */
    fun volumeDisplayName(path: String): String =
        runCatching { File(path.trimEnd('/')).name }.getOrDefault("")

    /**
     * Accurate display name for any folder [path]: last segment, falling
     * back to the full [path] when there is no segment (root "/").
     * Never returns blank.
     */
    fun folderDisplayName(path: String): String {
        if (path.isBlank()) return path
        val name = runCatching { File(path.trimEnd('/')).name }.getOrDefault("")
        return name.ifBlank { path }
    }

    /**
     * Title for a storage root: internal roots keep the caller-provided
     * [internalLabel]; external roots show the real volume name, falling
     * back to [externalFallback] only when blank.
     */
    fun storageRootTitle(
        path: String,
        isInternal: Boolean,
        internalLabel: String,
        externalFallback: String,
    ): String = if (isInternal) internalLabel else volumeDisplayName(path).ifBlank { externalFallback }

    /**
     * Disambiguate parent-folder labels for Category tabs.
     *
     * Groups must be keyed by full parent *path* (not bare name) so two
     * different folders that happen to share a name never merge. Display
     * labels stay as the last segment, but when several parents share the
     * same last segment the full parent path is shown instead so the tabs
     * stay distinguishable.
     *
     * @param parentPaths full parent directory paths (e.g. "/sdcard/DCIM").
     * @return map from parent path to the label to display.
     */
    fun disambiguateParentLabels(parentPaths: List<String>): Map<String, String> {
        if (parentPaths.isEmpty()) return emptyMap()
        val names = parentPaths.associateWith { folderDisplayName(it) }
        val counts = names.values.groupingBy { it }.eachCount()
        return parentPaths.associateWith { path ->
            val name = names[path].orEmpty()
            if ((counts[name] ?: 0) > 1) path else name
        }
    }
}
