package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.12.0: pins the picker MIME filter — wildcard accepts everything, a
 * major-type prefix matches its family, otherwise exact match; unknown file
 * types are never hidden.
 */
class MimeMatchesTest {

    @Test
    fun `wildcard accepts everything`() {
        assertTrue(mimeMatches("*/*", "image/png"))
        assertTrue(mimeMatches("", "application/pdf"))
        assertTrue(mimeMatches("  ", "text/plain"))
    }

    @Test
    fun `major type prefix matches`() {
        assertTrue(mimeMatches("image/*", "image/jpeg"))
        assertTrue(mimeMatches("application/*", "application/pdf"))
        assertFalse(mimeMatches("image/*", "video/mp4"))
    }

    @Test
    fun `exact match is case insensitive`() {
        assertTrue(mimeMatches("application/pdf", "application/PDF"))
        assertFalse(mimeMatches("application/pdf", "application/zip"))
    }

    @Test
    fun `unknown file types are accepted`() {
        assertTrue(mimeMatches("image/*", ""))
        assertTrue(mimeMatches("application/pdf", "application/octet-stream"))
    }
}
