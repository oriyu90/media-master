package com.example.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.FileViewModel
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.MediaFile
import com.example.ViewState
import com.example.formatSizeLocalized
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDashboardScreen(navController: NavHostController, viewModel: FileViewModel) {
    // Same canonical roots as the browser and DeX sidebar (incl. SD/USB), resolved off the main thread.
    var storageRoots by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        storageRoots = withContext(Dispatchers.IO) { viewModel.storageRoots() }
        viewModel.loadAllMedia()
    }
    val mediaViewState by viewModel.mediaState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StorageUsageCard(storageRoots.firstOrNull(), mediaViewState)
            }
            item {
                RecentFilesRow(mediaViewState) { file ->
                    navController.navigate("viewer/${android.net.Uri.encode(file.path)}")
                }
            }
            item {
                Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleMedium)
            }
            item {
                CategoryGrid { category ->
                    if (category == MediaCategory.APPS) {
                        navController.navigate("apps")
                    } else {
                        navController.navigate("category/${android.net.Uri.encode(category.key)}")
                    }
                }
            }
            item {
                Text(stringResource(R.string.storage_devices), style = MaterialTheme.typography.titleMedium)
                storageRoots.forEachIndexed { index, path ->
                    val isInternal = path.contains("emulated")
                    val title = if (isInternal) stringResource(R.string.internal_storage) else stringResource(R.string.external_storage)
                    val icon = if (isInternal) Icons.Default.Smartphone else Icons.Default.SdStorage
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            navController.navigate("file_browser?path=${android.net.Uri.encode(path)}")
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = { Text(stringResource(R.string.browse_all_folders)) },
                            leadingContent = { Icon(icon, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                    if (index < storageRoots.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            item {
                Text(stringResource(R.string.clean), style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        navController.navigate("clean")
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.clean_duplicates)) },
                        supportingContent = { Text(stringResource(R.string.free_up_space)) },
                        leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
    }
}

private val categoryColors = listOf(
    Color(0xFF4C8DF6), // Downloads
    Color(0xFF34A853), // Images
    Color(0xFFEA4335), // Videos
    Color(0xFFFBBC05), // Audio
    Color(0xFFAB47BC), // Documents
    Color(0xFF00ACC1), // Apps
)
private val otherColor = Color(0xFF9AA0A6)

/**
 * Files-by-Google/My-Files-style storage summary: a segmented bar (bytes per
 * MediaCategory, reusing MediaCategory.matches instead of new repository
 * queries) plus used/total space from StatFs on the primary storage root.
 */
@Composable
private fun StorageUsageCard(primaryRoot: String?, mediaViewState: ViewState) {
    val files = (mediaViewState as? ViewState.Success)?.files.orEmpty()
    val byCategory = remember(files) {
        MediaCategory.entries.associateWith { category -> files.filter { category.matches(it) }.sumOf { it.size } }
    }
    val categorizedBytes = byCategory.values.sum()
    val otherBytes = (files.sumOf { it.size } - categorizedBytes).coerceAtLeast(0)

    val statFs = remember(primaryRoot) {
        runCatching { android.os.StatFs(primaryRoot ?: android.os.Environment.getExternalStorageDirectory().path) }.getOrNull()
    }
    val totalBytes = statFs?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
    val freeBytes = statFs?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (totalBytes > 0) {
                Text(
                    stringResource(R.string.storage_used_of_total, formatSizeLocalized(usedBytes), formatSizeLocalized(totalBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShapeAll(6.dp)),
                ) {
                    MediaCategory.entries.forEachIndexed { i, category ->
                        val bytes = byCategory[category] ?: 0L
                        if (bytes > 0) {
                            Box(modifier = Modifier.weight(bytes.toFloat()).fillMaxHeight().background(categoryColors[i % categoryColors.size]))
                        }
                    }
                    if (otherBytes > 0) {
                        Box(modifier = Modifier.weight(otherBytes.toFloat()).fillMaxHeight().background(otherColor))
                    }
                    val unusedBytes = (freeBytes).coerceAtLeast(1L)
                    Box(modifier = Modifier.weight(unusedBytes.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun RoundedCornerShapeAll(radius: androidx.compose.ui.unit.Dp) =
    androidx.compose.foundation.shape.RoundedCornerShape(radius)

@Composable
private fun RecentFilesRow(mediaViewState: ViewState, onOpen: (MediaFile) -> Unit) {
    val files = (mediaViewState as? ViewState.Success)?.files.orEmpty()
    val recent = remember(files) { files.sortedByDescending { it.dateModified }.take(10) }
    if (recent.isEmpty()) return
    Column {
        Text(stringResource(R.string.recent_files), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recent.size) { i ->
                val file = recent[i]
                Card(
                    modifier = Modifier.width(96.dp).clickable { onOpen(file) },
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            categoryIconFor(file),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            file.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun categoryIconFor(file: MediaFile): androidx.compose.ui.graphics.vector.ImageVector =
    MediaCategory.entries.firstOrNull { it.matches(file) }?.icon ?: Icons.AutoMirrored.Filled.InsertDriveFile

@Composable
fun CategoryGrid(onCategoryClick: (MediaCategory) -> Unit) {
    val categories = MediaCategory.entries
    BoxWithConstraints {
        val columnCount = if (maxWidth < 360.dp) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in categories.indices step columnCount) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (offset in 0 until columnCount) {
                        val category = categories.getOrNull(i + offset)
                        Box(modifier = Modifier.weight(1f)) {
                            if (category != null) {
                                CategoryCard(stringResource(category.titleRes), category.icon) {
                                    onCategoryClick(category)
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
fun CategoryCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
