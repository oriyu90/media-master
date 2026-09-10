package com.example

import android.app.Application
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

import android.net.Uri
import android.content.ContentUris

data class MediaFile(
    val id: Long,
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val dateModified: Long,
    val isDirectory: Boolean = false,
    val contentUri: Uri? = null
)


enum class SortOption { NAME, DATE_CREATED, TYPE, SIZE }
enum class ViewMode { LIST, GRID }

sealed class ViewState {
    object Loading : ViewState()
    data class Success(val files: List<MediaFile>, val currentPath: String) : ViewState()
    data class Error(val message: String) : ViewState()
}

class FileViewModel(application: Application) : AndroidViewModel(application) {
    private val _fileTreeState = MutableStateFlow<ViewState>(ViewState.Loading)
    val fileTreeState: StateFlow<ViewState> = _fileTreeState.asStateFlow()

    private val _mediaState = MutableStateFlow<ViewState>(ViewState.Loading)
    val mediaState: StateFlow<ViewState> = _mediaState.asStateFlow()

    private val _documentsState = MutableStateFlow<ViewState>(ViewState.Loading)
    val documentsState: StateFlow<ViewState> = _documentsState.asStateFlow()

    private val _duplicateFiles = MutableStateFlow<List<List<MediaFile>>>(emptyList())
    val duplicateFiles: StateFlow<List<List<MediaFile>>> = _duplicateFiles.asStateFlow()

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()
    
    
    private val _sortOption = MutableStateFlow(SortOption.NAME)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _categoryViewMode = MutableStateFlow(ViewMode.LIST)
    val categoryViewMode: StateFlow<ViewMode> = _categoryViewMode.asStateFlow()

    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _showExcludedInManage = MutableStateFlow(false)
    val showExcludedInManage: StateFlow<Boolean> = _showExcludedInManage.asStateFlow()

    // Hallmark v1.0.0: single reload job so parallel Loading/Success writes
    // can no longer race (late error no longer clobbers fresh success).
    private var reloadJob: Job? = null

    init {
        val sharedPrefs = application.getSharedPreferences("media_master_prefs", Context.MODE_PRIVATE)
        // Atomic read; writes below always go through update + single apply().
        _excludedFolders.update { sharedPrefs.getStringSet("excluded_folders", emptySet()) ?: emptySet() }
    }

    fun toggleShowExcludedInManage() {
        _showExcludedInManage.update { !it }
    }

    fun setCategoryViewMode(mode: ViewMode) {
        _categoryViewMode.update { mode }
    }

    private fun persistExcludedLocked(current: Set<String>) {
        getApplication<Application>().getSharedPreferences("media_master_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("excluded_folders", current).apply()
    }

    fun addExcludedFolder(path: String) {
        var snapshot = emptySet<String>()
        _excludedFolders.update { current ->
            (current + path).also { snapshot = it }
        }
        persistExcludedLocked(snapshot)
    }

    fun removeExcludedFolder(path: String) {
        var snapshot = emptySet<String>()
        _excludedFolders.update { current ->
            (current - path).also { snapshot = it }
        }
        persistExcludedLocked(snapshot)
    }

    fun setSortOption(option: SortOption) {
        _sortOption.update { option }
        reload()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.update { mode }
    }

    private fun sortFiles(files: List<MediaFile>): List<MediaFile> {
        return when (_sortOption.value) {
            SortOption.NAME -> files.sortedBy { it.name.lowercase() }
            SortOption.DATE_CREATED -> files.sortedByDescending { it.dateModified }
            SortOption.SIZE -> files.sortedByDescending { it.size }
            SortOption.TYPE -> files.sortedBy { it.mimeType }
        }
    }

    // Deprecated Environment.getExternalStorageDirectory() replaced: primary
    // shared root comes from getExternalFilesDirs() so it works on all
    // manufacturers (no hard-coded /storage/emulated/0 comparisons in callers).
    val rootPath: String
        get() = storageRoots().firstOrNull()
            ?: getApplication<Application>().getExternalFilesDir(null)
                ?.absolutePath?.substringBefore("/Android/") ?: "/sdcard"
    private var currentPath: String? = null

    init {
        loadFiles(resolveRoot())
    }

    private fun resolveRoot(): String = rootPath

    fun reload() {
        // Cancel in-flight loads so only the latest request can publish.
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            launch { loadAllMediaInternal() }
            launch { loadDocumentsInternal() }
            loadFilesInternal(currentPath ?: resolveRoot())
        }
    }

