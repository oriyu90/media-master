package com.example.backup

import java.io.File

/**
 * Pure path helpers for backup/restore. Kept free of Android APIs so they can be
 * unit-tested on the host JVM.
 */
object BackupPaths {

    /**
     * Resolves [entryName] (a ZIP entry path) against [root] and returns the target
     * file only if it stays inside [root]. Returns `null` for entries that would
     * escape the root via `..`, absolute paths, or symlink-style tricks (zip-slip).
     */
    fun safeResolve(root: File, entryName: String): File? {
        if (entryName.isBlank()) return null
        val target = File(root, entryName)
        val rootCanonical = root.canonicalPath
        val targetCanonical = target.canonicalPath
        val prefix = if (rootCanonical.endsWith(File.separator)) rootCanonical else rootCanonical + File.separator
        return if (targetCanonical == rootCanonical || targetCanonical.startsWith(prefix)) target else null
    }
}
