package com.example.files

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.MediaFile
import java.io.File

/**
 * Repository for local file discovery (MVI Model layer).
 *
 * Owns every filesystem / MediaStore query so [com.example.FileViewModel] only
 * orchestrates intents and publishes state. All methods hit disk or a
 * ContentResolver and must run off the main thread.
 */
class MediaRepository(private val context: Context) {

    /**
     * Canonical storage roots shared by normal and DeX UI: internal, SD card,
     * USB-OTG where mounted. Call off the main thread (hits [File.exists]).
     */
    fun storageRoots(): List<String> {
        return buildList {
            context.getExternalFilesDirs(null).mapNotNull { it?.absolutePath?.substringBefore("/Android/data/") }
                .filter { it.isNotBlank() }
                .forEach(::add)
            // SECONDARY_STORAGE covers removable volumes the framework does not
            // always surface (SD cards / USB-OTG on some vendors, incl. DeX docks).
            System.getenv("SECONDARY_STORAGE")?.split(':')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.forEach(::add)
            // Legacy fallback for devices where app-scoped dirs are unavailable.
            @Suppress("DEPRECATION")
            add(Environment.getExternalStorageDirectory().absolutePath)
        }.distinct().filter { File(it).exists() }
    }

    /** Primary shared root (replaces hard-coded /storage/emulated/0 comparisons). */
    fun rootPath(): String = storageRoots().firstOrNull()
        ?: context.getExternalFilesDir(null)
            ?.absolutePath?.substringBefore("/Android/") ?: "/sdcard"

    /** Canonical root check shared with UI. */
    fun isStorageRoot(path: String): Boolean {
        val abs = File(path).absolutePath
        val secondary = System.getenv("SECONDARY_STORAGE")?.split(':')
            ?.map { it.trim() }.orEmpty()
        val candidates = context.getExternalFilesDirs(null)
            .mapNotNull { it?.absolutePath?.substringBefore("/Android/data/") } +
            secondary + listOf("/storage/emulated/0", "/sdcard")
        return candidates.any { abs == it }
    }

