package com.example.ui

import com.example.SortOption
import com.example.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FileDetailsTest {

    @Test
    fun `typeLabel prefers extension uppercase`() {
        assertEquals("PDF", FileDetails.typeLabel("report.pdf", "application/pdf"))
        assertEquals("ZIP", FileDetails.typeLabel("a.ZIP", "application/zip"))
        assertEquals("MP4", FileDetails.typeLabel("clip.mp4", "video/mp4"))
    }

    @Test
    fun `typeLabel falls back to mime subtype`() {
        assertEquals("JPEG", FileDetails.typeLabel("blob", "image/jpeg"))
        assertEquals("", FileDetails.typeLabel("noext", ""))
    }

    @Test
    fun `detailParts honours visibility and skips directories`() {
        val parts = FileDetails.detailParts(
            sizeText = "1.2 MB",
            modifiedText = "2026/09/29",
            createdText = "2026/09/28",
            typeText = "PDF",
            showSize = true,
            showModified = true,
            showCreated = false,
            showType = true,
            isDirectory = false,
        )
        assertEquals(listOf("1.2 MB", "2026/09/29", "PDF"), parts)
        assertEquals(emptyList<String>(), FileDetails.detailParts(
            sizeText = "1.2 MB", modifiedText = "d", createdText = "c", typeText = "PDF",
            showSize = true, showModified = true, showCreated = true, showType = true,
            isDirectory = true,
        ))
    }

    @Test
    fun `detailParts hides created when identical to modified`() {
        val parts = FileDetails.detailParts(
            sizeText = null, modifiedText = "2026/09/29", createdText = "2026/09/29",
            typeText = null, showSize = true, showModified = true,
            showCreated = true, showType = true, isDirectory = false,
        )
        assertEquals(listOf("2026/09/29"), parts)
    }

    @Test
    fun `joinParts uses middle dot separator`() {
        assertEquals("a • b", FileDetails.joinParts(listOf("a", "b")))
        assertEquals("", FileDetails.joinParts(emptyList()))
    }

    @Test
    fun `fileBrowserDefault is LIST on desktop, GRID on phone`() {
        assertEquals(ViewMode.LIST, DesktopViewModes.fileBrowserDefault(true))
        assertEquals(ViewMode.GRID, DesktopViewModes.fileBrowserDefault(false))
    }

    @Test
    fun `categoryDefault is LIST on desktop regardless of gallery`() {
        assertEquals(ViewMode.LIST, DesktopViewModes.categoryDefault(true, true))
        assertEquals(ViewMode.LIST, DesktopViewModes.categoryDefault(true, false))
        assertEquals(ViewMode.GRID, DesktopViewModes.categoryDefault(false, true))
        assertEquals(ViewMode.LIST, DesktopViewModes.categoryDefault(false, false))
    }

    @Test
    fun `sortForHeaderColumn maps Created to DATE_CREATED`() {
        assertEquals(SortOption.NAME, DesktopViewModes.sortForHeaderColumn(DesktopViewModes.HeaderColumn.NAME))
        assertEquals(SortOption.SIZE, DesktopViewModes.sortForHeaderColumn(DesktopViewModes.HeaderColumn.SIZE))
        assertEquals(SortOption.DATE_CREATED, DesktopViewModes.sortForHeaderColumn(DesktopViewModes.HeaderColumn.MODIFIED))
        assertEquals(SortOption.DATE_CREATED, DesktopViewModes.sortForHeaderColumn(DesktopViewModes.HeaderColumn.CREATED))
        assertEquals(SortOption.TYPE, DesktopViewModes.sortForHeaderColumn(DesktopViewModes.HeaderColumn.TYPE))
    }
}
