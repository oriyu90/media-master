package com.example

import android.net.Uri

/** Single media/document entry shared by the browser, library and cleaners. */
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

/** Shared sort logic reused by FileViewModel and any screen (e.g. Library) that needs the same ordering. */
fun sortMediaFiles(files: List<MediaFile>, sortOption: SortOption): List<MediaFile> {
    return when (sortOption) {
        SortOption.NAME -> files.sortedBy { it.name.lowercase() }
        SortOption.DATE_CREATED -> files.sortedByDescending { it.dateModified }
        SortOption.SIZE -> files.sortedByDescending { it.size }
        SortOption.TYPE -> files.sortedBy { it.mimeType }
    }
}

sealed class ViewState {
    object Loading : ViewState()
    data class Success(val files: List<MediaFile>, val currentPath: String) : ViewState()
    data class Error(val message: String) : ViewState()
}
