package com.example.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupPathsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val root: File get() = tmp.root

    @Test
    fun normalEntry_resolvesInsideRoot() {
        val resolved = BackupPaths.safeResolve(root, "Pictures/Album/photo.jpg")
        assertEquals(File(root, "Pictures/Album/photo.jpg").canonicalPath, resolved?.canonicalPath)
    }

    @Test
    fun parentTraversal_isRejected() {
        assertNull(BackupPaths.safeResolve(root, "../evil.txt"))
        assertNull(BackupPaths.safeResolve(root, "Pictures/../../evil.txt"))
        assertNull(BackupPaths.safeResolve(root, "a/b/c/../../../../evil.txt"))
    }

    @Test
    fun absolutePath_isDefangedIntoRoot() {
        // java.io.File(parent, "/abs/path") re-roots the absolute child under parent,
        // so it cannot escape; it must still resolve strictly inside root.
        val resolved = BackupPaths.safeResolve(root, "/etc/passwd")
        assertEquals(File(root, "etc/passwd").canonicalPath, resolved?.canonicalPath)
    }

    @Test
    fun parentTraversalThenAbsolute_isRejected() {
        assertNull(BackupPaths.safeResolve(root, "../../../../etc/passwd"))
    }

    @Test
    fun blankEntry_isRejected() {
        assertNull(BackupPaths.safeResolve(root, ""))
        assertNull(BackupPaths.safeResolve(root, "   "))
    }

    @Test
    fun siblingWithSharedPrefix_isRejected() {
        val sibling = File(root.parentFile, root.name + "_evil")
        assertNull(BackupPaths.safeResolve(root, "../${sibling.name}/x.txt"))
    }
}
