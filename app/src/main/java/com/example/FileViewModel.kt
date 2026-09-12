package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.files.DuplicateFinder
import com.example.files.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MVI ViewModel: owns UI state and user intents only.
 *
 * All filesystem / MediaStore queries live in [MediaRepository], duplicate
 * hashing in [DuplicateFinder]. Excluded folders persist via DataStore
 * ([SettingsRepository]) with a one-time legacy SharedPreferences migration.
 */
class FileViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaRepository = MediaRepository(application)
    private val duplicateFinder = DuplicateFinder(application.contentResolver)
    private val settingsRepository = SettingsRepository(application)

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

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    private val _renameError = MutableStateFlow<String?>(null)
    val renameError: StateFlow<String?> = _renameError.asStateFlow()

    private val _pendingDeleteIntent = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingDeleteIntent: StateFlow<android.content.IntentSender?> = _pendingDeleteIntent.asStateFlow()

    private var pendingDeletePath: String? = null

    // Single reload job so parallel Loading/Success writes can no longer race
    // (late error no longer clobbers fresh success).
    private var reloadJob: Job? = null

    private var currentPath: String? = null

    /** Canonical roots / primary root, shared with DeX and normal UI alike. */
    fun storageRoots(): List<String> = mediaRepository.storageRoots()

    val rootPath: String
        get() = mediaRepository.rootPath()

    /** Canonical root check shared with UI (replaces hard-coded /storage/emulated/0). */
    fun isStorageRoot(path: String): Boolean = mediaRepository.isStorageRoot(path)

    init {
        // Excluded folders now live in DataStore (single transactional writer).
        // One-time migration unions the legacy SharedPreferences set, then drops it.
        viewModelScope.launch {
            val legacy = withContext(Dispatchers.IO) {
                val prefs = application.getSharedPreferences("media_master_prefs", Context.MODE_PRIVATE)
                prefs.getStringSet("excluded_folders", null)?.toSet()
            }
            if (legacy != null) {
                settingsRepository.mergeExcludedFolders(legacy)
                withContext(Dispatchers.IO) {
                    application.getSharedPreferences("media_master_prefs", Context.MODE_PRIVATE)
                        .edit().remove("excluded_folders").apply()
                }
            }
            settingsRepository.excludedFoldersFlow.collect { stored ->
                _excludedFolders.update { stored }
            }
        }
    }

    init {
        loadFiles(resolveRoot())
    }

    private fun resolveRoot(): String = rootPath

    fun toggleShowExcludedInManage() {
        _showExcludedInManage.update { !it }
    }

    fun setCategoryViewMode(mode: ViewMode) {
        _categoryViewMode.update { mode }
    }

    fun addExcludedFolder(path: String) {
        viewModelScope.launch { settingsRepository.updateExcludedFolders(add = path) }
    }

    fun removeExcludedFolder(path: String) {
        viewModelScope.launch { settingsRepository.updateExcludedFolders(remove = path) }
    }

    fun setSortOption(option: SortOption) {
        _sortOption.update { option }
        reload()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.update { mode }
    }

    private fun sortFiles(files: List<MediaFile>): List<MediaFile> = sortMediaFiles(files, _sortOption.value)

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
                val indexedFiles = mediaRepository.getAllMediaFiles().associateBy { it.path }
                // File.listFiles() is deliberately the primary source here.  MediaStore only
                // indexes selected file types, which made folders and ordinary documents
                // disappear compared with Files by Google.
                val visibleItems = directory.listFiles()
                    ?.map { file ->
                        if (file.isDirectory) {
                            MediaFile(-1, file.name, file.absolutePath, 0, "folder", file.lastModified(), true)
                        } else {
                            indexedFiles[file.absolutePath] ?: with(mediaRepository) { file.toMediaFile() }
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

    fun findDuplicates() {
        if (_isScanningDuplicates.value) return

        viewModelScope.launch {
            _isScanningDuplicates.update { true }
            val duplicates = withContext(Dispatchers.IO) {
                val allFiles = sortFiles(mediaRepository.getAllMediaFiles())

                // Group by size first for performance
                val sizeGroups = allFiles.groupBy { it.size }.filter { it.value.size > 1 }

                // Then group by partial hash
                val duplicateGroups = mutableListOf<List<MediaFile>>()

                for ((_, files) in sizeGroups) {
                    // Null hash = unreadable; never group unreadables together.
                    val partialHashGroups = files.groupBy { duplicateFinder.calculatePartialHash(it) }
                        .filter { it.key != null && it.value.size > 1 }

                    // For groups that match size and partial hash, verify with full hash
                    for ((_, partialFiles) in partialHashGroups) {
                        val fullHashGroups = partialFiles.groupBy { duplicateFinder.calculateSampledHash(it) }
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
                sortFiles(mediaRepository.getAllMediaFiles())
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
            val files = withContext(Dispatchers.IO) { sortFiles(mediaRepository.getAllDocumentFiles()) }
            _documentsState.update { ViewState.Success(files, "Documents") }
        } catch (e: Exception) {
            _documentsState.update { ViewState.Error(e.message ?: "Unknown error") }
        }
    }

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

    fun clearDeleteError() {
        _deleteError.update { null }
    }

    /**
     * Renames a file in place (same folder). Mirrors deleteFile's dual-path
     * handling: MediaStore-indexed content (API 29+) is renamed via
     * ContentResolver first, falling back to a raw File rename for legacy
     * paths not backed by a content:// URI. Unlike deleteFile this doesn't
     * offer a RecoverableSecurityException retry flow — that only matters for
     * files this app doesn't own (e.g. saved by another app), which is rare
     * for a personal file manager; surfacing the error is enough for now.
     */
    fun renameFile(path: String, contentUri: Uri?, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            var renamed = false
            withContext(Dispatchers.IO) {
                try {
                    if (contentUri != null && contentUri != Uri.EMPTY) {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                        }
                        val updatedRows = getApplication<Application>().contentResolver.update(contentUri, values, null, null)
                        if (updatedRows > 0) renamed = true
                    }
                    if (!renamed) {
                        val file = File(path)
                        val target = File(file.parentFile, newName)
                        if (file.exists() && !target.exists() && file.renameTo(target)) {
                            renamed = true
                            android.media.MediaScannerConnection.scanFile(
                                getApplication(), arrayOf(path, target.path), null, null,
                            )
                        }
                    }
                } catch (e: Exception) {
                    _renameError.update { e.message }
                }
            }
            if (renamed) {
                onFileRenamed(path)
            }
        }
    }

    fun clearRenameError() {
        _renameError.update { null }
    }

    /**
     * Copies a file into [destDir]. Reads through ContentResolver when a
     * contentUri is available (works uniformly for MediaStore-indexed and
     * legacy files, unlike renameFile/deleteFile's MediaStore-update path,
     * which only applies to same-location renames), otherwise reads the raw
     * File. Always writes the destination via plain File I/O since the
     * destination folder is a location the user picked in-app, not a
     * MediaStore-restricted one.
     */
    fun copyFile(path: String, contentUri: Uri?, destDir: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val destPath = withContext(Dispatchers.IO) {
                runCatching {
                    val sourceName = File(path).name
                    val dest = File(destDir, sourceName)
                    dest.parentFile?.mkdirs()
                    val input = if (contentUri != null && contentUri != Uri.EMPTY) {
                        getApplication<Application>().contentResolver.openInputStream(contentUri)
                    } else {
                        File(path).inputStream()
                    }
                    input?.use { inStream -> dest.outputStream().use { out -> inStream.copyTo(out) } }
                    android.media.MediaScannerConnection.scanFile(getApplication(), arrayOf(dest.path), null, null)
                    dest.path
                }.onFailure { error -> _renameError.update { error.message } }.getOrNull()
            }
            if (destPath != null) loadAllMedia()
            onDone(destPath)
        }
    }

    /** Copies then deletes the source, reusing deleteFile's scoped-storage handling for the removal. */
    fun moveFile(path: String, contentUri: Uri?, destDir: String) {
        copyFile(path, contentUri, destDir) { destPath ->
            if (destPath != null) deleteFile(path, contentUri)
        }
    }

    private fun onFileRenamed(oldPath: String) {
        val parentPath = File(oldPath).parent
        val current = currentPath
        if (current == parentPath && _fileTreeState.value is ViewState.Success && current != null) {
            loadFiles(current)
        }
        loadAllMedia()
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
}
