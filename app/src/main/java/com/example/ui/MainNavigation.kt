package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.FileViewModel
import com.example.R
import com.example.SettingsViewModel
import com.example.FilesScreen
import com.example.ViewerScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainNavigation(
    fileViewModel: FileViewModel,
    settingsViewModel: SettingsViewModel,
    deepLinkRoute: StateFlow<String?> = MutableStateFlow(null),
    onDeepLinkHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val pendingDeepLink by deepLinkRoute.collectAsState()

    val permissionsToRequest = if (android.os.Build.VERSION.SDK_INT >= 34) {
        listOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        listOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        listOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)

    
    val hasAllFilesAccess = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    // Android's media permissions do not cover PDFs and ordinary files. Full file access is
    // therefore required on Android 11+ before presenting a file-manager interface.
    val isGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        hasAllFilesAccess
    } else {
        permissionsState.permissions.any { it.status.isGranted }
    }
    // Apply an external deep link once storage access is granted and the NavHost exists.
    // Unknown routes are swallowed so a malformed external intent can never crash the app.
    LaunchedEffect(pendingDeepLink, isGranted) {
        val route = pendingDeepLink
        if (isGranted && route != null) {
            runCatching {
                navController.navigate(route) { launchSingleTop = true }
            }
            onDeepLinkHandled()
        }
    }

    if (isGranted) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val desktop = isDesktopLayout()
            if (desktop) {
                DesktopNavigation(navController, settingsViewModel) { onPinFolder, onOpenFolderInNewTab ->
                    MediaNavHost(navController, fileViewModel, settingsViewModel, true, onPinFolder, onOpenFolderInNewTab)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    MediaNavHost(navController, fileViewModel, settingsViewModel, false)
                    com.example.playback.MiniPlayer(onNavigateToAudio = { navController.navigate("audio") { launchSingleTop = true } })
                }
            }
        }
    } else {
        PermissionScreen(
            onRequestPermission = { permissionsState.launchMultiplePermissionRequest() },
            shouldShowRationale = permissionsState.shouldShowRationale,
            onRequestAllFilesAccess = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }
            }
        )
    }
}

@Composable
private fun MediaNavHost(
    navController: NavHostController,
    fileViewModel: FileViewModel,
    settingsViewModel: SettingsViewModel,
    desktop: Boolean,
    onPinFolder: (String) -> Unit = {},
    onOpenFolderInNewTab: (String) -> Unit = {}
) {
    NavHost(navController = navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
        composable("home") { if (desktop) DesktopHomeScreen(navController) else HomeScreen(navController) }
        composable("library") { LibraryScreen(fileViewModel, navController) }
        composable("audio") { AudioScreen(fileViewModel, navController) }
        composable("documents") { DocumentsScreen(fileViewModel, navController) }
        composable("manage") { ManageDashboardScreen(navController) }
        composable(
            route = "file_browser?path={path}",
            arguments = listOf(androidx.navigation.navArgument("path") {
                type = androidx.navigation.NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString("path")?.let { Uri.decode(it) }
            FilesScreen(fileViewModel, navController, path, onPinFolder, onOpenFolderInNewTab)
        }
        composable("clean") { CleanScreen(fileViewModel, navController) }
        composable("settings") { SettingsScreen(settingsViewModel, navController) }
        composable("exclude_folders") { ExcludeFoldersScreen(fileViewModel, navController) }
        composable("apps") { AppManagerScreen(fileViewModel, navController) }
        composable("album/{albumName}") { backStackEntry ->
            backStackEntry.arguments?.getString("albumName")?.let(Uri::decode)?.let { AlbumScreen(it, fileViewModel, navController) }
        }
        composable("playlist/{playlistName}") { backStackEntry ->
            backStackEntry.arguments?.getString("playlistName")?.let(Uri::decode)?.let { PlaylistScreen(it, fileViewModel, navController) }
        }
        composable("category/{categoryName}") { backStackEntry ->
            backStackEntry.arguments?.getString("categoryName")?.let(Uri::decode)?.let { CategoryScreen(it, fileViewModel, navController) }
        }
        composable("viewer/{path}") { backStackEntry ->
            val path = backStackEntry.arguments?.getString("path")?.let(Uri::decode)
            ViewerScreen(path, fileViewModel, navController)
        }
        composable("imageEditor/{uriString}") { backStackEntry ->
            backStackEntry.arguments?.getString("uriString")?.let(Uri::decode)?.let { ImageEditorScreen(it, navController) }
        }
        composable("videoEditor/{uriString}") { backStackEntry ->
            backStackEntry.arguments?.getString("uriString")?.let(Uri::decode)?.let { VideoEditorScreen(it, navController) }
        }
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    Spacer(modifier = Modifier.width(16.dp))
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
        ) {
            item {
                HomeCard(
                    title = stringResource(R.string.library),
                    description = stringResource(R.string.photos),
                    icon = Icons.Default.PhotoLibrary,
                    onClick = { navController.navigate("library") { launchSingleTop = true } }
                )
            }
            item {
                HomeCard(
                    title = stringResource(R.string.audio),
                    description = stringResource(R.string.playlists),
                    icon = Icons.Default.LibraryMusic,
                    onClick = { navController.navigate("audio") { launchSingleTop = true } }
                )
            }
            item {
                HomeCard(
                    title = stringResource(R.string.manage),
                    description = stringResource(R.string.files_and_storage),
                    icon = Icons.Default.Folder,
                    onClick = { navController.navigate("manage") { launchSingleTop = true } }
                )
            }
            item {
                HomeCard(
                    title = stringResource(R.string.documents),
                    description = stringResource(R.string.files),
                    icon = Icons.Default.Description,
                    onClick = { navController.navigate("documents") { launchSingleTop = true } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 156.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PermissionScreen(
    onRequestPermission: () -> Unit, 
    shouldShowRationale: Boolean,
    onRequestAllFilesAccess: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.storage_permission), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.permission_desc), style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onRequestPermission() }
        ) {
            Text(stringResource(R.string.grant_permission))
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRequestAllFilesAccess) {
                Text(stringResource(R.string.grant_all_files_access))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        ) {
            Text(stringResource(R.string.app_settings))
        }
    }
}
