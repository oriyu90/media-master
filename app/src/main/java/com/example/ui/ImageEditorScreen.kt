package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(uriString: String, navController: NavHostController) {
    val uri = Uri.parse(uriString)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Editor tab pager, hoisted so the image overlay can react to the selected tab
    // without writing state during composition.
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Color
    val adjustmentValues = remember { mutableStateMapOf<AdjustmentType, Float>() }

    // Transform
    var rotationZ by remember { mutableFloatStateOf(0f) }
    var rotationX by remember { mutableFloatStateOf(0f) }
    var rotationY by remember { mutableFloatStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }

    // Crop / Perspective — derived from the active tab (page 2 == Crop).
    val isPerspectiveMode = pagerState.currentPage == 2
    var tl by remember { mutableStateOf(Offset(0f, 0f)) }
    var tr by remember { mutableStateOf(Offset(1f, 0f)) }
    var bl by remember { mutableStateOf(Offset(0f, 1f)) }
    var br by remember { mutableStateOf(Offset(1f, 1f)) }
    
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val colorMatrix = remember(adjustmentValues.toMap()) {
        val brightness = adjustmentValues[AdjustmentType.BRIGHTNESS] ?: 0f
        val contrast = adjustmentValues[AdjustmentType.CONTRAST] ?: 1f
        val saturation = adjustmentValues[AdjustmentType.SATURATION] ?: 1f
        val cm = ColorMatrix().apply { setToSaturation(saturation) }
        val scale = contrast
        val translate = brightness * 255f
        val contrastBrightnessMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.timesAssign(contrastBrightnessMatrix)
        cm
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_image)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isSaving = true
                            saveImage(context, bitmap, colorMatrix, rotationZ, rotationX, rotationY, flipHorizontal, tl, tr, bl, br)
                            isSaving = false
                            navController.popBackStack()
                        }
                    }, enabled = bitmap != null && !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        val controlsHeight = (maxHeight * 0.38f).coerceIn(140.dp, 260.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { imageSize = it.size },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.editing_image),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                rotationZ = rotationZ,
                                rotationY = rotationY,
                                rotationX = rotationX,
                                scaleX = if (flipHorizontal) -1f else 1f
                            ),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(colorMatrix)
                    )
                    
                    if (isPerspectiveMode && imageSize != IntSize.Zero) {
                        // Overlay for 4-point perspective
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            
                            val p1 = Offset(tl.x * w, tl.y * h)
                            val p2 = Offset(tr.x * w, tr.y * h)
                            val p3 = Offset(br.x * w, br.y * h)
                            val p4 = Offset(bl.x * w, bl.y * h)
                            
                            val path = Path().apply {
                                moveTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                lineTo(p3.x, p3.y)
                                lineTo(p4.x, p4.y)
                                close()
                            }
                            drawPath(path, color = Color.White.copy(alpha = 0.5f), style = Stroke(width = 4.dp.toPx()))
                            
                            drawCircle(Color.Red, radius = 10.dp.toPx(), center = p1)
                            drawCircle(Color.Red, radius = 10.dp.toPx(), center = p2)
                            drawCircle(Color.Red, radius = 10.dp.toPx(), center = p3)
                            drawCircle(Color.Red, radius = 10.dp.toPx(), center = p4)
                        }
                        
                        // Transparent draggable boxes
                        Box(modifier = Modifier.fillMaxSize()) {
                            val w = imageSize.width.toFloat()
                            val h = imageSize.height.toFloat()
                            
                            DraggableCorner(tl, w, h) { tl = it }
                            DraggableCorner(tr, w, h) { tr = it }
                            DraggableCorner(bl, w, h) { bl = it }
                            DraggableCorner(br, w, h) { br = it }
                        }
                    }
                }
            }
            
            // Tabs
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(selected = pagerState.currentPage == 0, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }) {
                    Text(stringResource(R.string.color), modifier = Modifier.padding(16.dp))
                }
                Tab(selected = pagerState.currentPage == 1, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }) {
                    Text(stringResource(R.string.transform), modifier = Modifier.padding(16.dp))
                }
                Tab(selected = pagerState.currentPage == 2, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }) {
                    Text(stringResource(R.string.crop), modifier = Modifier.padding(16.dp))
                }
            }
            
            HorizontalPager(state = pagerState, modifier = Modifier
                .fillMaxWidth()
                .height(controlsHeight)
                .background(MaterialTheme.colorScheme.surface)) { page ->
                when (page) {
                    0 -> {
                        AdjustmentControls(
                            adjustmentValues = adjustmentValues,
                            onAdjustmentChange = { type, value -> adjustmentValues[type] = value }
                        )
                    }
                    1 -> {
                        Column(modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Button(onClick = { flipHorizontal = !flipHorizontal }) {
                                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = stringResource(R.string.flip_horizontal))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.flip_horizontal))
                                }
                                Button(onClick = { rotationZ = (rotationZ + 90f) % 360f }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.rotate_90))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.rotate_90))
                                }
                            }
                            Text(stringResource(R.string.tilt_x))
                            Slider(value = rotationX, onValueChange = { rotationX = it }, valueRange = -45f..45f)
                            Text(stringResource(R.string.tilt_y))
                            Slider(value = rotationY, onValueChange = { rotationY = it }, valueRange = -45f..45f)
                            Text(stringResource(R.string.tilt_z))
                            Slider(value = rotationZ, onValueChange = { rotationZ = it }, valueRange = -180f..180f)
                        }
                    }
                    2 -> {
                        Column(modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.perspective_crop_help))
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                tl = Offset(0f, 0f)
                                tr = Offset(1f, 0f)
                                bl = Offset(0f, 1f)
                                br = Offset(1f, 1f)
                            }) {
                                Text(stringResource(R.string.reset_corners))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun DraggableCorner(offset: Offset, w: Float, h: Float, onOffsetChange: (Offset) -> Unit) {
    val pxX = offset.x * w
    val pxY = offset.y * h
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val cornerLabel = stringResource(R.string.perspective_crop_help)
    Box(
        modifier = Modifier
            .offset(x = (pxX / density - 24f).dp, y = (pxY / density - 24f).dp)
            .size(48.dp)
            .semantics { contentDescription = cornerLabel }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newX = (pxX + dragAmount.x).coerceIn(0f, w)
                    val newY = (pxY + dragAmount.y).coerceIn(0f, h)
                    onOffsetChange(Offset(newX / w, newY / h))
                }
            }
    )
}

