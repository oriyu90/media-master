package com.example.ui

import android.content.ClipDescription
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.R
import com.example.SettingsViewModel
import java.io.File

/** A large tablet or foldable is not DeX: use Android's desk-mode signal only. */
@Composable
fun isDesktopLayout(): Boolean {
    val configuration = LocalConfiguration.current
    val uiType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return uiType == Configuration.UI_MODE_TYPE_DESK
}

private data class DesktopTab(val id: Long, val title: String, val route: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopNavigation(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    navHost: @Composable (onPinFolder: (String) -> Unit, onOpenFolderInNewTab: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val customPins by settingsViewModel.pinnedFolders.collectAsStateWithLifecycle()
    val serverUrl by settingsViewModel.serverUrl.collectAsStateWithLifecycle()
    val root = Environment.getExternalStorageDirectory().absolutePath
    val standardPins = remember(root) {
        listOf("$root/Pictures", "$root/Download", "$root/apk")
    }
    val tabs = remember { mutableStateListOf(DesktopTab(0, context.getString(R.string.home), "home")) }
    var selectedTabId by remember { mutableLongStateOf(0L) }
    var nextTabId by remember { mutableLongStateOf(1L) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: "home"

    LaunchedEffect(currentRoute) {
        val index = tabs.indexOfFirst { it.id == selectedTabId }
        if (index >= 0) {
            val title = when (currentRoute) {
                "home" -> context.getString(R.string.home)
                "library" -> context.getString(R.string.library)
                "audio" -> context.getString(R.string.audio)
                "documents" -> context.getString(R.string.documents)
                "manage" -> context.getString(R.string.manage)
                "apps" -> context.getString(R.string.apps)
                "settings" -> context.getString(R.string.settings)
                "clean" -> context.getString(R.string.clean)
                else -> null
            }
            if (title != null) tabs[index] = tabs[index].copy(title = title, route = currentRoute)
        }
    }

    fun navigateInCurrentTab(route: String, title: String) {
        val index = tabs.indexOfFirst { it.id == selectedTabId }
        if (index >= 0) tabs[index] = tabs[index].copy(title = title, route = route)
        navController.navigate(route) { launchSingleTop = true }
    }

    fun openInNewTab(path: String) {
        val title = File(path).name.ifBlank { context.getString(R.string.files) }
        val route = "file_browser?path=${Uri.encode(path)}"
        val tab = DesktopTab(nextTabId++, title, route)
        tabs.add(tab)
        selectedTabId = tab.id
        navController.navigate(route) { launchSingleTop = true }
    }

    val dropTarget = remember(settingsViewModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clipData = event.toAndroidDragEvent().clipData ?: return false
                if (clipData.itemCount == 0) return false
                val path = clipData.getItemAt(0).text?.toString() ?: return false
                if (!File(path).isDirectory) return false
                settingsViewModel.addPinnedFolder(path)
                return true
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        DesktopSidebar(
            modifier = Modifier
                .widthIn(min = 220.dp, max = 280.dp)
                .fillMaxHeight()
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        ClipDescription.MIMETYPE_TEXT_PLAIN in event.mimeTypes()
                    },
                    target = dropTarget
                ),
            currentRoute = currentRoute,
            standardPins = standardPins,
            customPins = customPins.toList().sorted(),
            serverUrl = serverUrl,
            onNavigate = ::navigateInCurrentTab,
            onNewTab = ::openInNewTab,
            onRemovePin = settingsViewModel::removePinnedFolder
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            DesktopTabStrip(
                tabs = tabs,
                selectedTabId = selectedTabId,
                onSelect = { tab ->
                    selectedTabId = tab.id
                    navController.navigate(tab.route) { launchSingleTop = true }
                },
                onClose = { tab ->
                    if (tabs.size == 1) return@DesktopTabStrip
                    val wasSelected = selectedTabId == tab.id
                    tabs.remove(tab)
                    if (wasSelected) {
                        val next = tabs.last()
                        selectedTabId = next.id
                        navController.navigate(next.route) { launchSingleTop = true }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                navHost(settingsViewModel::addPinnedFolder, ::openInNewTab)
            }
        }
    }
}

@Composable
private fun DesktopTabStrip(
    tabs: List<DesktopTab>,
    selectedTabId: Long,
    onSelect: (DesktopTab) -> Unit,
    onClose: (DesktopTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            Surface(
                modifier = Modifier.widthIn(min = 132.dp, max = 220.dp).fillMaxHeight(),
                color = if (tab.id == selectedTabId) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = { onSelect(tab) }
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (tabs.size > 1) {
                        IconButton(onClick = { onClose(tab) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_tab), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopSidebar(
    modifier: Modifier,
    currentRoute: String,
    standardPins: List<String>,
    customPins: List<String>,
    serverUrl: String,
    onNavigate: (String, String) -> Unit,
    onNewTab: (String) -> Unit,
    onRemovePin: (String) -> Unit
) {
    val context = LocalContext.current
    val externalRoots = remember(context) {
        ContextCompat.getExternalFilesDirs(context, null).mapNotNull { dir ->
            dir?.absolutePath?.substringBefore("/Android/data/")
        }.distinct()
    }
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item { SidebarSectionTitle(stringResource(R.string.favorites)) }
        item { SidebarDestination(stringResource(R.string.home), Icons.Default.Home, currentRoute == "home") { onNavigate("home", context.getString(R.string.home)) } }
        item { SidebarDestination(stringResource(R.string.library), Icons.Default.PhotoLibrary, currentRoute == "library") { onNavigate("library", context.getString(R.string.library)) } }
        item { SidebarDestination(stringResource(R.string.audio), Icons.Default.LibraryMusic, currentRoute == "audio") { onNavigate("audio", context.getString(R.string.audio)) } }
        item { SidebarDestination(stringResource(R.string.documents), Icons.Default.Description, currentRoute == "documents") { onNavigate("documents", context.getString(R.string.documents)) } }

        item { SidebarSectionTitle(stringResource(R.string.pinned_folders)) }
        items(standardPins.size) { index ->
            val path = standardPins[index]
            PinnedFolderItem(path, removable = false, onNavigate, onNewTab, onRemovePin)
        }
        items(customPins.size) { index ->
            PinnedFolderItem(customPins[index], removable = true, onNavigate, onNewTab, onRemovePin)
        }

        item { SidebarSectionTitle(stringResource(R.string.devices)) }
        items(externalRoots.size) { index ->
            val path = externalRoots[index]
            val internal = path.contains("emulated")
            val title = stringResource(if (internal) R.string.internal_storage else R.string.external_storage)
            SidebarDestination(title, if (internal) Icons.Default.PhoneAndroid else Icons.Default.SdStorage, false) {
                onNavigate("file_browser?path=${Uri.encode(path)}", title)
            }
        }
        item {
            SidebarDestination(stringResource(R.string.network_storage), Icons.Default.Storage, false) {
                if (serverUrl.isBlank()) onNavigate("settings", context.getString(R.string.settings))
                else onNavigate("file_browser?path=${Uri.encode(serverUrl)}", context.getString(R.string.network_storage))
            }
        }
        item { SidebarSectionTitle(stringResource(R.string.tools)) }
        item { SidebarDestination(stringResource(R.string.manage), Icons.Default.Folder, currentRoute == "manage") { onNavigate("manage", context.getString(R.string.manage)) } }
        item { SidebarDestination(stringResource(R.string.apps), Icons.Default.Apps, currentRoute == "apps") { onNavigate("apps", context.getString(R.string.apps)) } }
        item { SidebarDestination(stringResource(R.string.settings), Icons.Default.Settings, currentRoute == "settings") { onNavigate("settings", context.getString(R.string.settings)) } }
    }
}

@Composable
private fun SidebarSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SidebarDestination(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedFolderItem(
    path: String,
    removable: Boolean,
    onNavigate: (String, String) -> Unit,
    onNewTab: (String) -> Unit,
    onRemovePin: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val title = File(path).name.ifBlank { path }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .pointerInput(path) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                menuExpanded = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .combinedClickable(
                    onClick = { onNavigate("file_browser?path=${Uri.encode(path)}", title) },
                    onLongClick = { menuExpanded = true }
                )
                .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            // Keyboard/touch-accessible alternative to right-click / long-press.
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.options),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_in_new_tab)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                onClick = { menuExpanded = false; onNewTab(path) }
            )
            if (removable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_pin)) },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) },
                    onClick = { menuExpanded = false; onRemovePin(path) }
                )
            }
        }
    }
}

@Composable
fun DesktopHomeScreen(navController: NavHostController) {
    val actions = listOf(
        Triple(R.string.library, Icons.Default.PhotoLibrary, "library"),
        Triple(R.string.audio, Icons.Default.LibraryMusic, "audio"),
        Triple(R.string.documents, Icons.Default.Description, "documents"),
        Triple(R.string.manage, Icons.Default.Folder, "manage"),
        Triple(R.string.apps, Icons.Default.Apps, "apps"),
        Triple(R.string.settings, Icons.Default.Settings, "settings"),
    )
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text(stringResource(R.string.home), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(actions) { (titleRes, icon, route) ->
                ElevatedCard(
                    onClick = { navController.navigate(route) { launchSingleTop = true } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
