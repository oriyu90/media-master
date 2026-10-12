package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNamesTest {

    @Test
    fun `volumeDisplayName returns last segment`() {
        assertEquals("ABCD-1234", FolderNames.volumeDisplayName("/storage/ABCD-1234"))
        assertEquals("0", FolderNames.volumeDisplayName("/storage/emulated/0"))
        assertEquals("", FolderNames.volumeDisplayName("/"))
        assertEquals("Download", FolderNames.volumeDisplayName("/storage/emulated/0/Download/"))
    }

    @Test
    fun `folderDisplayName never returns blank`() {
        assertEquals("Download", FolderNames.folderDisplayName("/storage/emulated/0/Download"))
        assertEquals("/", FolderNames.folderDisplayName("/"))
        assertEquals("ABCD-1234", FolderNames.folderDisplayName("/storage/ABCD-1234/"))
    }

    @Test
    fun `storageRootTitle uses real volume name for external`() {
        assertEquals("Internal", FolderNames.storageRootTitle("/storage/emulated/0", true, "Internal", "External"))
        assertEquals("ABCD-1234", FolderNames.storageRootTitle("/storage/ABCD-1234", false, "Internal", "External"))
        assertEquals("External", FolderNames.storageRootTitle("/", false, "Internal", "External"))
    }

    @Test
    fun `disambiguateParentLabels keeps unique names short`() {
        val labels = FolderNames.disambiguateParentLabels(
            listOf("/sdcard/DCIM", "/sdcard/Download"),
        )
        assertEquals("DCIM", labels["/sdcard/DCIM"])
        assertEquals("Download", labels["/sdcard/Download"])
    }

    @Test
    fun `disambiguateParentLabels expands duplicate names to full path`() {
        val labels = FolderNames.disambiguateParentLabels(
            listOf("/sdcard/A/DCIM", "/usb/B/DCIM"),
        )
        assertEquals("/sdcard/A/DCIM", labels["/sdcard/A/DCIM"])
        assertEquals("/usb/B/DCIM", labels["/usb/B/DCIM"])
    }
}
