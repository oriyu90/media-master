package com.example.ui

import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDashboardScreen(navController: NavHostController) {
    val context = LocalContext.current
    val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
    val storageRoots = externalDirs.mapNotNull { dir ->
        if (dir != null) {
            val path = dir.absolutePath
            val androidIndex = path.indexOf("/Android/data/")
            if (androidIndex != -1) {
                path.substring(0, androidIndex)
            } else {
                null
            }
        } else null
    }.distinct()
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
                Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleMedium)
            }
            item {
                CategoryGrid { category ->
                    if (category == "Apps") {
                        navController.navigate("apps")
                    } else {
                        navController.navigate("category/${android.net.Uri.encode(category)}")
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

@Composable
fun CategoryGrid(onCategoryClick: (String) -> Unit) {
    val categories = listOf(
        Triple("Downloads", R.string.downloads, Icons.Default.Download),
        Triple("Images", R.string.images, Icons.Default.Image),
        Triple("Videos", R.string.videos, Icons.Default.VideoLibrary),
        Triple("Audio", R.string.audio, Icons.Default.Audiotrack),
        Triple("Documents", R.string.documents, Icons.Default.Description),
        Triple("Apps", R.string.apps, Icons.Default.Apps)
    )
    BoxWithConstraints {
        val columnCount = if (maxWidth < 360.dp) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in categories.indices step columnCount) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (offset in 0 until columnCount) {
                        val category = categories.getOrNull(i + offset)
                        Box(modifier = Modifier.weight(1f)) {
                            if (category != null) {
                                CategoryCard(stringResource(category.second), category.third) {
                                    onCategoryClick(category.first)
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp).clickable(onClick = onClick),
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
