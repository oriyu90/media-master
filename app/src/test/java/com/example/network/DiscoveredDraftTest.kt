package com.example.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.0 testing seam: [discoveredToDraft] is pure JVM (no Application /
 * DataStore / EncryptedSharedPreferences), so the LAN prefill mapping is
 * covered without Robolectric.
 */
class DiscoveredDraftTest {

    @Test
    fun `null hint defaults to SMB`() {
        val draft = discoveredToDraft(
            DiscoveredDevice(host = "192.168.1.10", port = 445, serviceName = "NAS", hint = null),
            id = "id-1",
        )
        assertEquals(NetworkProtocol.SMB, draft.protocol)
        assertEquals("192.168.1.10", draft.host)
        assertEquals(445, draft.port)
        assertEquals("id-1", draft.id)
    }

    @Test
    fun `webdav hint is preserved`() {
        val draft = discoveredToDraft(
            DiscoveredDevice(host = "nas.local", port = 443, serviceName = "Files", hint = NetworkProtocol.WEBDAV),
            id = "id-2",
        )
        assertEquals(NetworkProtocol.WEBDAV, draft.protocol)
        assertEquals(443, draft.effectivePort)
    }

    @Test
    fun `long service names are capped and credentials stay empty`() {
        val draft = discoveredToDraft(
            DiscoveredDevice(host = "h", port = 445, serviceName = "x".repeat(100), hint = null),
            id = "id-3",
        )
        assertTrue(draft.name.length <= 48)
        assertEquals("", draft.share)
        assertEquals("", draft.basePath)
        assertEquals("", draft.username)
    }
}
