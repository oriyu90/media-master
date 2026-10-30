package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.R
import com.example.ui.components.EditorSlider
import com.example.ui.editor.AspectOption
import com.example.ui.editor.CropOverlay
import com.example.ui.editor.CropRect
import com.example.ui.editor.GeometryParams
import com.example.ui.editor.PerspectiveOverlay
import com.example.ui.editor.applyPerspective
import com.example.ui.editor.decodeSampledBitmap
import com.example.ui.editor.defaultQuad
import com.example.ui.editor.downscaleBitmap
import com.example.ui.editor.fitAspect
import com.example.ui.editor.fitImageRect
import com.example.ui.editor.isUsableQuad
import com.example.ui.editor.resolveRatio
import com.example.ui.editor.saveEditedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TAB_COLOR = 0
private const val TAB_TRANSFORM = 1
private const val TAB_CROP = 2

/**
 * v1.14.0 image editor.
 *
 * The geometry (flip / tilt / rotate) is rendered into a preview bitmap with
 * the *same* function the encoder uses, so crop and perspective overlays can
 * trust one coordinate space. The crop tab gains aspect-ratio presets and the
 * transform tab gains a Google-Photos-style "align corners" mode that bakes
 * the selected quad into the image on OK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(uriString: String, navController: NavHostController) {
    val uri = remember(uriString) { Uri.parse(uriString) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewSource by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isOpening by remember { mutableStateOf(true) }
    var openFailed by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isApplyingPerspective by remember { mutableStateOf(false) }

    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_COLOR) }
    val adjustmentValues = remember { mutableStateMapOf<AdjustmentType, Float>() }

    // Transform
    var rotationZ by rememberSaveable { mutableFloatStateOf(0f) }
    var rotationX by rememberSaveable { mutableFloatStateOf(0f) }
    var rotationY by rememberSaveable { mutableFloatStateOf(0f) }
    var flipHorizontal by rememberSaveable { mutableStateOf(false) }

    // Crop / perspective
    var cropRect by remember { mutableStateOf(CropRect.FULL) }
    var aspectOption by remember { mutableStateOf(AspectOption.FREE) }
    var perspectiveMode by remember { mutableStateOf(false) }
    var quad by remember { mutableStateOf(defaultQuad()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val geometry = GeometryParams(
        flipHorizontal = flipHorizontal,
        tiltX = rotationX,
        tiltY = rotationY,
        rotateZ = rotationZ,
    )

    LaunchedEffect(uri) {
        isOpening = true
        openFailed = false
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeSampledBitmap(context, uri) }.getOrNull()
        }
        if (decoded == null) {
            openFailed = true
            isOpening = false
        } else {
            bitmap = decoded
            previewBitmap = decoded
            val preview = withContext(Dispatchers.Default) {
                runCatching { downscaleBitmap(decoded) }.getOrDefault(decoded)
            }
            previewSource = preview
            isOpening = false
        }
    }

    // Re-render the preview with the same geometry function the save uses.
    LaunchedEffect(previewSource, flipHorizontal, rotationX, rotationY, rotationZ) {
        val source = previewSource ?: return@LaunchedEffect
        val params = geometry
        val rendered = withContext(Dispatchers.Default) {
            runCatching { com.example.ui.editor.applyGeometry(source, params) }.getOrNull()
        }
        if (rendered != null) previewBitmap = rendered
    }

    val colorMatrix = remember(adjustmentValues.toMap()) { buildColorMatrix(adjustmentValues) }

    val previewWidth = previewBitmap?.width ?: 1
    val previewHeight = previewBitmap?.height ?: 1
    val imageAspect = previewWidth.toFloat() / previewHeight.coerceAtLeast(1)
    val activeRatio = aspectOption.resolveRatio(imageAspect)
    val imageFit = remember(containerSize, previewWidth, previewHeight) {
        fitImageRect(
            containerWidth = containerSize.width.toFloat(),
            containerHeight = containerSize.height.toFloat(),
            imageWidth = previewWidth.toFloat(),
            imageHeight = previewHeight.toFloat(),
        )
    }

    // A rotate/flip changes the bitmap aspect; re-fit a locked crop frame so
    // the visible frame keeps the ratio the chip promises.
    LaunchedEffect(imageAspect, aspectOption) {
        val ratio = aspectOption.resolveRatio(imageAspect)
        if (ratio != null && !perspectiveMode) {
            cropRect = fitAspect(cropRect, ratio, imageAspect)
        }
    }

    fun save() {
        val source = bitmap ?: return
        // In align-corners mode the quad is applied with OK; saving halfway
        // would silently drop it.
        if (isSaving || isApplyingPerspective || perspectiveMode) return
        coroutineScope.launch {
            isSaving = true
            val adjustments = adjustmentValues.toMap()
            val androidColorMatrix = if (hasColorAdjustments(adjustments)) {
                android.graphics.ColorMatrix(colorMatrix.values)
            } else {
                null
            }
            val result = saveEditedImage(
                context = context,
                source = source,
                params = geometry,
                crop = cropRect,
                colorMatrix = androidColorMatrix,
            )
            isSaving = false
            result
                .onSuccess { navController.popBackStack() }
                .onFailure {
                    Toast.makeText(context, R.string.save_failed, Toast.LENGTH_LONG).show()
                }
        }
    }

    // Back inside align-corners cancels the mode (and keeps the edit session)
    // instead of popping the whole editor.
    androidx.activity.compose.BackHandler(enabled = perspectiveMode) {
        perspectiveMode = false
        quad = defaultQuad()
    }

    fun applyPerspectiveNow() {
        val source = bitmap ?: return
        if (isApplyingPerspective || isSaving) return
        coroutineScope.launch {
            isApplyingPerspective = true
            val baked = withContext(Dispatchers.Default) {
                runCatching { applyPerspective(source, geometry, quad) }.getOrNull()
            }
            if (baked == null) {
                isApplyingPerspective = false
                Toast.makeText(context, R.string.perspective_invalid, Toast.LENGTH_LONG).show()
            } else {
                bitmap = baked
                previewBitmap = baked
                val preview = withContext(Dispatchers.Default) {
                    runCatching { downscaleBitmap(baked) }.getOrDefault(baked)
                }
                previewSource = preview
                // Geometry is baked into the pixels now; reset the sliders so
                // the values keep describing the visible image.
                flipHorizontal = false
                rotationX = 0f
                rotationY = 0f
                rotationZ = 0f
                cropRect = CropRect.FULL
                aspectOption = AspectOption.FREE
                quad = defaultQuad()
                perspectiveMode = false
                isApplyingPerspective = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (perspectiveMode) R.string.perspective_align_corners else R.string.edit_image,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (perspectiveMode) {
                                perspectiveMode = false
                                quad = defaultQuad()
                            } else {
                                navController.popBackStack()
                            }
                        },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(
                        onClick = ::save,
                        enabled = bitmap != null && !isSaving && !isApplyingPerspective && !perspectiveMode,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            // Short landscape windows must still leave room for the image.
            val controlsHeight = (maxHeight * 0.46f)
                .coerceIn(150.dp, 360.dp)
                .coerceAtMost(maxHeight * 0.62f)
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { containerSize = it.size },
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = previewBitmap
                    if (shown != null) {
                        Image(
                            bitmap = shown.asImageBitmap(),
                            contentDescription = stringResource(R.string.editing_image),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        )
                    }
                    if (shown != null && !isOpening && !openFailed) {
                        if (perspectiveMode) {
                            PerspectiveOverlay(
                                fit = imageFit,
                                quad = quad,
                                onChange = { quad = it },
                            )
                        } else if (selectedTab == TAB_CROP) {
                            CropOverlay(
                                fit = imageFit,
                                cropRect = cropRect,
                                ratio = activeRatio,
                                imageAspect = imageAspect,
                                onChange = { cropRect = it },
                            )
                        }
                    }
                    if (isOpening) {
                        CircularProgressIndicator(color = Color.White)
                    } else if (openFailed) {
                        Text(
                            text = stringResource(R.string.image_open_failed),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    if (isSaving || isApplyingPerspective) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }

                if (perspectiveMode) {
                    PerspectivePanel(
                        quadUsable = isUsableQuad(quad),
                        enabled = !isApplyingPerspective && !isSaving,
                        onReset = { quad = defaultQuad() },
                        onCancel = {
                            perspectiveMode = false
                            quad = defaultQuad()
                        },
                        onApply = ::applyPerspectiveNow,
                    )
                } else {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == TAB_COLOR,
                            onClick = { selectedTab = TAB_COLOR },
                            text = { Text(stringResource(R.string.color)) },
                        )
                        Tab(
                            selected = selectedTab == TAB_TRANSFORM,
                            onClick = { selectedTab = TAB_TRANSFORM },
                            text = { Text(stringResource(R.string.transform)) },
                        )
                        Tab(
                            selected = selectedTab == TAB_CROP,
                            onClick = { selectedTab = TAB_CROP },
                            text = { Text(stringResource(R.string.crop)) },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(controlsHeight)
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        when (selectedTab) {
                            TAB_COLOR -> AdjustmentControls(
                                adjustmentValues = adjustmentValues,
                                onAdjustmentChange = { type, value -> adjustmentValues[type] = value },
                            )

                            TAB_TRANSFORM -> TransformControls(
                                rotationX = rotationX,
                                rotationY = rotationY,
                                rotationZ = rotationZ,
                                onRotationX = { rotationX = it },
                                onRotationY = { rotationY = it },
                                onRotationZ = { rotationZ = it },
                                onFlip = { flipHorizontal = !flipHorizontal },
                                onRotate90 = {
                                    rotationZ = normalizeDegrees(rotationZ + 90f)
                                },
                                onAlignCorners = {
                                    quad = defaultQuad()
                                    perspectiveMode = true
                                },
                            )

                            else -> CropControls(
                                selected = aspectOption,
                                onSelect = { option ->
                                    aspectOption = option
                                    option.resolveRatio(imageAspect)?.let { ratio ->
                                        cropRect = fitAspect(cropRect, ratio, imageAspect)
                                    }
                                },
                                onReset = {
                                    cropRect = CropRect.FULL
                                    aspectOption = AspectOption.FREE
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformControls(
    rotationX: Float,
    rotationY: Float,
    rotationZ: Float,
    onRotationX: (Float) -> Unit,
    onRotationY: (Float) -> Unit,
    onRotationZ: (Float) -> Unit,
    onFlip: () -> Unit,
    onRotate90: () -> Unit,
    onAlignCorners: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // The three actions scroll horizontally so a narrow phone never clips
        // the new "align corners" entry.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onFlip) {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.flip_horizontal))
            }
            OutlinedButton(onClick = onRotate90) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.rotate_90))
            }
            OutlinedButton(onClick = onAlignCorners) {
                Icon(Icons.Default.CropFree, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.perspective_align_corners))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        EditorSlider(
            label = stringResource(R.string.tilt_x),
            value = rotationX,
            defaultValue = 0f,
            valueRange = -45f..45f,
            onValueChange = onRotationX,
        )
        EditorSlider(
            label = stringResource(R.string.tilt_y),
            value = rotationY,
            defaultValue = 0f,
            valueRange = -45f..45f,
            onValueChange = onRotationY,
        )
        EditorSlider(
            label = stringResource(R.string.tilt_z),
            value = rotationZ,
            defaultValue = 0f,
            valueRange = -180f..180f,
            onValueChange = onRotationZ,
        )
    }
}

@Composable
private fun CropControls(
    selected: AspectOption,
    onSelect: (AspectOption) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.crop_aspect_ratio),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(AspectOption.entries, key = { it.name }, contentType = { "aspect" }) { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.crop_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = onReset,
            modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.reset_crop))
        }
    }
}

@Composable
private fun PerspectivePanel(
    quadUsable: Boolean,
    enabled: Boolean,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.perspective_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!quadUsable) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.perspective_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onReset, enabled = enabled) {
                    Text(stringResource(R.string.reset_corners))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel, enabled = enabled) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = onApply, enabled = enabled && quadUsable) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

/** Mirrors the preview colour matrix. */
private fun buildColorMatrix(values: Map<AdjustmentType, Float>): ColorMatrix {
    val brightness = values[AdjustmentType.BRIGHTNESS] ?: 0f
    val contrast = values[AdjustmentType.CONTRAST] ?: 1f
    val saturation = values[AdjustmentType.SATURATION] ?: 1f
    val matrix = ColorMatrix().apply { setToSaturation(saturation) }
    val scale = contrast
    val translate = brightness * 255f
    val contrastBrightness = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    matrix.timesAssign(contrastBrightness)
    return matrix
}

/** True when any colour slider differs from its neutral value. */
private fun hasColorAdjustments(values: Map<AdjustmentType, Float>): Boolean {
    val brightness = values[AdjustmentType.BRIGHTNESS] ?: 0f
    val contrast = values[AdjustmentType.CONTRAST] ?: 1f
    val saturation = values[AdjustmentType.SATURATION] ?: 1f
    return abs(brightness) > 0.001f || abs(contrast - 1f) > 0.001f || abs(saturation - 1f) > 0.001f
}

/**
 * Normalise a rotation to (-180, 180] so the Z slider and the 90° button
 * share one value space (90, 180, -90, 0, ...).
 */
internal fun normalizeDegrees(degrees: Float): Float {
    var value = degrees % 360f
    if (value > 180f) value -= 360f
    if (value <= -180f) value += 360f
    return value
}
