@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example

import androidx.compose.ui.layout.onGloballyPositioned
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.theme.ViewerChromeContainer
import com.example.ui.theme.ViewerOnSurface
import com.example.ui.theme.ViewerScrim
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(path: String?, viewModel: FileViewModel?, navController: NavHostController) {
    val context = LocalContext.current
    if (path == null || viewModel == null) {
        EmptyState(
            icon = Icons.Default.BrokenImage,
            title = stringResource(R.string.invalid_file_path),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val viewState = viewModel.mediaState.collectAsStateWithLifecycle().value

    var isFullScreen by remember { mutableStateOf(false) }
    
    // OCR State
    var isOcrMode by remember { mutableStateOf(false) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf<Text?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    val coroutineScope = rememberCoroutineScope()

    when (val s = viewState) {
        is ViewState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ViewerOnSurface)
            }
            return
        }
        is ViewState.Error -> {
            ErrorState(
                message = s.message,
                modifier = Modifier.fillMaxSize(),
                retryLabel = stringResource(R.string.retry),
                onRetry = { viewModel.loadAllMedia() },
            )
            return
        }
        is ViewState.Success -> {
        val targetFile = s.files.find { it.path == path }
        if (targetFile == null) {
            EmptyState(
                icon = Icons.Default.BrokenImage,
                title = stringResource(R.string.invalid_file_path),
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        val isTargetAudio = targetFile.mimeType.startsWith("audio/")
        val isTargetImageOrVideo = targetFile.mimeType.startsWith("image/") || targetFile.mimeType.startsWith("video/")
        
        val mediaList = remember(s.files, path) {
            if (isTargetImageOrVideo) {
                s.files.filter { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }
            } else if (isTargetAudio) {
                s.files.filter { it.mimeType.startsWith("audio/") }
            } else {
                listOf(targetFile)
            }
        }
        
        val initialIndex = mediaList.indexOfFirst { it.path == path }.coerceAtLeast(0)
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = initialIndex,
            pageCount = { mediaList.size }
        )

        val safePage = if (pagerState.currentPage >= mediaList.size) maxOf(0, mediaList.size - 1) else pagerState.currentPage
        val currentFile = mediaList.getOrNull(safePage)
        if (currentFile == null) {
            EmptyState(
                icon = Icons.Default.BrokenImage,
                title = stringResource(R.string.invalid_file_path),
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        val contentUri = currentFile.contentUri ?: Uri.fromFile(File(currentFile.path))
        val isVideo = currentFile.mimeType.startsWith("video/")
        val isAudio = currentFile.mimeType.startsWith("audio/")
        val isImage = currentFile.mimeType.startsWith("image/")

        var isZoomedIn by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.currentPage) {
            isOcrMode = false
            recognizedText = null
            imageSize = IntSize.Zero
            isZoomedIn = false
        }

        // Single player for the whole viewer session: pages share it instead of
        // each building an ExoPlayer (pager keeps neighbours composed, which
        // previously held up to 3 decoder instances alive at once).
        val viewerPlayer = remember { ExoPlayer.Builder(context).build() }
        DisposableEffect(viewerPlayer) {
            onDispose { viewerPlayer.release() }
        }
        LaunchedEffect(currentFile) {
            if (isVideo || isAudio) {
                viewerPlayer.setMediaItem(MediaItem.fromUri(contentUri))
                viewerPlayer.prepare()
                viewerPlayer.playWhenReady = true
            } else {
                viewerPlayer.playWhenReady = false
                viewerPlayer.stop()
            }
        }

        Scaffold(
            topBar = {
                if (!isFullScreen) {
                    TopAppBar(
                        title = { Text(currentFile.name) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                        actions = {
                            if (isImage) {
                                IconButton(onClick = {
                                    if (isOcrMode) {
                                        isOcrMode = false
                                    } else {
                                        isOcrMode = true
                                        if (recognizedText == null && !isOcrLoading) {
                                            isOcrLoading = true
                                            coroutineScope.launch {
                                                val recognizer = TextRecognition.getClient(
                                                    JapaneseTextRecognizerOptions.Builder().build(),
                                                )
                                                try {
                                                    val inputImage = withContext(Dispatchers.IO) {
                                                        InputImage.fromFilePath(context, contentUri)
                                                    }
                                                    val size = IntSize(inputImage.width, inputImage.height)
                                                    val result = recognizer.process(inputImage).await()
                                                    imageSize = size
                                                    recognizedText = result
                                                } catch (_: Exception) {
                                                    recognizedText = null
                                                } finally {
                                                    runCatching { recognizer.close() }
                                                    isOcrLoading = false
                                                }
                                            }
                                        }
                                    }
                                }) {
                                    if (isOcrLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = ViewerOnSurface,
                                            strokeWidth = 2.dp
                                        )
                                    } else if (isOcrMode) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_ocr))
                                    } else {
                                        Icon(Icons.Default.TextFormat, contentDescription = stringResource(R.string.ocr))
                                    }
                                }
                            }
                            
                            if (isImage || isVideo) {
                                IconButton(onClick = {
                                    val encoded = Uri.encode(contentUri.toString())
                                    navController.navigate(
                                        if (isImage) "imageEditor/$encoded" else "videoEditor/$encoded"
                                    ) { launchSingleTop = true }
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                }
                            }
                            var confirmDelete by remember { mutableStateOf(false) }
                            if (confirmDelete) {
                                ConfirmDeleteDialog(
                                    title = stringResource(R.string.delete),
                                    message = currentFile.name,
                                    confirmLabel = stringResource(R.string.delete),
                                    dismissLabel = stringResource(R.string.cancel),
                                    onDismiss = { confirmDelete = false },
                                    onConfirm = {
                                        confirmDelete = false
                                        val deletedPath = currentFile.path
                                        coroutineScope.launch {
                                            viewModel.deleteFile(deletedPath, currentFile.contentUri)
                                            navController.popBackStack()
                                        }
                                    },
                                )
                            }
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                            IconButton(onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = currentFile.mimeType
                                    putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_media)))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                            }
                            var showRenameDialog by remember { mutableStateOf(false) }
                            if (showRenameDialog) {
                                RenameDialog(
                                    currentName = currentFile.name,
                                    onDismiss = { showRenameDialog = false },
                                    onConfirm = { newName ->
                                        showRenameDialog = false
                                        viewModel.renameFile(currentFile.path, currentFile.contentUri, newName)
                                    },
                                )
                            }
                            IconButton(onClick = { showRenameDialog = true }) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = stringResource(R.string.rename))
                            }
                            var showInfoSheet by remember { mutableStateOf(false) }
                            if (showInfoSheet) {
                                FileInfoSheet(
                                    file = currentFile,
                                    player = if (isVideo) viewerPlayer else null,
                                    onDismiss = { showInfoSheet = false },
                                )
                            }
                            IconButton(onClick = { showInfoSheet = true }) {
                                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.file_info))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ViewerChromeContainer,
                            titleContentColor = ViewerOnSurface,
                            actionIconContentColor = ViewerOnSurface,
                            navigationIconContentColor = ViewerOnSurface
                        )
                    )
                }
            }
        ) { innerPadding ->
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(ViewerScrim)
                    .padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding),
                userScrollEnabled = !isOcrMode && !isZoomedIn
            ) { page ->
                val pageFile = mediaList[page]
                val pageUri = pageFile.contentUri ?: Uri.fromFile(File(pageFile.path))
                val pageIsVideo = pageFile.mimeType.startsWith("video/")
                val pageIsAudio = pageFile.mimeType.startsWith("audio/")
                val pageIsImage = pageFile.mimeType.startsWith("image/")

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (pageIsVideo || pageIsAudio) {
                        // Only the settled page owns the surface: binding the shared
                        // player to several PlayerViews would move the video output
                        // to an off-screen page. Neighbours show a placeholder.
                        if (page == pagerState.currentPage) {
                            com.example.ui.components.ZoomableBox(
                                modifier = Modifier.fillMaxSize(),
                                enabled = pageIsVideo,
                                onZoomChanged = { isZoomedIn = it },
                            ) {
                                AndroidView(
                                    factory = {
                                        PlayerView(context).apply {
                                            player = viewerPlayer
                                            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                                                isFullScreen = visibility != android.view.View.VISIBLE
                                            })
                                        }
                                    },
                                    update = { it.player = viewerPlayer },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (pageIsVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = ViewerOnSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else if (pageIsImage) {
                        ImageWithOcrOverlay(
                            uri = pageUri,
                            onTap = { isFullScreen = !isFullScreen },
                            isOcrMode = isOcrMode && page == pagerState.currentPage,
                            recognizedText = if (page == pagerState.currentPage) recognizedText else null,
                            imageSize = if (page == pagerState.currentPage) imageSize else IntSize.Zero,
                            context = context
                        )
                    }
                }
            }
        }
        } // when (Success)
    } // when
}

