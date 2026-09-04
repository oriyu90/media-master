package com.example.network

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owns the saved network locations and the current browse session.
 * State is only ever mutated via [MutableStateFlow.update]-style replacement.
 */
class NetworkViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NetworkLocationRepository(app)
    private val credentials = NetworkCredentialStore(app)
    private val client = NetworkStorageClient()

    val locations: StateFlow<List<NetworkLocation>> =
        repo.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _browse = MutableStateFlow<BrowseUiState>(BrowseUiState.Idle)
    val browse: StateFlow<BrowseUiState> = _browse.asStateFlow()

    fun newLocationId(): String = UUID.randomUUID().toString()

    /** [password] blank when editing means "keep the stored password". */
    fun saveLocation(location: NetworkLocation, password: String) {
        viewModelScope.launch {
            repo.upsert(location)
            if (password.isNotEmpty()) credentials.setPassword(location.id, password)
        }
    }

    fun deleteLocation(id: String) {
        viewModelScope.launch {
            repo.delete(id)
            credentials.removePassword(id)
            if ((_browse.value as? BrowseUiState.Ready)?.location?.id == id) _browse.value = BrowseUiState.Idle
        }
    }

    fun closeBrowser() { _browse.value = BrowseUiState.Idle }

    fun open(location: NetworkLocation) = navigate(location, "")

    fun navigate(location: NetworkLocation, path: String) {
        _browse.value = BrowseUiState.Loading
        viewModelScope.launch {
            val pw = withContext(Dispatchers.IO) { credentials.getPassword(location.id) }
            client.list(location, pw, path)
                .onSuccess { entries -> _browse.value = BrowseUiState.Ready(location, path, entries) }
                .onFailure { e -> _browse.value = BrowseUiState.Error(e.message ?: "Connection failed") }
        }
    }

    fun up() {
        val state = _browse.value as? BrowseUiState.Ready ?: return
        if (state.path.isEmpty()) { _browse.value = BrowseUiState.Idle; return }
        navigate(state.location, state.path.substringBeforeLast('/', ""))
    }

    fun openEntry(entry: NetworkEntry) {
        val state = _browse.value as? BrowseUiState.Ready ?: return
        if (entry.isDirectory) {
            navigate(state.location, entry.relativePath)
        } else {
            downloadAndView(state.location, entry)
        }
    }

    private fun downloadAndView(location: NetworkLocation, entry: NetworkEntry) {
        viewModelScope.launch {
            val prev = _browse.value
            _browse.value = BrowseUiState.Loading
            val pw = withContext(Dispatchers.IO) { credentials.getPassword(location.id) }
            val ctx = getApplication<Application>()
            val cacheDir = File(ctx.cacheDir, "network").apply { mkdirs() }
            val dest = File(cacheDir, entry.name)
            client.download(location, pw, entry.relativePath, dest)
                .onSuccess {
                    _browse.value = prev
                    runCatching {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dest)
                        val mime = ctx.contentResolver.getType(uri)
                            ?: android.webkit.MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(dest.extension.lowercase()) ?: "*/*"
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
                .onFailure { e -> _browse.value = BrowseUiState.Error(e.message ?: "Download failed") }
        }
    }
}
