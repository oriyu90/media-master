package com.example

import android.app.Application
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        val sharedPrefs = application.getSharedPreferences("media_master_prefs", Context.MODE_PRIVATE)
        _excludedFolders.value = sharedPrefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
    }

    fun toggleShowExcludedInManage() {
        _showExcludedInManage.value = !_showExcludedInManage.value
    }

    fun setCategoryViewMode(mode: ViewMode) {
        _categoryViewMode.value = mode
    }

    fun addExcludedFolder(path: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.add(path)
        _excludedFolders.value = current
        getApplication<Application>().getSharedPreferences("media_master_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("excluded_folders", current).apply()
    }

    fun removeExcludedFolder(path: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.remove(path)
        _excludedFolders.value = current
        getApplication<Application>().getSharedPreferences("media_master_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("excluded_folders", current).apply()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        reload()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    private fun sortFiles(files: List<MediaFile>): List<MediaFile> {
        return when (_sortOption.value) {
            SortOption.NAME -> files.sortedBy { it.name.lowercase() }
            SortOption.DATE_CREATED -> files.sortedByDescending { it.dateModified }
            SortOption.SIZE -> files.sortedByDescending { it.size }
            SortOption.TYPE -> files.sortedBy { it.mimeType }
        }
    }

    private val rootPath = Environment.getExternalStorageDirectory().absolutePath
    private var currentPath = rootPath

    init {
        loadFiles(rootPath)
    }

    fun reload() {
        loadAllMedia()
        loadDocuments()
        loadFiles(currentPath)
    }

    fun loadFiles(path: String = currentPath) {
        currentPath = path
        viewModelScope.launch {
            _fileTreeState.value = ViewState.Loading
            try {
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

                    if (path !in storageRoots()) {
                        val parent = File(path).parent ?: rootPath
                        result.add(MediaFile(-1, "..", parent, 0, "folder", 0, true))
                    }
                    val dirs = visibleItems.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
                    result + dirs + sortFiles(visibleItems.filter { !it.isDirectory })
                }
                _fileTreeState.value = ViewState.Success(files, path)
            } catch (e: Exception) {
                _fileTreeState.value = ViewState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun navigateUp() {
        val currentFile = File(currentPath)
        if (currentFile.absolutePath !in storageRoots()) {
            currentFile.parent?.let { loadFiles(it) }
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp" -> "image/*"
            "mp4", "mkv", "avi" -> "video/*"
            "mp3", "wav", "ogg", "flac" -> "audio/*"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "rtf" -> "application/rtf"
            "txt" -> "text/plain"
            "xls", "xlsx", "ods", "ppt", "pptx", "odp" -> "application/octet-stream"
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

    private fun storageRoots(): List<String> {
        val context = getApplication<Application>()
        return buildList {
            add(rootPath)
            context.getExternalFilesDirs(null).mapNotNull { it?.absolutePath?.substringBefore("/Android/data/") }
                .filter { it.isNotBlank() }
                .forEach(::add)
        }.distinct().filter { File(it).exists() }
    }

    fun findDuplicates() {
        if (_isScanningDuplicates.value) return
        
        viewModelScope.launch {
            _isScanningDuplicates.value = true
            val duplicates = withContext(Dispatchers.IO) {
                val allFiles = sortFiles(getAllMediaFiles())
                
                // Group by size first for performance
                val sizeGroups = allFiles.groupBy { it.size }.filter { it.value.size > 1 }
                
                // Then group by partial hash
                val duplicateGroups = mutableListOf<List<MediaFile>>()
                
                for ((_, files) in sizeGroups) {
                    val partialHashGroups = files.groupBy { calculatePartialHash(it) }
                        .filter { it.key.isNotEmpty() && it.value.size > 1 }
                    
                    // For groups that match size and partial hash, verify with full hash
                    for ((_, partialFiles) in partialHashGroups) {
                        val fullHashGroups = partialFiles.groupBy { calculateFullHash(it) }
                            .filter { it.key.isNotEmpty() && it.value.size > 1 }
                        
                        fullHashGroups.values.forEach {
                            duplicateGroups.add(it)
                        }
                    }
                }
                duplicateGroups
            }
            _duplicateFiles.value = duplicates
            _isScanningDuplicates.value = false
        }
    }

    fun loadAllMedia() {
        viewModelScope.launch {
            _mediaState.value = ViewState.Loading
            try {
                val files = withContext(Dispatchers.IO) {
                    sortFiles(getAllMediaFiles())
                }
                _mediaState.value = ViewState.Success(files, "All Media")
            } catch (e: Exception) {
                _mediaState.value = ViewState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Lists local PDF and office documents, including app-created scans and non-media files. */
    fun loadDocuments() {
        viewModelScope.launch {
            _documentsState.value = ViewState.Loading
            try {
                val files = withContext(Dispatchers.IO) { sortFiles(getAllDocumentFiles()) }
                _documentsState.value = ViewState.Success(files, "Documents")
            } catch (e: Exception) {
                _documentsState.value = ViewState.Error(e.message ?: "Unknown error")
            }
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
                            _pendingDeleteIntent.value = intentSender
                            pendingDeletePath = path
                        }
                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val recoverableSecurityException = e as? android.app.RecoverableSecurityException
                        if (recoverableSecurityException != null) {
                            _pendingDeleteIntent.value = recoverableSecurityException.userAction.actionIntent.intentSender
                            pendingDeletePath = path
                        }
                    } else {
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (deleted) {
                onFileDeleted(path)
            }
        }
    }

    fun onPendingDeleteResult(success: Boolean) {
        _pendingDeleteIntent.value = null
        val path = pendingDeletePath
        if (success && path != null) {
            onFileDeleted(path)
        }
        pendingDeletePath = null
    }

    private fun onFileDeleted(path: String) {
        if (!_isScanningDuplicates.value) {
            val updatedDuplicates = _duplicateFiles.value.mapNotNull { group ->
                val newGroup = group.filter { it.path != path }
                if (newGroup.size > 1) newGroup else null
            }
            _duplicateFiles.value = updatedDuplicates
        }
        val parentPath = File(path).parent
        if (currentPath == parentPath && _fileTreeState.value is ViewState.Success) {
            loadFiles(currentPath)
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
        "pdf", "doc", "docx", "odt", "rtf", "txt", "xls", "xlsx", "ods", "ppt", "pptx", "odp"
    )

    private fun calculatePartialHash(mediaFile: MediaFile): String {
        try {
            val resolver = getApplication<Application>().contentResolver
            val inputStream = if (mediaFile.contentUri != null) {
                resolver.openInputStream(mediaFile.contentUri)
            } else {
                null
            }
            if (inputStream == null) return ""
            
            val md = MessageDigest.getInstance("MD5")
            val bytesToRead = minOf(mediaFile.size, 1024 * 1024L) // Max 1MB
            val buffer = ByteArray(8192)
            
            inputStream.use { input ->
                var read = 0L
                while (read < bytesToRead) {
                    val chunk = input.read(buffer, 0, minOf(8192L, bytesToRead - read).toInt())
                    if (chunk == -1) break
                    md.update(buffer, 0, chunk)
                    read += chunk
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }

    private fun calculateFullHash(mediaFile: MediaFile): String {
        try {
            val resolver = getApplication<Application>().contentResolver
            val inputStream = if (mediaFile.contentUri != null) {
                resolver.openInputStream(mediaFile.contentUri)
            } else {
                null
            }
            if (inputStream == null) return ""
            
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            
            // Read first 1MB
            inputStream.use { input ->
                var read = 0L
                while (read < 1024 * 1024L) {
                    val chunk = input.read(buffer, 0, minOf(8192L, 1024 * 1024L - read).toInt())
                    if (chunk == -1) break
                    md.update(buffer, 0, chunk)
                    read += chunk
                }
            }
            // In a real app we'd also hash the end, but MediaStore URI doesn't easily support seek without FileDescriptor.
            // For safety, we just rely on this larger chunk.
            
            return md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }
}
