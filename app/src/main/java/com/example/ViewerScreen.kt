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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(path: String?, viewModel: FileViewModel?, navController: NavHostController) {
    if (path == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.invalid_file_path))
        }
        return
    }

    val context = LocalContext.current
    val viewState = viewModel?.mediaState?.collectAsStateWithLifecycle()?.value

    var isFullScreen by remember { mutableStateOf(false) }
    
    // OCR State
    var isOcrMode by remember { mutableStateOf(false) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf<Text?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    val coroutineScope = rememberCoroutineScope()

    if (viewState is ViewState.Success) {
        val targetFile = viewState.files.find { it.path == path } ?: return
        val isTargetAudio = targetFile.mimeType.startsWith("audio/")
        val isTargetImageOrVideo = targetFile.mimeType.startsWith("image/") || targetFile.mimeType.startsWith("video/")
        
        val mediaList = if (isTargetImageOrVideo) {
            viewState.files.filter { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }
        } else if (isTargetAudio) {
            viewState.files.filter { it.mimeType.startsWith("audio/") }
        } else {
            listOf(targetFile)
        }
        
        val initialIndex = mediaList.indexOfFirst { it.path == path }.coerceAtLeast(0)
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = initialIndex,
            pageCount = { mediaList.size }
        )

        val safePage = if (pagerState.currentPage >= mediaList.size) maxOf(0, mediaList.size - 1) else pagerState.currentPage
        val currentFile = mediaList.getOrNull(safePage) ?: return
        val contentUri = currentFile.contentUri ?: Uri.fromFile(File(currentFile.path))
        val isVideo = currentFile.mimeType.startsWith("video/")
        val isAudio = currentFile.mimeType.startsWith("audio/")
        val isImage = currentFile.mimeType.startsWith("image/")

        LaunchedEffect(pagerState.currentPage) {
            isOcrMode = false
            recognizedText = null
            imageSize = IntSize.Zero
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
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val inputImage = InputImage.fromFilePath(context, contentUri)
                                                    imageSize = IntSize(inputImage.width, inputImage.height)
                                                    
                                                    val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                                    val japaneseRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                                                    
                                                    val latinTask = async { latinRecognizer.process(inputImage).await() }
                                                    val japaneseTask = async { japaneseRecognizer.process(inputImage).await() }
                                                    
                                                    val (latinResult, japaneseResult) = awaitAll(latinTask, japaneseTask)
                                                    
                                                    recognizedText = if (japaneseResult.text.length > latinResult.text.length) {
                                                        japaneseResult
                                                    } else {
                                                        latinResult
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                } finally {
                                                    isOcrLoading = false
                                                }
                                            }
                                        }
                                    }
                                }) {
                                    if (isOcrLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else if (isOcrMode) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_ocr))
                                    } else {
                                        Icon(Icons.Default.TextFormat, contentDescription = stringResource(R.string.ocr))
                                    }
                                }
                            }
                            
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    viewModel.deleteFile(currentFile.path, currentFile.contentUri)
                                    if (mediaList.size <= 1) {
                                        navController.popBackStack()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                            IconButton(onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = currentFile.mimeType
                                    putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_media)))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            titleContentColor = Color.White,
                            actionIconContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                }
            }
        ) { innerPadding ->
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding),
                userScrollEnabled = !isOcrMode
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
                        val exoPlayer = remember(pageUri) {
                            ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(pageUri))
                                prepare()
                                playWhenReady = (page == pagerState.currentPage)
                            }
                        }
                        
                        DisposableEffect(exoPlayer) {
                            onDispose {
                                exoPlayer.release()
                            }
                        }

                        LaunchedEffect(pagerState.currentPage) {
                            if (page == pagerState.currentPage) {
                                exoPlayer.playWhenReady = true
                            } else {
                                exoPlayer.playWhenReady = false
                                exoPlayer.seekTo(0)
                            }
                        }

                        DisposableEffect(pageUri) {
                            onDispose {
                                exoPlayer.release()
                            }
                        }

                        AndroidView(
                            factory = {
                                PlayerView(context).apply {
                                    player = exoPlayer
                                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                                        isFullScreen = visibility != android.view.View.VISIBLE
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
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
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
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
                            if (selectionStart != null && selectionCurrent != null) {
                                val s = selectionStart!!
                                val c = selectionCurrent!!
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