    fun loadFiles(path: String = currentPath ?: resolveRoot()) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch { loadFilesInternal(path) }
    }

    private suspend fun loadFilesInternal(path: String) {
        currentPath = path
        _fileTreeState.update { ViewState.Loading }
        try {
            val roots = withContext(Dispatchers.IO) { storageRoots() }
            val files = withContext(Dispatchers.IO) {
                val result = mutableListOf<MediaFile>()
                val directory = File(path)
                val indexedFiles = getAllMediaFiles().associateBy { it.path }
                // File.listFiles() is deliberately the primary source here.  MediaStore only
                // indexes selected file types, which made folders and ordinary documents
                // disappear compared with Files by Google.
                val visibleItems = directory.listFiles()
                    ?.map { file ->
                        if (file.isDirectory) {
                            MediaFile(-1, file.name, file.absolutePath, 0, "folder", file.lastModified(), true)
                        } else {
                            indexedFiles[file.absolutePath] ?: file.toMediaFile()
                        }
                    }
                    ?.toMutableList()
                    ?: mutableListOf()

                // On devices where a provider exposes an item before the filesystem does,
                // retain direct MediaStore children as a fallback.
                val targetDir = if (path.endsWith("/")) path else "$path/"
                indexedFiles.values.filter { item ->
                    item.path.startsWith(targetDir) && !item.path.removePrefix(targetDir).contains("/")
                }.forEach { item ->
                    if (visibleItems.none { it.path == item.path }) visibleItems.add(item)
                }

                if (path !in roots) {
                    val parent = File(path).parent ?: roots.firstOrNull() ?: path
                    result.add(MediaFile(-1, "..", parent, 0, "folder", 0, true))
                }
                val dirs = visibleItems.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
                result + dirs + sortFiles(visibleItems.filter { !it.isDirectory })
            }
            _fileTreeState.update { ViewState.Success(files, path) }
        } catch (e: Exception) {
            _fileTreeState.update { ViewState.Error(e.message ?: "Unknown error") }
        }
    }

    fun navigateUp() {
        val current = currentPath ?: return
        val currentFile = File(current)
        viewModelScope.launch(Dispatchers.IO) {
            val roots = storageRoots()
            if (currentFile.absolutePath !in roots) {
                currentFile.parent?.let { loadFiles(it) }
            }
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "tif", "tiff", "svg" -> "image/*"
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv" -> "video/*"
            "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus" -> "audio/*"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "rtf" -> "application/rtf"
            "txt", "md" -> "text/plain"
            "csv" -> "text/csv"
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

    private fun File.toMediaFile(): MediaFile = MediaFile(
        id = -1,
        name = name,
        path = absolutePath,
        size = length(),
        mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: getMimeType(extension),
        dateModified = lastModified(),
        contentUri = null
    )

    /** Call off the main thread: hits the filesystem via [File.exists]. */
    private fun storageRoots(): List<String> {
        val context = getApplication<Application>()
        return buildList {
            context.getExternalFilesDirs(null).mapNotNull { it?.absolutePath?.substringBefore("/Android/data/") }
                .filter { it.isNotBlank() }
                .forEach(::add)
            // Legacy fallback for devices where app-scoped dirs are unavailable.
            @Suppress("DEPRECATION")
            add(Environment.getExternalStorageDirectory().absolutePath)
        }.distinct().filter { File(it).exists() }
    }

    /** Canonical root check shared with UI (replaces hard-coded /storage/emulated/0). */
    fun isStorageRoot(path: String): Boolean {
        val abs = File(path).absolutePath
        val context = getApplication<Application>()
        val candidates = context.getExternalFilesDirs(null)
            .mapNotNull { it?.absolutePath?.substringBefore("/Android/data/") } +
            listOf("/storage/emulated/0", "/sdcard")
        return candidates.any { abs == it }
    }

    fun findDuplicates() {
        if (_isScanningDuplicates.value) return

        viewModelScope.launch {
            _isScanningDuplicates.update { true }
            val duplicates = withContext(Dispatchers.IO) {
                val allFiles = sortFiles(getAllMediaFiles())
                
                // Group by size first for performance
                val sizeGroups = allFiles.groupBy { it.size }.filter { it.value.size > 1 }
                
                // Then group by partial hash
                val duplicateGroups = mutableListOf<List<MediaFile>>()
                
                for ((_, files) in sizeGroups) {
                    // Null hash = unreadable; never group unreadables together.
                    val partialHashGroups = files.groupBy { calculatePartialHash(it) }
                        .filter { it.key != null && it.value.size > 1 }

                    // For groups that match size and partial hash, verify with full hash
                    for ((_, partialFiles) in partialHashGroups) {
                        val fullHashGroups = partialFiles.groupBy { calculateSampledHash(it) }
                            .filter { it.key != null && it.value.size > 1 }
                        
                        fullHashGroups.values.forEach {
                            duplicateGroups.add(it)
                        }
                    }
                }
                duplicateGroups
            }
            _duplicateFiles.update { duplicates }
            _isScanningDuplicates.update { false }
        }
    }

    fun loadAllMedia() {
        viewModelScope.launch { loadAllMediaInternal() }
    }

    private suspend fun loadAllMediaInternal() {
        _mediaState.update { ViewState.Loading }
        try {
            val files = withContext(Dispatchers.IO) {
                sortFiles(getAllMediaFiles())
            }
            _mediaState.update { ViewState.Success(files, "All Media") }
        } catch (e: Exception) {
            _mediaState.update { ViewState.Error(e.message ?: "Unknown error") }
        }
    }

    /** Lists local PDF and office documents, including app-created scans and non-media files. */
    fun loadDocuments() {
        viewModelScope.launch { loadDocumentsInternal() }
    }

    private suspend fun loadDocumentsInternal() {
        _documentsState.update { ViewState.Loading }
        try {
            val files = withContext(Dispatchers.IO) { sortFiles(getAllDocumentFiles()) }
            _documentsState.update { ViewState.Success(files, "Documents") }
        } catch (e: Exception) {
            _documentsState.update { ViewState.Error(e.message ?: "Unknown error") }
        }
    }

    private val _pendingDeleteIntent = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingDeleteIntent: StateFlow<android.content.IntentSender?> = _pendingDeleteIntent.asStateFlow()

    private var pendingDeletePath: String? = null

    fun deleteFile(path: String, contentUri: Uri? = null) {
        viewModelScope.launch {
            var deleted = false
            withContext(Dispatchers.IO) {
                try {
                    if (contentUri != null && contentUri != Uri.EMPTY) {
                        val deletedRows = getApplication<Application>().contentResolver.delete(contentUri, null, null)
                        if (deletedRows > 0) deleted = true
                    }
                    if (!deleted) {
                        val file = File(path)
                        if (file.exists()) {
                            deleted = file.delete()
                        }
                    }
                } catch (e: SecurityException) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        if (contentUri != null) {
                            val intentSender = MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, listOf(contentUri)).intentSender
                            _pendingDeleteIntent.update { intentSender }
                            pendingDeletePath = path
                        }
                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val recoverableSecurityException = e as? android.app.RecoverableSecurityException
                        if (recoverableSecurityException != null) {
                            _pendingDeleteIntent.update { recoverableSecurityException.userAction.actionIntent.intentSender }
                            pendingDeletePath = path
                        }
                    }
                    // No printStackTrace: surface via deleteError below.
                    _deleteError.update { e.message }
                } catch (e: Exception) {
                    _deleteError.update { e.message }
                }
            }
            if (deleted) {
                onFileDeleted(path)
            }
        }
    }

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun clearDeleteError() {
        _deleteError.update { null }
    }

    fun onPendingDeleteResult(success: Boolean) {
        _pendingDeleteIntent.update { null }
        val path = pendingDeletePath
        if (success && path != null) {
            onFileDeleted(path)
        }
        pendingDeletePath = null
    }

    private fun onFileDeleted(path: String) {
        if (!_isScanningDuplicates.value) {
            _duplicateFiles.update { groups ->
                groups.mapNotNull { group ->
                    val newGroup = group.filter { it.path != path }
                    if (newGroup.size > 1) newGroup else null
                }
            }
        }
        val parentPath = File(path).parent
        val current = currentPath
        if (current == parentPath && _fileTreeState.value is ViewState.Success && current != null) {
            loadFiles(current)
        }
        loadAllMedia()
    }

    private fun getAllMediaFiles(): List<MediaFile> {
        val mediaList = linkedMapOf<String, MediaFile>()
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

        val context: Context = getApplication()
        val volumes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
        } else setOf("external")
        val roots = storageRoots()

        volumes.forEach { volume ->
            context.contentResolver.query(MediaStore.Files.getContentUri(volume), projection, null, null, null)?.use {
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
                val id = it.getLong(idCol)
                val displayName = it.getString(nameCol) ?: "unknown"
                
                val path = if (useRelativePath) {
                    val relPath = it.getString(pathCol) ?: ""
                    val root = roots.firstOrNull { candidate ->
                        candidate.substringAfterLast('/').equals(volume, ignoreCase = true)
                    } ?: rootPath
                    val fullPath = if (relPath.isNotEmpty()) "$root/$relPath$displayName" else "$root/$displayName"
                    fullPath.replace("//", "/")
                } else {
                    it.getString(pathCol) ?: ""
                }
                
                val mimeType = it.getString(mimeCol) ?: ""
                
                // We only care about media/documents, ignoring random binary/system files if possible
                if (mimeType.isNotBlank() || displayName.endsWith(".apk", ignoreCase = true)) {
                    val finalMimeType = if (mimeType.isNotBlank()) mimeType else if (displayName.endsWith(".apk", true)) "application/vnd.android.package-archive" else ""
                    val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri(volume), id)
                    mediaList[path] =
                        MediaFile(
                            id = id,
                            name = displayName,
                            path = path,
                            size = it.getLong(sizeCol),
                            mimeType = finalMimeType,
                            dateModified = it.getLong(dateCol) * 1000,
                            isDirectory = false,
                            contentUri = contentUri
                        )
                }
            }
        }
        }
        return mediaList.values.toList()
    }

    private fun getAllDocumentFiles(): List<MediaFile> {
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

    private fun MediaFile.isDocument(): Boolean = isDocumentName(name) ||
        mimeType == "application/pdf" || mimeType.startsWith("application/vnd.openxmlformats-officedocument") ||
        mimeType.startsWith("application/vnd.oasis.opendocument") || mimeType == "application/msword" ||
        mimeType == "application/rtf" || mimeType == "text/plain"

    private fun File.isDocument(): Boolean = isFile && isDocumentName(name)

    private fun isDocumentName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf(
        "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "csv", "json", "html", "htm",
        "xls", "xlsx", "ods", "ppt", "pptx", "odp", "epub"
    )

    /** Head sample (first 256KB). Returns null when unreadable so callers never group failures. */
    private fun calculatePartialHash(mediaFile: MediaFile): String? {
        return sampleStream(mediaFile, headBytes = 256 * 1024L, tailBytes = 0L)
    }

    /**
     * Head + tail sampled hash (first 256KB + last 256KB + size mixed in).
     * Replaces the old misnamed "full hash" which only read the first 1MB.
     */
    private fun calculateSampledHash(mediaFile: MediaFile): String? {
        return sampleStream(mediaFile, headBytes = 256 * 1024L, tailBytes = 256 * 1024L)
    }

    private fun sampleStream(mediaFile: MediaFile, headBytes: Long, tailBytes: Long): String? {
        return try {
            val resolver = getApplication<Application>().contentResolver
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            if (mediaFile.contentUri != null && mediaFile.contentUri != Uri.EMPTY) {
                resolver.openInputStream(mediaFile.contentUri)?.use { input ->
                    var remaining = headBytes
                    while (remaining > 0) {
                        val chunk = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (chunk == -1) break
                        md.update(buffer, 0, chunk)
                        remaining -= chunk
                    }
                } ?: return null
                if (tailBytes > 0) {
                    // Tail sampling via FileDescriptor seek when possible; skip silently otherwise.
                    try {
                        resolver.openFileDescriptor(mediaFile.contentUri, "r")?.use { pfd ->
                            val fileSize = pfd.statSize.takeIf { it > 0 } ?: mediaFile.size
                            val tailStart = maxOf(0L, fileSize - tailBytes)
                            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { raw ->
                                // Re-open for tail: descriptor streams support skip reliably for local files.
                                var toSkip = tailStart - headBytes.coerceAtMost(fileSize)
                                while (toSkip > 0) {
                                    val skipped = raw.skip(toSkip)
                                    if (skipped <= 0) break
                                    toSkip -= skipped
                                }
                                var remainingTail = minOf(tailBytes, fileSize - tailStart)
                                while (remainingTail > 0) {
                                    val chunk = raw.read(buffer, 0, minOf(buffer.size.toLong(), remainingTail).toInt())
                                    if (chunk == -1) break
                                    md.update(buffer, 0, chunk)
                                    remainingTail -= chunk
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Tail unavailable (remote provider) — head-only hash still valid.
                    }
                }
            } else {
                val file = File(mediaFile.path)
                if (!file.isFile || !file.canRead()) return null
                file.inputStream().use { input ->
                    var remaining = headBytes
                    while (remaining > 0) {
                        val chunk = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (chunk == -1) break
                        md.update(buffer, 0, chunk)
                        remaining -= chunk
                    }
                }
                if (tailBytes > 0) {
                    java.io.RandomAccessFile(file, "r").use { raf ->
                        val tailStart = maxOf(0L, raf.length() - tailBytes)
                        raf.seek(tailStart)
                        var remainingTail = raf.length() - tailStart
                        while (remainingTail > 0) {
                            val chunk = raf.read(buffer, 0, minOf(buffer.size.toLong(), remainingTail).toInt())
                            if (chunk == -1) break
                            md.update(buffer, 0, chunk)
                            remainingTail -= chunk
                        }
                    }
                }
            }
            // Mix size in so same-prefix files of different lengths never collide.
            md.update(mediaFile.size.toString().toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
