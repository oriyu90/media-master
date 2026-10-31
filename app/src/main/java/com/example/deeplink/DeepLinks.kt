package com.example.deeplink

import android.content.Intent
import android.net.Uri

/**
 * External-access surface for Media Master.
 *
 * Other apps can open a specific Media Master feature in two ways:
 *
 *  1. The dedicated `mediamaster://` scheme — an explicit, documented per-feature
 *     entry point (NOT marked BROWSABLE, so arbitrary web pages cannot trigger it):
 *
 *       mediamaster://home
 *       mediamaster://library
 *       mediamaster://audio
 *       mediamaster://documents
 *       mediamaster://manage
 *       mediamaster://apps
 *       mediamaster://clean
 *       mediamaster://settings
 *       mediamaster://browse?path=/storage/emulated/0/Download
 *       mediamaster://edit/image?uri=<content-uri>
 *       mediamaster://edit/video?uri=<content-uri>
 *
 *  2. Standard Android intents, so Media Master shows up in the system
 *     "Open with" / "Edit with" chooser:
 *
 *       ACTION_EDIT  of an image       -> image editor
 *       ACTION_EDIT  of a video        -> video editor
 *       ACTION_VIEW  of image/video      -> the media viewer, opened fullscreen
 *                                          on the incoming URI. If the file is
 *                                          visible in the Library it pages in
 *                                          the Library order; otherwise it is a
 *                                          single page and closing/dismissing
 *                                          lands on the Library screen.
 *       ACTION_VIEW  of audio             -> library (existing behavior)
 *       ACTION_VIEW  of a directory    -> file browser at that path
 *       ACTION_VIEW  of text/csv/json/markdown/pdf/word/powerpoint/zip
 *                                      -> the universal document viewer, opened
 *                                         directly on the incoming URI (Media
 *                                         Master does not need to already know
 *                                         about the file)
 *
 *  3. Standard file-manager picking (v1.12.0): Media Master answers the
 *     system file picker so other apps can receive one user-picked file:
 *
 *       ACTION_GET_CONTENT / ACTION_OPEN_DOCUMENT (any type)
 *                                      -> in-app file picker; the tapped file
 *                                         is returned via setResult + finish.
 *                                         Handled via [PickRequest.parse], not
 *                                         [resolve], so a picker launch never
 *                                         turns into a normal deep link.
 *
 * [resolve] converts an incoming [Intent] into an internal navigation route
 * string, or `null` when the request is not understood. Callers must treat
 * `null` as "just open the app normally" — never crash.
 *
 * Destructive operations (delete, uninstall, restore-from-backup) are
 * intentionally NOT reachable from an external intent; they always require an
 * explicit in-app confirmation.
 */
object DeepLinks {

    const val SCHEME = "mediamaster"

    /** Routes that take no arguments and can be opened directly by host name. */
    private val SIMPLE_HOSTS = setOf(
        "home", "library", "audio", "documents", "manage", "apps", "clean", "settings"
    )

    /** Resolve a full [Intent] (cold start or onNewIntent) to an internal route, or null. */
    fun resolve(intent: Intent?): String? {
        if (intent == null) return null
        val data = intent.data
        val action = intent.action
        val type = intent.type

        // 1. Our own scheme.
        if (data != null && data.scheme.equals(SCHEME, ignoreCase = true)) {
            return resolveAppScheme(data)
        }

        // 2. Standard system intents.
        return when (action) {
            Intent.ACTION_EDIT -> when {
                type?.startsWith("image/") == true && data != null -> route("imageEditor", data.toString())
                type?.startsWith("video/") == true && data != null -> route("videoEditor", data.toString())
                else -> null
            }
            Intent.ACTION_VIEW -> when {
                // v1.12.0: also accept resource/folder (used by several
                // third-party "open folder" senders). Only real filesystem
                // paths are honored — a content:// provider path is not a
                // raw path, so it falls back to Manage instead of a bogus
                // navigation that would show an empty/error browser.
                type == "vnd.android.document/directory" || type == "resource/folder" -> {
                    val rawPath = data?.path
                    val usePath = when {
                        rawPath.isNullOrBlank() -> null
                        data?.scheme == "file" -> rawPath
                        rawPath.startsWith("/storage/") || rawPath.startsWith("/sdcard") -> rawPath
                        else -> null
                    }
                    if (usePath != null) "file_browser?path=${Uri.encode(usePath)}" else "manage"
                }
                type?.startsWith("image/") == true || type?.startsWith("video/") == true -> {
                    if (data != null && isLocalMediaUri(data)) {
                        externalViewerRoute(data.toString(), type)
                    } else {
                        // Remote URLs and missing data keep the previous
                        // Library behavior instead of an unreadable file URI.
                        "library"
                    }
                }
                type?.startsWith("audio/") == true -> "library"
                // v2.0.0: senders that only carry a media file name (generic
                // type) still open in the media viewer instead of a browser.
                data != null && isLocalMediaUri(data) &&
                    mediaFallbackMime(type, data) != null ->
                    externalViewerRoute(data.toString(), mediaFallbackMime(type, data))
                data != null && isDocumentViewerType(type) -> route("docViewer", data.toString())
                // v1.10.0: ZIP from external apps. Some senders omit the MIME
                // type (null / */* / octet-stream) and only carry a .zip URI,
                // so fall back to the extension in that case. Explicit ZIP
                // MIMEs are covered by isDocumentViewerType above.
                data != null && isZipFallback(type, data) -> route("docViewer", data.toString())
                else -> null
            }
            else -> null
        }
    }

