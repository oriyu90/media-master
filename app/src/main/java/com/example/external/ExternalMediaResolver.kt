package com.example.external

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

/**
 * v2.0.0: resolve a URI handed over by another app into display metadata plus
 * the best-effort Library identity ([ExternalMediaInfo.mediaId] /
 * [ExternalMediaInfo.path]).
 *
 * Runs on IO (callers wrap it in `withContext(Dispatchers.IO)`); every query is
 * guarded because third-party providers may reject MediaStore columns.
 */
fun resolveExternalMediaInfo(context: Context, uri: Uri, mimeHint: String?): ExternalMediaInfo {
    var displayName = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment
        ?: "media"
    var size = 0L
    var path: String? = if (uri.scheme == "file") uri.path else null
    // Only MediaStore-authority URIs expose a trustworthy _ID. A numeric last
    // path segment from WhatsApp/Telegram/Gmail (or a file:// path) is not a
    // MediaStore id and must never page the Library.
    val isMediaAuthority = uri.authority == MediaStore.AUTHORITY
    val volume = if (isMediaAuthority) normalizeVolume(uri.pathSegments.firstOrNull()) else null
    var mediaId = if (isMediaAuthority) parseMediaId(uri.lastPathSegment) else -1L
    var dateModified = path?.let { runCatching { java.io.File(it).lastModified() }.getOrDefault(0L) } ?: 0L

    // OpenableColumns works for MediaStore, SAF documents and most providers.
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    }

    // MediaStore extras: only for the MediaStore authority, and never assume
    // the provider supports these columns.
    if (isMediaAuthority) runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                if (idIndex >= 0 && !cursor.isNull(idIndex)) mediaId = cursor.getLong(idIndex)
                val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataIndex >= 0 && !cursor.isNull(dataIndex)) {
                    cursor.getString(dataIndex)?.takeIf { it.isNotBlank() }?.let { path = it }
                }
                val modifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                    val seconds = cursor.getLong(modifiedIndex)
                    if (seconds > 0) dateModified = seconds * 1000
                }
            }
        }
    }

    val mime = mimeHint?.takeIf { it.isNotBlank() && it != "*/*" }
        ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
        ?: mimeFromName(displayName)
        ?: "image/*"
    return ExternalMediaInfo(
        uriString = uri.toString(),
        mimeType = mime,
        displayName = displayName,
        size = size,
        path = path,
        mediaId = mediaId,
        volume = volume,
        dateModified = dateModified,
    )
}

/** Parse `123`, `image:123` or `video:123` into the MediaStore id (else -1). */
internal fun parseMediaId(segment: String?): Long {
    if (segment.isNullOrBlank()) return -1L
    segment.toLongOrNull()?.let { return it }
    return segment.substringAfterLast(':').toLongOrNull() ?: -1L
}

internal fun mimeFromName(name: String): String? {
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isEmpty()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
}
