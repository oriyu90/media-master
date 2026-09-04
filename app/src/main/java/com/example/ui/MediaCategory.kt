package com.example.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.MediaFile
import com.example.R

/**
 * Storage categories shown on the Manage dashboard and resolved by CategoryScreen.
 *
 * [key] is the stable, non-localised identifier carried in the navigation route
 * (`category/{key}`). Previously the same English strings ("Downloads", "Images",
 * …) were duplicated as literals in ManageDashboardScreen and matched in two
 * places in CategoryScreen — this enum is the single source of truth.
 */
enum class MediaCategory(
    val key: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    DOWNLOADS("Downloads", R.string.downloads, Icons.Default.Download),
    IMAGES("Images", R.string.images, Icons.Default.Image),
    VIDEOS("Videos", R.string.videos, Icons.Default.VideoLibrary),
    AUDIO("Audio", R.string.audio, Icons.Default.Audiotrack),
    DOCUMENTS("Documents", R.string.documents, Icons.Default.Description),
    APPS("Apps", R.string.apps, Icons.Default.Apps);

    /** Whether [file] belongs to this category. Single definition, previously duplicated. */
    fun matches(file: MediaFile): Boolean = when (this) {
        DOWNLOADS -> file.path.contains("Download")
        IMAGES -> file.mimeType.startsWith("image/")
        VIDEOS -> file.mimeType.startsWith("video/")
        AUDIO -> file.mimeType.startsWith("audio/")
        DOCUMENTS -> file.mimeType.startsWith("application/") || file.mimeType.startsWith("text/")
        APPS -> file.mimeType == "application/vnd.android.package-archive"
    }

    companion object {
        fun fromKey(key: String?): MediaCategory? = entries.firstOrNull { it.key == key }
    }
}
