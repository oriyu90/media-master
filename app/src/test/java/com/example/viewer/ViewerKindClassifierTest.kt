package com.example.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerKindClassifierTest {

    @Test
    fun `classifies by extension when mime type is missing`() {
        assertEquals(ViewerKind.CSV, ViewerKindClassifier.classify("data.csv", null))
        assertEquals(ViewerKind.JSON, ViewerKindClassifier.classify("data.json", null))
        assertEquals(ViewerKind.MARKDOWN, ViewerKindClassifier.classify("README.md", null))
        assertEquals(ViewerKind.PDF, ViewerKindClassifier.classify("report.pdf", null))
        assertEquals(ViewerKind.DOCX, ViewerKindClassifier.classify("letter.docx", null))
        assertEquals(ViewerKind.PPTX, ViewerKindClassifier.classify("deck.pptx", null))
        assertEquals(ViewerKind.TEXT, ViewerKindClassifier.classify("notes.txt", null))
    }

    @Test
    fun `classifies by mime type when extension is unhelpful`() {
        assertEquals(ViewerKind.CSV, ViewerKindClassifier.classify("export", "text/csv"))
        assertEquals(ViewerKind.JSON, ViewerKindClassifier.classify("blob", "application/json"))
        assertEquals(ViewerKind.PDF, ViewerKindClassifier.classify("blob", "application/pdf"))
        assertEquals(ViewerKind.TEXT, ViewerKindClassifier.classify("blob", "text/x-log"))
    }

    @Test
    fun `legacy office formats are external-only, not hex`() {
        assertEquals(ViewerKind.EXTERNAL_ONLY, ViewerKindClassifier.classify("old.doc", "application/msword"))
        assertEquals(ViewerKind.EXTERNAL_ONLY, ViewerKindClassifier.classify("old.ppt", "application/vnd.ms-powerpoint"))
    }

    @Test
    fun `unknown binary falls back to hex instead of being refused`() {
        assertEquals(ViewerKind.HEX, ViewerKindClassifier.classify("firmware.bin", "application/octet-stream"))
        assertEquals(ViewerKind.HEX, ViewerKindClassifier.classify("noext", null))
    }
}
