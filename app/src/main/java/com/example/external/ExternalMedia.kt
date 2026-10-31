package com.example.external

import android.net.Uri
import com.example.MediaFile

private const val MEDIA_AUTHORITY = "media"

/**
 * v2.0.0: one-shot request for the media viewer opened by another app's
 * `ACTION_VIEW` (photos/videos).
 *
 * The viewer treats this as a first-class target: it opens fullscreen
 * immediately, and if the URI resolves to a file that is visible in the
 * Library it pages through the Library's image/video order. When the URI is
 * not in the Library the viewer shows a single page (no previous/next) and
 * leaving it always lands on the Library screen.
 */
data class ExternalViewerRequest(
    val uriString: String,
    /** MIME carried by the incoming intent, when the sender supplied one. */
    val mimeType: String?,
)

/**
 * Metadata resolved from the incoming URI. [path] and [mediaId] are best-effort:
 * MediaStore providers expose them, generic document providers may not.
 */
data class ExternalMediaInfo(
    val uriString: String,
    val mimeType: String,
    val displayName: String,
    val size: Long,
    val path: String?,
    val mediaId: Long,
    /**
     * Normalized MediaStore volume of the incoming URI (`external_primary`,
     * `1234-5678`, …). Null for non-MediaStore providers: id matching is only
     * safe when the volume is known.
     */
    val volume: String? = null,
    /** Best-effort last-modified time in ms (0 when the provider hides it). */
    val dateModified: Long = 0L,
)

/**
 * Match an incoming external URI against the Library's visible media order.
 *
 * Identity is checked in order of reliability:
 * 1. the exact content URI string (re-sharing a MediaStore URI),
 * 2. the resolved filesystem path (`DATA` / `file://`),
 * 3. the MediaStore `_ID` (Gmail/Gallery style `images/media/<id>` and
 *    documents-provider `image:<id>` URIs).
 *
 * Name/size matching is deliberately NOT used: two different files can share
 * a display name, and falsely enabling paging from a cloud/chat attachment
 * would be worse than showing a single image.
 */
fun matchLibraryFile(files: List<MediaFile>, info: ExternalMediaInfo): MediaFile? {
    files.firstOrNull { it.contentUri?.toString() == info.uriString }?.let { return it }
    val path = info.path
    if (!path.isNullOrBlank()) {
        files.firstOrNull { it.path == path }?.let { return it }
    }
    // MediaStore _ID is only unique per volume, and third-party providers may
    // use numeric path segments that are not MediaStore ids at all. Require a
    // known MediaStore volume and compare it, so an SD-card twin or a chat
    // attachment can never resolve to an unrelated Library photo.
    if (info.mediaId > 0 && info.volume != null) {
        files.firstOrNull { file ->
            file.id == info.mediaId && volumeOf(file.contentUri) == info.volume
        }?.let { return it }
    }
    return null
}

/** `external` is the legacy alias for the primary volume. */
fun normalizeVolume(volume: String?): String? = when {
    volume.isNullOrBlank() -> null
    volume == "external" -> "external_primary"
    else -> volume
}

/** MediaStore volume of a library content URI, or null for non-MediaStore URIs. */
fun volumeOf(uri: Uri?): String? {
    if (uri == null || uri.authority != MEDIA_AUTHORITY) return null
    return normalizeVolume(uri.pathSegments.firstOrNull())
}

/**
 * Library image/video order used when the external target is in the Library.
 * Mirrors `LibraryScreen` (oldest first, excluded folders hidden) so swiping
 * follows the same sequence as the Photos grid.
 */
fun libraryMediaOrder(files: List<MediaFile>, excludedFolders: Set<String>): List<MediaFile> =
    files
        .filter { file ->
            (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")) &&
                excludedFolders.none { excluded -> file.path.startsWith(excluded) }
        }
        .sortedBy { it.dateModified }

/**
 * Pager content for the external request: the Library order when the target
 * is visible there, otherwise exactly one page so horizontal swipes cannot
 * leave the incoming image.
 */
fun externalMediaList(
    libraryOrder: List<MediaFile>,
    matched: MediaFile?,
    externalFile: MediaFile,
): List<MediaFile> = if (matched != null) libraryOrder else listOf(externalFile)
