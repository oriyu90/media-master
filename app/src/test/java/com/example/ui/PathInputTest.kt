package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.1.0 Explorer: the typed-path destination dialog delegates every
 * accept/reject decision to [PathInput]. These tests pin the rules that keep
 * a move from destroying data (same folder, folder into itself) and keep the
 * UI honest about unusable input.
 */
class PathInputTest {

    private val root = "/storage/emulated/0"
    private val roots = listOf(root, "/storage/1234-ABCD")

    // Named arguments are mandatory here: passing the probes positionally
    // would silently swap them with the source list.
    private fun validate(
        raw: String,
        sources: List<String> = listOf("$root/Download/a.txt"),
        exists: (String) -> Boolean = { true },
        writable: (String) -> Boolean = { true },
    ) = PathInput.validate(
        raw = raw,
        primaryRoot = root,
        storageRoots = roots,
        sources = sources,
        exists = exists,
        writable = writable,
    )

    @Test
    fun `normalize trims, collapses and strips trailing slash`() {
        assertEquals("$root/Download", PathInput.normalize("  $root/Download/  ", root))
        assertEquals("$root/Download", PathInput.normalize("$root//Download", root))
        assertEquals(root, PathInput.normalize("$root/", root))
        assertEquals("/", PathInput.normalize("/", root))
    }

    @Test
    fun `normalize expands tilde and file scheme`() {
        assertEquals("$root/Pictures", PathInput.normalize("~/Pictures", root))
        assertEquals(root, PathInput.normalize("~", root))
        assertEquals("$root/DCIM", PathInput.normalize("file://$root/DCIM", root))
        assertNull(PathInput.normalize("~", null))
    }

    @Test
    fun `normalize rejects relative and empty input`() {
        assertNull(PathInput.normalize("", root))
        assertNull(PathInput.normalize("   ", root))
        assertNull(PathInput.normalize("Download", root))
        assertNull(PathInput.normalize("content://media/external/images/1", root))
    }

    @Test
    fun `valid destination is accepted and normalized`() {
        val result = validate("$root/Movies/")
        assertTrue(result is PathInput.Result.Ok)
        assertEquals("$root/Movies", (result as PathInput.Result.Ok).path)
    }

    @Test
    fun `second volume is inside storage`() {
        val result = validate("/storage/1234-ABCD/Backup")
        assertTrue(result is PathInput.Result.Ok)
    }

    @Test
    fun `missing folder is rejected`() {
        val result = validate("$root/Nope", exists = { false })
        assertEquals(PathInput.Error.NOT_FOUND, (result as PathInput.Result.Invalid).error)
    }

    @Test
    fun `outside storage is rejected`() {
        val result = validate("/data/local/tmp")
        assertEquals(PathInput.Error.OUTSIDE_STORAGE, (result as PathInput.Result.Invalid).error)
    }

    @Test
    fun `same folder as source is rejected`() {
        // The source is the folder the user is browsing and the destination
        // is that same folder: moving into itself would delete the data.
        val result = validate("$root/Download", sources = listOf("$root/Download"))
        assertEquals(PathInput.Error.SAME_AS_SOURCE, (result as PathInput.Result.Invalid).error)
    }

    @Test
    fun `folder into its own descendant is rejected`() {
        val result = validate("$root/Photos/2026", sources = listOf("$root/Photos"))
        assertEquals(PathInput.Error.INSIDE_SOURCE, (result as PathInput.Result.Invalid).error)
    }

    @Test
    fun `sibling with a shared name prefix is not a descendant`() {
        // "/storage/emulated/0/Photos2" must not be treated as inside
        // "/storage/emulated/0/Photos" (string-prefix bug class).
        val result = validate("$root/Photos2", sources = listOf("$root/Photos"))
        assertTrue(result is PathInput.Result.Ok)
        assertFalse(PathInput.isSameOrInside("$root/Photos2", "$root/Photos"))
        assertTrue(PathInput.isSameOrInside("$root/Photos/x", "$root/Photos"))
    }

    @Test
    fun `read-only folder is rejected`() {
        val result = validate("$root/System", writable = { false })
        assertEquals(PathInput.Error.NOT_WRITABLE, (result as PathInput.Result.Invalid).error)
    }

    @Test
    fun `empty input reports empty`() {
        assertEquals(PathInput.Error.EMPTY, (validate(" ") as PathInput.Result.Invalid).error)
    }

    @Test
    fun `relative input reports not absolute`() {
        assertEquals(PathInput.Error.NOT_ABSOLUTE, (validate("Download") as PathInput.Result.Invalid).error)
    }

    @Test
    fun `dot segments cannot bypass the source checks`() {
        // "/…/Download/../Download" is the source folder written differently;
        // a plain string compare would accept it and the move would delete
        // the folder it just copied into itself.
        val same = validate("$root/Download/../Download", sources = listOf("$root/Download"))
        assertEquals(PathInput.Error.SAME_AS_SOURCE, (same as PathInput.Result.Invalid).error)
        val inside = validate("$root/Photos/./2026", sources = listOf("$root/Photos"))
        assertEquals(PathInput.Error.INSIDE_SOURCE, (inside as PathInput.Result.Invalid).error)
    }

    @Test
    fun `dot segments cannot escape the storage roots`() {
        val escaped = validate("$root/../..")
        assertEquals(PathInput.Error.OUTSIDE_STORAGE, (escaped as PathInput.Result.Invalid).error)
    }

    @Test
    fun `canonicalize resolves dot segments lexically`() {
        assertEquals("/storage/emulated/0/Download", PathInput.canonicalize("/storage/emulated/0/./Download"))
        assertEquals("/", PathInput.canonicalize("/storage/../.."))
        assertEquals("/a", PathInput.canonicalize("/a/b/.."))
        assertEquals("/a/b", PathInput.canonicalize("/a/./b/"))
        assertEquals("/", PathInput.canonicalize("/"))
    }

    @Test
    fun `destination path keeps the file name`() {
        assertEquals("$root/Movies/a.txt", PathInput.destinationPath("$root/Movies", "$root/Download/a.txt"))
        assertEquals("/a.txt", PathInput.destinationPath("/", "/x/a.txt"))
        assertNull(PathInput.destinationPath(root, "/"))
        assertNull(PathInput.destinationPath(root, ".."))
    }
}