    /**
     * Every indexed media file across all external volumes, keyed by path.
     *
     * Queries run in bounded LIMIT/OFFSET pages (stable `_ID` order) so a huge
     * library never materialises a giant single CursorWindow; callers still get
     * one merged list and keep their existing sort/filter logic untouched.
     */
    fun getAllMediaFiles(): List<MediaFile> {
        val mediaList = linkedMapOf<String, MediaFile>()
        val volumes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
        } else setOf("external")
        volumes.forEach { volume -> queryMediaVolume(volume, mediaList) }
        return mediaList.values.toList()
    }

    /** One volume, read in bounded pages. Call off the main thread. */
    private fun queryMediaVolume(volume: String, mediaList: LinkedHashMap<String, MediaFile>) {
        val useRelativePath = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        val projection = if (useRelativePath) {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
        } else {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
        }

        val roots = storageRoots()
        val fallbackRoot = rootPath()
        val contentUri = MediaStore.Files.getContentUri(volume)

        var offset = 0
        while (true) {
            // Stable _ID order keeps page boundaries deterministic across chunks.
            val sortOrder = "${MediaStore.Files.FileColumns._ID} ASC LIMIT $MEDIA_PAGE_SIZE OFFSET $offset"
            var rows = 0
            context.contentResolver.query(contentUri, projection, null, null, sortOrder)?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                val pathCol = if (useRelativePath) {
                    it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                } else {
                    it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                }

                while (it.moveToNext()) {
                    rows++
                    val id = it.getLong(idCol)
                    val displayName = it.getString(nameCol) ?: "unknown"

                    val path = if (useRelativePath) {
                        val relPath = it.getString(pathCol) ?: ""
                        val root = roots.firstOrNull { candidate ->
                            candidate.substringAfterLast('/').equals(volume, ignoreCase = true)
                        } ?: fallbackRoot
                        val fullPath = if (relPath.isNotEmpty()) "$root/$relPath$displayName" else "$root/$displayName"
                        fullPath.replace("//", "/")
                    } else {
                        it.getString(pathCol) ?: ""
                    }

                    val mimeType = it.getString(mimeCol) ?: ""

                    // We only care about media/documents, ignoring random binary/system files if possible
                    if (mimeType.isNotBlank() || displayName.endsWith(".apk", ignoreCase = true)) {
                        val finalMimeType = if (mimeType.isNotBlank()) mimeType else if (displayName.endsWith(".apk", true)) "application/vnd.android.package-archive" else ""
                        val itemUri = ContentUris.withAppendedId(contentUri, id)
                        mediaList[path] =
                            MediaFile(
                                id = id,
                                name = displayName,
                                path = path,
                                size = it.getLong(sizeCol),
                                mimeType = finalMimeType,
                                dateModified = it.getLong(dateCol) * 1000,
                                isDirectory = false,
                                contentUri = itemUri
                            )
                    }
                }
            }
            if (rows < MEDIA_PAGE_SIZE) break
            offset += MEDIA_PAGE_SIZE
        }
    }

    companion object {
        /** Rows per MediaStore page: small enough for CursorWindow, large enough to avoid chatty queries. */
        private const val MEDIA_PAGE_SIZE = 2000
    }

    /**
     * Local PDF and office documents: MediaStore matches plus a bounded
     * filesystem traversal for app-created scans and non-indexed files.
     */
    fun getAllDocumentFiles(): List<MediaFile> {
        val documents = linkedMapOf<String, MediaFile>()
        getAllMediaFiles().filter { it.isDocument() }.forEach { documents[it.path] = it }

        // Newly scanned PDFs and files which are not indexed by MediaStore (notably documents
        // in an app-created Documents directory) are found by a bounded filesystem traversal.
        val pending = java.util.ArrayDeque<File>()
        storageRoots().map(::File).filter(File::isDirectory).forEach(pending::add)
        var visited = 0
        while (pending.isNotEmpty() && visited < 50_000) {
            val directory = pending.removeFirst()
            val children = directory.listFiles() ?: continue
            for (child in children) {
                if (++visited > 50_000) break
                if (child.isDirectory) {
                    if (child.name !in setOf("Android", ".thumbnails")) pending.add(child)
                } else if (child.isDocument()) {
                    documents.putIfAbsent(child.absolutePath, child.toMediaFile())
                }
            }
        }
        return documents.values.toList()
    }

    fun MediaFile.isDocument(): Boolean = isDocumentName(name) ||
        mimeType == "application/pdf" || mimeType.startsWith("application/vnd.openxmlformats-officedocument") ||
        mimeType.startsWith("application/vnd.oasis.opendocument") || mimeType == "application/msword" ||
        mimeType == "application/vnd.ms-powerpoint" || mimeType == "application/rtf" ||
        mimeType == "text/plain" || mimeType == "text/csv" || mimeType == "text/markdown" ||
        mimeType == "application/json" || mimeType == "text/html"

    fun File.isDocument(): Boolean = isFile && isDocumentName(name)

    fun File.toMediaFile(): MediaFile = MediaFile(
        id = -1,
        name = name,
        path = absolutePath,
        size = length(),
        mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: getMimeType(extension),
        dateModified = lastModified(),
        contentUri = null
    )

    fun isDocumentName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf(
        "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "markdown", "log", "csv", "tsv", "json", "html", "htm",
        "xls", "xlsx", "ods", "ppt", "pptx", "odp", "epub", "tex", "ltx"
    )

    fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "tif", "tiff", "svg" -> "image/*"
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv" -> "video/*"
            "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus" -> "audio/*"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "rtf" -> "application/rtf"
            "txt", "log", "ini", "conf", "cfg", "yaml", "yml", "properties" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            "tex", "ltx" -> "text/x-tex"
            "csv", "tsv" -> "text/csv"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "epub" -> "application/epub+zip"
            "zip" -> "application/zip"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
