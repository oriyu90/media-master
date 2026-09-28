package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.R
import com.example.network.BrowseUiState
import com.example.network.DiscoveredDevice
import com.example.network.NetworkLocation
import com.example.network.NetworkProtocol
import com.example.network.NetworkViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(navController: NavHostController, viewModel: NetworkViewModel = viewModel()) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    // v1.8.0 (#6): LAN discovery state; scan stops when leaving the screen.
    val discovered by viewModel.discovered.collectAsStateWithLifecycle()
    val discovering by viewModel.discovering.collectAsStateWithLifecycle()
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.stopDiscovery() }
    }

    var editing by remember { mutableStateOf<NetworkLocation?>(null) }
    var editorIsNew by remember { mutableStateOf(true) }
    var showEditor by remember { mutableStateOf(false) }

    val browseState = browse
    val title = when (browseState) {
        is BrowseUiState.Ready -> browseState.location.name
        else -> stringResource(R.string.network_locations)
    }

    BackHandler(enabled = browseState !is BrowseUiState.Idle) { viewModel.up() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (browseState is BrowseUiState.Idle) navController.popBackStack() else viewModel.up()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // v1.9.0 (MD3 remake): top-app-bar actions are IconButtons.
                    if (browseState is BrowseUiState.Idle) {
                        if (discovering) {
                            IconButton(onClick = viewModel::stopDiscovery) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.stop_scan))
                            }
                        } else {
                            IconButton(onClick = viewModel::startDiscovery) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.scan_lan))
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (browseState is BrowseUiState.Idle) {
                FloatingActionButton(onClick = { editing = null; editorIsNew = true; showEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_location))
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = browseState) {
                is BrowseUiState.Idle -> LocationList(
                    locations = locations,
                    discovered = discovered,
                    discovering = discovering,
                    onOpen = viewModel::open,
                    onEdit = { editing = it; editorIsNew = false; showEditor = true },
                    onDelete = viewModel::deleteLocation,
                    onAddDiscovered = { device ->
                        editing = viewModel.draftFromDiscovered(device)
                        editorIsNew = true
                        showEditor = true
                    },
                )
                is BrowseUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is BrowseUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = s.message,
                        retryLabel = stringResource(R.string.back),
                        onRetry = viewModel::closeBrowser,
                    )
                }
                is BrowseUiState.Ready -> {
                    if (s.entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            EmptyState(icon = Icons.Default.Folder, title = s.location.name, description = stringResource(R.string.no_files_found))
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.entries, key = { it.relativePath }) { entry ->
                                ListItem(
                                    headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = {
                                        Icon(
                                            if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                        )
                                    },
                                    modifier = Modifier.clickable { viewModel.openEntry(entry) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        LocationEditorDialog(
            existing = if (editorIsNew) null else editing,
            draft = if (editorIsNew) editing else null,
            onDismiss = { showEditor = false },
            onSave = { location, password ->
                viewModel.saveLocation(location, password)
                showEditor = false
            },
            newId = viewModel::newLocationId,
        )
    }
}

@Composable
private fun LocationList(
    locations: List<NetworkLocation>,
    discovered: List<DiscoveredDevice>,
    discovering: Boolean,
    onOpen: (NetworkLocation) -> Unit,
    onEdit: (NetworkLocation) -> Unit,
    onDelete: (String) -> Unit,
    onAddDiscovered: (DiscoveredDevice) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        // v1.8.0 (#6): discovered section first — tap Add to prefill the
        // manual editor (never auto-saves, never auto-connects with passwords).
        item {
            DiscoveredSection(
                discovered = discovered,
                discovering = discovering,
                onAdd = onAddDiscovered,
            )
        }
        if (locations.isEmpty() && discovered.isEmpty() && !discovering) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Storage,
                        title = stringResource(R.string.network_storage),
                        description = stringResource(R.string.no_network_locations),
                    )
                }
            }
            return@LazyColumn
        }
        items(locations, key = { it.id }) { loc ->
            ListItem(
                headlineContent = { Text(loc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${loc.protocol.name.lowercase()}://${loc.host}/${loc.share}".trimEnd('/'))
                },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                trailingContent = {
                    // v1.9.0 (MD3 remake): gap between the two 48dp targets.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onEdit(loc) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_location))
                        }
                        IconButton(onClick = { onDelete(loc.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                modifier = Modifier.clickable { onOpen(loc) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun DiscoveredSection(
    discovered: List<DiscoveredDevice>,
    discovering: Boolean,
    onAdd: (DiscoveredDevice) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // v1.9.0 (MD3 remake): section header uses the label role, not title
        // in primary.
        Text(
            text = stringResource(R.string.discovered_devices),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        if (discovering && discovered.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scanning_lan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (discovered.isEmpty()) {
            Text(
                text = stringResource(R.string.no_servers_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            discovered.forEach { device ->
                ListItem(
                    headlineContent = { Text(device.serviceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val proto = device.hint?.name?.lowercase() ?: "lan"
                        Text(proto + "://" + device.host + ":" + device.port.toString())
                    },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                    trailingContent = {
                        TextButton(onClick = { onAdd(device) }) {
                            Text(stringResource(R.string.add_discovered))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationEditorDialog(
    existing: NetworkLocation?,
    draft: NetworkLocation? = null,
    onDismiss: () -> Unit,
    onSave: (NetworkLocation, String) -> Unit,
    newId: () -> String,
) {
    // v1.8.0 (#6): draft is a prefilled unsaved device (discovered); existing
    // is a saved location being edited. Draft values prefill, title stays Add.
    val initial = draft ?: existing
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var protocol by remember(initial) { mutableStateOf(initial?.protocol ?: NetworkProtocol.SMB) }
    var host by remember(initial) { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember(initial) { mutableStateOf(initial?.port?.takeIf { it != 0 }?.toString().orEmpty()) }
    var share by remember(initial) { mutableStateOf(initial?.share.orEmpty()) }
    var basePath by remember(initial) { mutableStateOf(initial?.basePath.orEmpty()) }
    var username by remember(initial) { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember(initial) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_location else R.string.edit_location)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleLineField(name, { name = it }, R.string.location_name)
                Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = protocol == NetworkProtocol.SMB,
                        onClick = { protocol = NetworkProtocol.SMB },
                        label = { Text(stringResource(R.string.smb)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = protocol == NetworkProtocol.WEBDAV,
                        onClick = { protocol = NetworkProtocol.WEBDAV },
                        label = { Text(stringResource(R.string.webdav)) },
                    )
                }
                SingleLineField(host, { host = it }, R.string.host)
                SingleLineField(port, { port = it.filter(Char::isDigit).take(5) }, R.string.port, KeyboardType.Number)
                SingleLineField(share, { share = it }, R.string.share_or_path)
                SingleLineField(basePath, { basePath = it }, R.string.base_path)
                SingleLineField(username, { username = it }, R.string.username)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    supportingText = if (existing != null) {
                        { Text(stringResource(R.string.password_keep_hint)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && host.isNotBlank(),
                onClick = {
                    onSave(
                        NetworkLocation(
                            id = existing?.id ?: draft?.id ?: newId(),
                            name = name.trim(),
                            protocol = protocol,
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 0,
                            share = share.trim().trim('/'),
                            basePath = basePath.trim().trim('/'),
                            username = username.trim(),
                        ),
                        password,
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SingleLineField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
