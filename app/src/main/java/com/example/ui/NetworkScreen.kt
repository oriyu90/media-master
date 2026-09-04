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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
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

    var editing by remember { mutableStateOf<NetworkLocation?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val browseState = browse
    val title = when (browseState) {
        is BrowseUiState.Ready -> browseState.location.name
        else -> stringResource(R.string.network_locations)
    }

    BackHandler(enabled = browseState !is BrowseUiState.Idle) { viewModel.up() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
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
            )
        },
        floatingActionButton = {
            if (browseState is BrowseUiState.Idle) {
                FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_location))
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = browseState) {
                is BrowseUiState.Idle -> LocationList(
                    locations = locations,
                    onOpen = viewModel::open,
                    onEdit = { editing = it; showEditor = true },
                    onDelete = viewModel::deleteLocation,
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
            existing = editing,
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
    onOpen: (NetworkLocation) -> Unit,
    onEdit: (NetworkLocation) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (locations.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            EmptyState(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.network_storage),
                description = stringResource(R.string.no_network_locations),
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(locations, key = { it.id }) { loc ->
            ListItem(
                headlineContent = { Text(loc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${loc.protocol.name.lowercase()}://${loc.host}/${loc.share}".trimEnd('/'))
                },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                trailingContent = {
                    Row {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationEditorDialog(
    existing: NetworkLocation?,
    onDismiss: () -> Unit,
    onSave: (NetworkLocation, String) -> Unit,
    newId: () -> String,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var protocol by remember { mutableStateOf(existing?.protocol ?: NetworkProtocol.SMB) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember { mutableStateOf(existing?.port?.takeIf { it != 0 }?.toString().orEmpty()) }
    var share by remember { mutableStateOf(existing?.share.orEmpty()) }
    var basePath by remember { mutableStateOf(existing?.basePath.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_location else R.string.edit_location)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
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
                            id = existing?.id ?: newId(),
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