suspend fun saveImage(
    context: Context, originalBitmap: Bitmap?, colorMatrix: ColorMatrix,
    rotationZ: Float, rotationX: Float, rotationY: Float, flipHorizontal: Boolean,
    tl: Offset, tr: Offset, bl: Offset, br: Offset
) {
    if (originalBitmap == null) return
    withContext(Dispatchers.IO) {
        try {
            val matrix = android.graphics.Matrix()
            val cx = originalBitmap.width / 2f
            val cy = originalBitmap.height / 2f
            
            if (flipHorizontal) {
                matrix.postScale(-1f, 1f, cx, cy)
            }
            
            val camera = android.graphics.Camera()
            val cameraMatrix = android.graphics.Matrix()
            camera.save()
            camera.rotateX(-rotationX) // Pitch
            camera.rotateY(rotationY)  // Yaw
            camera.getMatrix(cameraMatrix)
            camera.restore()
            
            cameraMatrix.preTranslate(-cx, -cy)
            cameraMatrix.postTranslate(cx, cy)
            
            matrix.postConcat(cameraMatrix)
            matrix.postRotate(rotationZ, cx, cy)
            
            var rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            
            // Perspective Crop
            if (tl != Offset(0f, 0f) || tr != Offset(1f, 0f) || bl != Offset(0f, 1f) || br != Offset(1f, 1f)) {
                val w = rotatedBitmap.width.toFloat()
                val h = rotatedBitmap.height.toFloat()
                val src = floatArrayOf(
                    tl.x * w, tl.y * h,
                    tr.x * w, tr.y * h,
                    br.x * w, br.y * h,
                    bl.x * w, bl.y * h
                )
                
                // Determine new size based on top width and left height
                val dstW = max(tr.x * w - tl.x * w, br.x * w - bl.x * w)
                val dstH = max(bl.y * h - tl.y * h, br.y * h - tr.y * h)
                
                if (dstW > 0 && dstH > 0) {
                    val dst = floatArrayOf(
                        0f, 0f,
                        dstW, 0f,
                        dstW, dstH,
                        0f, dstH
                    )
                    val polyMatrix = android.graphics.Matrix()
                    polyMatrix.setPolyToPoly(src, 0, dst, 0, 4)
                    
                    val perspectiveBitmap = Bitmap.createBitmap(dstW.toInt(), dstH.toInt(), Bitmap.Config.ARGB_8888)
                    val pCanvas = android.graphics.Canvas(perspectiveBitmap)
                    pCanvas.drawBitmap(rotatedBitmap, polyMatrix, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
                    rotatedBitmap = perspectiveBitmap
                }
            }
            
            val resultBitmap = Bitmap.createBitmap(rotatedBitmap.width, rotatedBitmap.height, rotatedBitmap.config ?: Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(resultBitmap)
            val paint = android.graphics.Paint()
            val androidColorMatrix = android.graphics.ColorMatrix(colorMatrix.values)
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(androidColorMatrix)
            canvas.drawBitmap(rotatedBitmap, 0f, 0f, paint)
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Edited_Image_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Edited")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    outputStream.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
