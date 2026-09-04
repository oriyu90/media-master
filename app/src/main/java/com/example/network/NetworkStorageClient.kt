package com.example.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.File
import java.net.URLDecoder
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads directory listings and files from SMB and WebDAV servers.
 *
 * Every public method runs on [Dispatchers.IO], is cancellation-aware, closes
 * its own resources, and returns a [Result] — a bad host, wrong password, or
 * timeout yields `Result.failure`, never a crash.
 */
class NetworkStorageClient {

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun list(
        location: NetworkLocation,
        password: String,
        relativePath: String,
    ): Result<List<NetworkEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            when (location.protocol) {
                NetworkProtocol.SMB -> smbList(location, password, relativePath)
                NetworkProtocol.WEBDAV -> webdavList(location, password, relativePath)
            }
        }
    }

    /** Streams the remote file at [relativePath] into [dest]. */
    suspend fun download(
        location: NetworkLocation,
        password: String,
        relativePath: String,
        dest: File,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (location.protocol) {
                NetworkProtocol.SMB -> smbDownload(location, password, relativePath, dest)
                NetworkProtocol.WEBDAV -> webdavDownload(location, password, relativePath, dest)
            }
        }
    }

    // ----------------------------------------------------------------- SMB ---

    private inline fun <T> withSmbShare(
        location: NetworkLocation,
        password: String,
        block: (DiskShare) -> T,
    ): T {
        SMBClient().connect(location.host, location.effectivePort).use { conn ->
            val domain = location.username.substringBefore('\\', "").ifEmpty { null }
            val user = location.username.substringAfter('\\')
            val auth = AuthenticationContext(user, password.toCharArray(), domain)
            val session = conn.authenticate(auth)
            (session.connectShare(location.share) as DiskShare).use { share ->
                return block(share)
            }
        }
    }

    private fun joinSmb(base: String, rel: String): String {
        val parts = listOf(base, rel).flatMap { it.split('/', '\\') }.filter { it.isNotBlank() }
        return parts.joinToString("\\")
    }

    private fun smbList(location: NetworkLocation, password: String, relativePath: String): List<NetworkEntry> =
        withSmbShare(location, password) { share ->
            val dir = joinSmb(location.basePath, relativePath)
            share.list(dir)
                .filter { it.fileName != "." && it.fileName != ".." }
                .map { info ->
                    val isDir = com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value and info.fileAttributes != 0L
                    NetworkEntry(
                        name = info.fileName,
                        relativePath = listOf(relativePath, info.fileName).filter { it.isNotBlank() }.joinToString("/"),
                        isDirectory = isDir,
                        size = info.endOfFile,
                        lastModified = info.changeTime?.toEpochMillis() ?: 0L,
                    )
                }
                .sortedWith(compareByDescending<NetworkEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }

    private fun smbDownload(location: NetworkLocation, password: String, relativePath: String, dest: File) {
        withSmbShare(location, password) { share ->
            val path = joinSmb(location.basePath, relativePath)
            share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { remote ->
                remote.inputStream.use { input -> dest.outputStream().use(input::copyTo) }
            }
        }
    }

    // -------------------------------------------------------------- WebDAV ---

    private fun webdavBaseUrl(location: NetworkLocation): String {
        val scheme = if (location.effectivePort == 80) "http" else "https"
        val segments = listOf(location.share, location.basePath)
            .flatMap { it.split('/') }.filter { it.isNotBlank() }
        val path = if (segments.isEmpty()) "" else "/" + segments.joinToString("/")
        return "$scheme://${location.host}:${location.effectivePort}$path"
    }

    private fun webdavUrl(location: NetworkLocation, relativePath: String): String {
        val rel = relativePath.split('/').filter { it.isNotBlank() }.joinToString("/") { encodePathSegment(it) }
        return webdavBaseUrl(location).trimEnd('/') + if (rel.isEmpty()) "/" else "/$rel"
    }

    private fun webdavList(location: NetworkLocation, password: String, relativePath: String): List<NetworkEntry> {
        val url = webdavUrl(location, relativePath)
        val body = PROPFIND_BODY.toRequestBody("application/xml".toMediaType())
        val req = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .header("Depth", "1")
            .apply { authHeader(location.username, password)?.let { header("Authorization", it) } }
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val xml = resp.body?.byteStream() ?: error("empty response")
            val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(xml)
            val responses = doc.getElementsByTagNameNS("*", "response")
            val selfPath = java.net.URI(url).path.trimEnd('/')
            val out = ArrayList<NetworkEntry>()
            for (i in 0 until responses.length) {
                val el = responses.item(i) as? Element ?: continue
                val href = el.textForChild("href")?.let { java.net.URI(it).path } ?: continue
                if (href.trimEnd('/') == selfPath) continue // skip the folder itself
                val isDir = el.getElementsByTagNameNS("*", "collection").length > 0
                val size = el.textForChild("getcontentlength")?.toLongOrNull() ?: 0L
                val name = URLDecoder.decode(href.trimEnd('/').substringAfterLast('/'), "UTF-8")
                out += NetworkEntry(
                    name = name,
                    relativePath = listOf(relativePath, name).filter { it.isNotBlank() }.joinToString("/"),
                    isDirectory = isDir,
                    size = size,
                    lastModified = 0L,
                )
            }
            return out.sortedWith(compareByDescending<NetworkEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun webdavDownload(location: NetworkLocation, password: String, relativePath: String, dest: File) {
        val req = Request.Builder()
            .url(webdavUrl(location, relativePath))
            .apply { authHeader(location.username, password)?.let { header("Authorization", it) } }
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.byteStream()?.use { input -> dest.outputStream().use(input::copyTo) } ?: error("empty response")
        }
    }

    private fun authHeader(username: String, password: String): String? {
        if (username.isBlank()) return null
        val token = android.util.Base64.encodeToString(
            "$username:$password".toByteArray(), android.util.Base64.NO_WRAP,
        )
        return "Basic $token"
    }

    private fun encodePathSegment(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun Element.textForChild(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim()?.takeIf { it.isNotEmpty() } else null
    }

    companion object {
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
    }
}

