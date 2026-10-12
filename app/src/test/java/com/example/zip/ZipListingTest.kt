package com.example.zip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipListingTest {

    private fun zipBytes(entries: List<Pair<String, String>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun `lists entries in archive order`() {
        val bytes = zipBytes(listOf("a.txt" to "hello", "dir/b.txt" to "world"))
        val entries = ZipListing.listEntries(ByteArrayInputStream(bytes))
        assertEquals(listOf("a.txt", "dir/b.txt"), entries.map { it.name })
        assertFalse(entries.any { it.isDirectory })
    }

    @Test
    fun `corrupt input degrades to empty list, never throws`() {
        val entries = ZipListing.listEntries(ByteArrayInputStream("not a zip".toByteArray()))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `isZipName matches extension case-insensitively`() {
        assertTrue(ZipListing.isZipName("archive.ZIP"))
        assertTrue(ZipListing.isZipName("  photo.zip  "))
        assertFalse(ZipListing.isZipName("photo.png"))
        assertFalse(ZipListing.isZipName(""))
    }

    @Test
    fun `isZipMime matches known ZIP types only`() {
        assertTrue(ZipListing.isZipMime("application/zip"))
        assertTrue(ZipListing.isZipMime("application/x-zip-compressed"))
        assertTrue(ZipListing.isZipMime("application/x-zip"))
        assertFalse(ZipListing.isZipMime("application/octet-stream"))
        assertFalse(ZipListing.isZipMime(null))
        assertFalse(ZipListing.isZipMime("application/pdf"))
    }

    @Test
    fun `isSafeEntryName rejects traversal and absolute paths`() {
        assertTrue(ZipListing.isSafeEntryName("docs/a.txt"))
        assertTrue(ZipListing.isSafeEntryName("a.txt"))
        assertFalse(ZipListing.isSafeEntryName(""))
        assertFalse(ZipListing.isSafeEntryName("/abs/path.txt"))
        assertFalse(ZipListing.isSafeEntryName("../escape.txt"))
        assertFalse(ZipListing.isSafeEntryName("a/../../b.txt"))
        assertFalse(ZipListing.isSafeEntryName("C:\\win.txt"))
    }
}