    /**
     * v2.0.0: route for a media file handed over by another app. The viewer
     * decodes its own arguments, so the URI and MIME are encoded as separate
     * path/query parts.
     */
    fun externalViewerRoute(uriString: String, mimeType: String?): String {
        val base = "externalViewer/${Uri.encode(uriString)}"
        return if (mimeType.isNullOrBlank() || mimeType == "*/*") {
            base
        } else {
            "$base?mime=${Uri.encode(mimeType)}"
        }
    }

    /** Common image/video extensions used when the sender omits the MIME. */
    /** Only local providers can be opened by the viewer (no http/rtsp). */
    private fun isLocalMediaUri(uri: Uri?): Boolean =
        uri?.scheme == "content" || uri?.scheme == "file"

    private val MEDIA_VIEW_MIME_BY_EXTENSION = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
        "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif",
        "mp4" to "video/mp4", "m4v" to "video/x-m4v", "mkv" to "video/x-matroska",
        "webm" to "video/webm", "3gp" to "video/3gpp", "mov" to "video/quicktime",
    )

    /**
     * v2.0.0: MIME inferred from the file name for image/video senders that
     * pass a wildcard or no type at all. Only fires for generic types so an
     * explicit document MIME can never be hijacked.
     */
    internal fun mediaFallbackMime(type: String?, data: Uri): String? {
        if (type != null && type != "*/*" && type != "application/octet-stream") return null
        val name = runCatching { data.lastPathSegment.orEmpty() }.getOrDefault("")
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return null
        return MEDIA_VIEW_MIME_BY_EXTENSION[extension]
    }

    private fun resolveAppScheme(uri: Uri): String? {
        val host = uri.host?.lowercase().orEmpty()
        return when {
            host in SIMPLE_HOSTS -> host
            host == "browse" -> {
                val path = uri.getQueryParameter("path")
                if (!path.isNullOrBlank() && path.startsWith("/")) {
                    "file_browser?path=${Uri.encode(path)}"
                } else {
                    "manage"
                }
            }
            host == "edit" -> {
                val kind = uri.pathSegments.firstOrNull()?.lowercase()
                val target = uri.getQueryParameter("uri")?.takeIf { it.isNotBlank() } ?: return null
                when (kind) {
                    "image" -> route("imageEditor", target)
                    "video" -> route("videoEditor", target)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun route(base: String, arg: String) = "$base/${Uri.encode(arg)}"

    /**
     * MIME types the universal document viewer (`docViewer`) can open.
     * Legacy `.doc`/`.ppt` are intentionally excluded — see [com.example.viewer.ViewerKind.EXTERNAL_ONLY].
     * v1.10.0 adds ZIP (display + safe extract).
     */
    private val DOCUMENT_VIEWER_TYPES = setOf(
        "text/plain", "text/csv", "text/markdown", "text/x-tex", "application/json", "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/zip", "application/x-zip-compressed", "application/x-zip",
    )

    private fun isDocumentViewerType(type: String?): Boolean = type != null && type in DOCUMENT_VIEWER_TYPES

    /**
     * Extension fallback for ZIP senders that omit the MIME type.
     * Only fires for null/generic types to avoid hijacking unrelated files.
     */
    internal fun isZipFallback(type: String?, data: Uri): Boolean {
        val generic = type == null || type == "*/*" || type == "application/octet-stream"
        if (!generic) return false
        val name = runCatching {
            data.lastPathSegment.orEmpty()
        }.getOrDefault("")
        return name.lowercase().endsWith(".zip")
    }
}

/**
 * v1.12.0 file-manager picking: a parsed `ACTION_GET_CONTENT` /
 * `ACTION_OPEN_DOCUMENT` request. Pure data + [parse] so unit tests pin it
 * without Android.
 *
 * @param mimeType requested type (wildcard when the sender did not care).
 * @param allowMultiple sender's `EXTRA_ALLOW_MULTIPLE` (honored as
 *   single-return: pickers that need exactly one file keep working, and
 *   multi-needing callers still receive a valid single URI).
 * @param persistable true for `ACTION_OPEN_DOCUMENT` (long-lived access
 *   expected). Media Master returns a readable URI grant; callers that
 *   require a persistable `DocumentsProvider` tree should use the system
 *   picker instead — this limitation is documented, never a crash.
 */
data class PickRequest(
    val mimeType: String,
    val allowMultiple: Boolean,
    val persistable: Boolean,
) {
    companion object {
        fun parse(intent: Intent?): PickRequest? {
            if (intent == null) return null
            val action = intent.action ?: return null
            if (action != Intent.ACTION_GET_CONTENT && action != Intent.ACTION_OPEN_DOCUMENT) return null
            return PickRequest(
                mimeType = intent.type?.takeUnless { it.isBlank() } ?: "*/*",
                allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
                persistable = action == Intent.ACTION_OPEN_DOCUMENT,
            )
        }
    }
}

/**
 * v1.12.0 desktop doc-tab route builder. Pure so unit tests pin it:
 * `docViewer/{uri}[?path=...]`, mirroring the NavHost route shape.
 */
internal fun docTabRoute(uriString: String, path: String?): String {
    val base = "docViewer/${Uri.encode(uriString)}"
    return if (path != null) "$base?path=${Uri.encode(path)}" else base
}
