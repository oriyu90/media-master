package com.example.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.FileViewModel
import com.example.R
import com.example.ViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val name: String,
    val packageName: String,
    val sourceDir: String,
    val icon: Drawable
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppManagerScreen(viewModel: FileViewModel, navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewState by viewModel.mediaState.collectAsStateWithLifecycle()
    
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    
    val selectedApps = remember { mutableStateListOf<String>() } // Package names for apps
    val selectedApks = remember { mutableStateListOf<String>() } // File paths for apks
    
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    LaunchedEffect(Unit) {
        viewModel.loadAllMedia()
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val apps = packages // Show all apps, including system apps, to ensure list is not empty
                .map { info ->
                    AppInfo(
                        name = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        sourceDir = info.sourceDir,
                        icon = pm.getApplicationIcon(info)
                    )
                }.sortedBy { it.name }
            withContext(Dispatchers.Main) {
                installedApps = apps
                isLoadingApps = false
            }
        }
    }

    val isAppSelectionMode = selectedApps.isNotEmpty() && pagerState.currentPage == 0
    val isApkSelectionMode = selectedApks.isNotEmpty() && pagerState.currentPage == 1

    Scaffold(
        topBar = {
            if (isAppSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedApps.size} ${stringResource(R.string.selected)}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedApps.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            val uris = selectedApps.mapNotNull { pkg ->
                                val app = installedApps.find { it.packageName == pkg }
                                app?.sourceDir?.let { Uri.fromFile(File(it)) }
                            }
                            if (uris.isNotEmpty()) {
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "application/vnd.android.package-archive"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_apk)))
                            }
                            selectedApps.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = {
                            selectedApps.forEach { pkg ->
                                val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                                    }
                                } else {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:$pkg")
                                    }
                                }
                                context.startActivity(intent)
                            }
                            selectedApps.clear()
                        }) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = stringResource(R.string.notification_settings))
                        }
                        IconButton(onClick = { 
                            selectedApps.forEach { pkg ->
                                val intent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:$pkg")
                                }
                                context.startActivity(intent)
                            }
                            selectedApps.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                )
            } else if (isApkSelectionMode) {
                 TopAppBar(
                    title = { Text("${selectedApks.size} ${stringResource(R.string.selected)}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedApks.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            val uris = selectedApks.mapNotNull { path ->
                                (viewState as? ViewState.Success)?.files?.find { it.path == path }?.contentUri
                            }
                            if (uris.isNotEmpty()) {
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "application/vnd.android.package-archive"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_apk)))
                            }
                            selectedApks.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = { 
                            selectedApks.forEach { path ->
                                val mediaFile = (viewState as? ViewState.Success)?.files?.find { it.path == path }
                                if (mediaFile != null) {
                                    viewModel.deleteFile(mediaFile.path, mediaFile.contentUri)
                                }
                            }
                            selectedApks.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.apps)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.installed)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.apks)) }
                )
            }
            
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> InstalledAppsView(installedApps, isLoadingApps, selectedApps, isAppSelectionMode)
                    1 -> ApksView(viewState, selectedApks, isApkSelectionMode)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstalledAppsView(installedApps: List<AppInfo>, isLoadingApps: Boolean, selectedApps: MutableList<String>, isSelectionMode: Boolean) {
    val context = LocalContext.current
    if (isLoadingApps) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(installedApps) { app ->
                val isSelected = selectedApps.contains(app.packageName)
                var expanded by remember { mutableStateOf(false) }
                
                ListItem(
                    headlineContent = { Text(app.name) },
                    supportingContent = { Text(app.packageName) },
                    leadingContent = { 
                        Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
                            contentDescription = app.name,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Box {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.app_settings)) },
                                        onClick = {
                                            expanded = false
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${app.packageName}")
                                            }
                                            context.startActivity(intent)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.uninstall)) },
                                        onClick = {
                                            expanded = false
                                            val intent = Intent(Intent.ACTION_DELETE).apply {
                                                data = Uri.parse("package:${app.packageName}")
                                            }
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedApps.remove(app.packageName) else selectedApps.add(app.packageName)
                            } else {
                                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (intent != null) {
                                    context.startActivity(intent)
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(R.string.cannot_open_app), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedApps.add(app.packageName)
                            }
                        }
                    )
                )
                HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ApksView(viewState: ViewState, selectedApks: MutableList<String>, isSelectionMode: Boolean) {
    val context = LocalContext.current
    when (viewState) {
        is ViewState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ViewState.Success -> {
            val apkFiles = viewState.files.filter { it.mimeType == "application/vnd.android.package-archive" || it.path.endsWith(".apk", ignoreCase = true) }
            if (apkFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_apks))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apkFiles) { file ->
                    val isSelected = selectedApks.contains(file.path)
                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = { Text(file.path) },
                        leadingContent = { Icon(Icons.Default.Android, contentDescription = null) },
                        trailingContent = {
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedApks.remove(file.path) else selectedApks.add(file.path)
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))
                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_apk), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedApks.add(file.path)
                                }
                            }
                        )
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            }
        }
        is ViewState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(viewState.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
