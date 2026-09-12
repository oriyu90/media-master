package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for how a [MediaFile] is represented as a small
 * preview: real image/video frame thumbnails via Coil, embedded album art
 * for audio, and a type-appropriate icon fallback everywhere else. Used by
 * every file-list/grid surface (Files, Audio, Manage) so they stay in sync.
 */
@Composable
fun MediaThumbnail(
    file: MediaFile,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    iconSize: Dp = 40.dp,
) {
    when {
        file.isDirectory -> Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(iconSize))
        }
        file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/") -> {
            AsyncImage(
                model = file.contentUri ?: File(file.path),
                contentDescription = file.name,
                modifier = modifier,
                contentScale = contentScale,
            )
        }
        file.mimeType.startsWith("audio/") -> AudioThumbnail(file, modifier, contentScale, iconSize)
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudioThumbnail(
    file: MediaFile,
    modifier: Modifier,
    contentScale: ContentScale,
    iconSize: Dp,
) {
    val context = LocalContext.current
    val artwork by produceState<Bitmap?>(initialValue = null, key1 = file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    if (file.contentUri != null) {
                        retriever.setDataSource(context, file.contentUri)
                    } else {
                        retriever.setDataSource(file.path)
                    }
                    retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }

    val bitmap = artwork
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = file.name,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