@Composable
fun ImageWithOcrOverlay(
    uri: Uri,
    isOcrMode: Boolean,
    recognizedText: Text?,
    imageSize: IntSize,
    context: Context,
    onTap: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Text selection state
    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionCurrent by remember { mutableStateOf<Offset?>(null) }
    var selectedLines by remember { mutableStateOf<Set<Text.Line>>(emptySet()) }
    
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun getSelectedText(): String {
        return selectedLines
            .sortedBy { it.boundingBox?.top ?: 0 }
            .joinToString("\n") { it.text }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                // Expose OCR text to TalkBack (was unreachable Canvas-only).
                val text = recognizedText?.text.orEmpty()
                if (text.isNotEmpty()) {
                    contentDescription = text
                }
            }
            .onGloballyPositioned { coordinates ->
                boxSize = coordinates.size
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(isOcrMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = max(1f, scale * zoom)
                    offset += pan
                }
            }
            .pointerInput(isOcrMode, recognizedText) {
                if (isOcrMode && recognizedText != null && imageSize != IntSize.Zero) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { startOffset ->
                            // Convert touch coordinate to image coordinate
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val canvasAspectRatio = boxSize.width.toFloat() / boxSize.height.toFloat()
                            val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
                            
                            var drawWidth = boxSize.width.toFloat()
                            var drawHeight = boxSize.height.toFloat()
                            var drawLeft = 0f
                            var drawTop = 0f
                            
                            if (imageAspectRatio > canvasAspectRatio) {
                                drawHeight = boxSize.width / imageAspectRatio
                                drawTop = (boxSize.height - drawHeight) / 2f
                            } else {
                                drawWidth = boxSize.height * imageAspectRatio
                                drawLeft = (boxSize.width - drawWidth) / 2f
                            }
                            
                            val scaleX = drawWidth / imageSize.width
                            val scaleY = drawHeight / imageSize.height
                            
                            // Transform screen offset to untransformed offset
                            val unscaledOffsetX = (startOffset.x - center.x - offset.x) / scale + center.x
                            val unscaledOffsetY = (startOffset.y - center.y - offset.y) / scale + center.y
                            
                            // Transform to image pixel coordinates
                            val imgX = (unscaledOffsetX - drawLeft) / scaleX
                            val imgY = (unscaledOffsetY - drawTop) / scaleY
                            
                            selectionStart = Offset(imgX, imgY)
                            selectionCurrent = Offset(imgX, imgY)
                            selectedLines = emptySet()
                        },
                        onDrag = { change, _ ->
                            val currentOffset = change.position
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val canvasAspectRatio = boxSize.width.toFloat() / boxSize.height.toFloat()
                            val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
                            
                            var drawWidth = boxSize.width.toFloat()
                            var drawHeight = boxSize.height.toFloat()
                            var drawLeft = 0f
                            var drawTop = 0f
                            
                            if (imageAspectRatio > canvasAspectRatio) {
                                drawHeight = boxSize.width / imageAspectRatio
                                drawTop = (boxSize.height - drawHeight) / 2f
                            } else {
                                drawWidth = boxSize.height * imageAspectRatio
                                drawLeft = (boxSize.width - drawWidth) / 2f
                            }
                            
                            val scaleX = drawWidth / imageSize.width
                            val scaleY = drawHeight / imageSize.height
                            
                            val unscaledOffsetX = (currentOffset.x - center.x - offset.x) / scale + center.x
                            val unscaledOffsetY = (currentOffset.y - center.y - offset.y) / scale + center.y
                            
                            val imgX = (unscaledOffsetX - drawLeft) / scaleX
                            val imgY = (unscaledOffsetY - drawTop) / scaleY
                            
                            selectionCurrent = Offset(imgX, imgY)
                            
                            // Find intersections
                            val s = selectionStart
                            val c = selectionCurrent
                            if (s != null && c != null) {
                                val selRect = Rect(
                                    left = minOf(s.x, c.x),
                                    top = minOf(s.y, c.y),
                                    right = maxOf(s.x, c.x),
                                    bottom = maxOf(s.y, c.y)
                                )
                                
                                val newSelection = mutableSetOf<Text.Line>()
                                for (block in recognizedText.textBlocks) {
                                    for (line in block.lines) {
                                        val box = line.boundingBox
                                        if (box != null) {
                                            val lineRect = Rect(
                                                left = box.left.toFloat(),
                                                top = box.top.toFloat(),
                                                right = box.right.toFloat(),
                                                bottom = box.bottom.toFloat()
                                            )
                                            if (selRect.overlaps(lineRect)) {
                                                newSelection.add(line)
                                            }
                                        }
                                    }
                                }
                                selectedLines = newSelection
                            }
                        },
                        onDragEnd = {
                            val text = getSelectedText()
                            if (text.isNotEmpty()) {
                                val clip = ClipData.newPlainText("OCR Text", text)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            }
                            selectionStart = null
                            selectionCurrent = null
                        }
                    )
                }
            }
    ) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.image_viewer),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
        
        if (isOcrMode && recognizedText != null && imageSize != IntSize.Zero) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                val canvasAspectRatio = size.width / size.height
                val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
                
                var drawWidth = size.width
                var drawHeight = size.height
                var drawLeft = 0f
                var drawTop = 0f
                
                if (imageAspectRatio > canvasAspectRatio) {
                    drawHeight = size.width / imageAspectRatio
                    drawTop = (size.height - drawHeight) / 2f
                } else {
                    drawWidth = size.height * imageAspectRatio
                    drawLeft = (size.width - drawWidth) / 2f
                }
                
                val scaleX = drawWidth / imageSize.width
                val scaleY = drawHeight / imageSize.height

                for (block in recognizedText.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        val isSelected = selectedLines.contains(line)
                        if (box != null) {
                            val rect = Rect(
                                left = drawLeft + box.left * scaleX,
                                top = drawTop + box.top * scaleY,
                                right = drawLeft + box.right * scaleX,
                                bottom = drawTop + box.bottom * scaleY
                            )
                            drawRect(
                                color = if (isSelected) Color.Blue.copy(alpha = 0.5f) else Color.Yellow.copy(alpha = 0.3f),
                                topLeft = Offset(rect.left, rect.top),
                                size = Size(rect.width, rect.height)
                            )
                            drawRect(
                                color = if (isSelected) Color.Blue else Color.Yellow,
                                topLeft = Offset(rect.left, rect.top),
                                size = Size(rect.width, rect.height),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }
                
                // Draw selection rectangle if active
                val s = selectionStart
                val c = selectionCurrent
                if (s != null && c != null) {
                    val startX = drawLeft + s.x * scaleX
                    val startY = drawTop + s.y * scaleY
                    val currX = drawLeft + c.x * scaleX
                    val currY = drawTop + c.y * scaleY
                    
                    val selRect = Rect(
                        left = minOf(startX, currX),
                        top = minOf(startY, currY),
                        right = maxOf(startX, currX),
                        bottom = maxOf(startY, currY)
                    )
                    
                    drawRect(
                        color = Color.Blue.copy(alpha = 0.2f),
                        topLeft = Offset(selRect.left, selRect.top),
                        size = Size(selRect.width, selRect.height)
                    )
                    drawRect(
                        color = Color.Blue,
                        topLeft = Offset(selRect.left, selRect.top),
                        size = Size(selRect.width, selRect.height),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.new_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileInfoSheet(file: MediaFile, player: ExoPlayer?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isImage = file.mimeType.startsWith("image/")
    var resolution by remember(file.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(file.path) {
        if (isImage) {
            resolution = withContext(Dispatchers.IO) {
                runCatching {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val uri = file.contentUri ?: Uri.fromFile(File(file.path))
                    context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, options)
                    }
                    if (options.outWidth > 0 && options.outHeight > 0) "${options.outWidth} × ${options.outHeight}" else null
                }.getOrNull()
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.file_info), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow(stringResource(R.string.file_name), file.name)
            InfoRow(stringResource(R.string.file_path), file.path)
            InfoRow(stringResource(R.string.file_size), formatSizeLocalized(file.size))
            InfoRow(stringResource(R.string.date_modified), formatDate(file.dateModified))
            if (isImage) {
                resolution?.let { InfoRow(stringResource(R.string.resolution), it) }
            }
            if (player != null && player.duration > 0) {
                val totalSeconds = player.duration / 1000
                InfoRow(stringResource(R.string.duration), "%d:%02d".format(totalSeconds / 60, totalSeconds % 60))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
