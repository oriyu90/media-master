package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.example.backup.RestoreWorker
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, navController: NavHostController) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    val backupEnabled by viewModel.backupEnabled.collectAsStateWithLifecycle()
    val backupStartTime by viewModel.backupStartTime.collectAsStateWithLifecycle()
    val backupEndTime by viewModel.backupEndTime.collectAsStateWithLifecycle()
    val backupRequiresCharging by viewModel.backupRequiresCharging.collectAsStateWithLifecycle()
    val backupRequiresWifi by viewModel.backupRequiresWifi.collectAsStateWithLifecycle()
    val backupWifiSsid by viewModel.backupWifiSsid.collectAsStateWithLifecycle()
    val backupTargetPath by viewModel.backupTargetPath.collectAsStateWithLifecycle()
    val backupDeletePrevious by viewModel.backupDeletePrevious.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_mode)) },
                    supportingContent = { Text(getThemeString(themeMode)) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.language)) },
                    supportingContent = { Text(getLangString(language)) },
                    modifier = Modifier.clickable { showLangDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.server_connection)) },
                    supportingContent = { Text(stringResource(R.string.network_locations)) },
                    modifier = Modifier.clickable { navController.navigate("network") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.media_folders)) },
                    supportingContent = { Text(stringResource(R.string.configure_folders)) },
                    modifier = Modifier.clickable { navController.navigate("exclude_folders") }
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.backup_settings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.enable_backup)) },
                    supportingContent = { Text(stringResource(R.string.auto_backup)) },
                    trailingContent = {
                        Switch(checked = backupEnabled, onCheckedChange = { viewModel.setBackupEnabled(it) })
                    }
                )
            }
            if (backupEnabled) {
                item {
                    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                        uri?.let { viewModel.setBackupTargetPath(it.toString()) }
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.target_path)) },
                        supportingContent = { Text(if (backupTargetPath.isEmpty()) stringResource(R.string.not_set) else Uri.parse(backupTargetPath).lastPathSegment ?: backupTargetPath) },
                        modifier = Modifier.clickable { dirPicker.launch(null) }
                    )
                }
                item {
                    var showTimeDialog by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_time_window)) },
                        supportingContent = { 
                            val startStr = "${backupStartTime / 60}:${(backupStartTime % 60).toString().padStart(2, '0')}"
                            val endStr = "${backupEndTime / 60}:${(backupEndTime % 60).toString().padStart(2, '0')}"
                            Text(stringResource(R.string.backup_from_to, startStr, endStr))
                        },
                        modifier = Modifier.clickable { showTimeDialog = true }
                    )
                    if (showTimeDialog) {
                        var startH by remember { mutableStateOf((backupStartTime / 60).toString()) }
                        var startM by remember { mutableStateOf((backupStartTime % 60).toString()) }
                        var endH by remember { mutableStateOf((backupEndTime / 60).toString()) }
                        var endM by remember { mutableStateOf((backupEndTime % 60).toString()) }
                        AlertDialog(
                            onDismissRequest = { showTimeDialog = false },
                            title = { Text(stringResource(R.string.time_window)) },
                            text = {
                                Column {
                                    Text(stringResource(R.string.start_time))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = startH, onValueChange = { startH = it.filter(Char::isDigit).take(2) },
                                            label = { Text("h") }, singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(":", modifier = Modifier.padding(horizontal = 8.dp))
                                        OutlinedTextField(
                                            value = startM, onValueChange = { startM = it.filter(Char::isDigit).take(2) },
                                            label = { Text("m") }, singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(stringResource(R.string.end_time))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = endH, onValueChange = { endH = it.filter(Char::isDigit).take(2) },
                                            label = { Text("h") }, singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(":", modifier = Modifier.padding(horizontal = 8.dp))
                                        OutlinedTextField(
                                            value = endM, onValueChange = { endM = it.filter(Char::isDigit).take(2) },
                                            label = { Text("m") }, singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    val sh = (startH.toIntOrNull() ?: 0).coerceIn(0, 23)
                                    val sm = (startM.toIntOrNull() ?: 0).coerceIn(0, 59)
                                    val eh = (endH.toIntOrNull() ?: 0).coerceIn(0, 23)
                                    val em = (endM.toIntOrNull() ?: 0).coerceIn(0, 59)
                                    viewModel.setBackupStartTime(sh * 60 + sm)
                                    viewModel.setBackupEndTime(eh * 60 + em)
                                    showTimeDialog = false
                                }) { Text(stringResource(R.string.save)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTimeDialog = false }) { Text(stringResource(R.string.cancel)) }
                            }
                        )
                    }
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.require_charging)) },
                        trailingContent = {
                            Switch(checked = backupRequiresCharging, onCheckedChange = { viewModel.setBackupRequiresCharging(it) })
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.require_wifi)) },
                        trailingContent = {
                            Switch(checked = backupRequiresWifi, onCheckedChange = { viewModel.setBackupRequiresWifi(it) })
                        }
                    )
                }
                if (backupRequiresWifi) {
                    item {
                        var showSsidDialog by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.specific_wifi)) },
                            supportingContent = { Text(backupWifiSsid.ifEmpty { stringResource(R.string.any_wifi) }) },
                            modifier = Modifier.clickable { showSsidDialog = true }
                        )
                        if (showSsidDialog) {
                            var ssidInput by remember { mutableStateOf(backupWifiSsid) }
                            AlertDialog(
                                onDismissRequest = { showSsidDialog = false },
                                title = { Text(stringResource(R.string.specific_wifi)) },
                                text = {
                                    OutlinedTextField(
                                        value = ssidInput,
                                        onValueChange = { ssidInput = it },
                                        label = { Text(stringResource(R.string.ssid)) },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        viewModel.setBackupWifiSsid(ssidInput)
                                        showSsidDialog = false
                                    }) { Text(stringResource(R.string.save)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSsidDialog = false }) { Text(stringResource(R.string.cancel)) }
                                }
                            )
                        }
                    }
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.delete_previous)) },
                        trailingContent = {
                            Switch(checked = backupDeletePrevious, onCheckedChange = { viewModel.setBackupDeletePrevious(it) })
                        }
                    )
                }
                item {
                    val context = LocalContext.current
                    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        uri?.let { 
                            val data = Data.Builder().putString("uri", it.toString()).build()
                            val req = OneTimeWorkRequestBuilder<RestoreWorker>().setInputData(data).build()
                            WorkManager.getInstance(context).enqueue(req)
                            android.widget.Toast.makeText(context, context.getString(R.string.restore_started), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.restore_backup)) },
                        supportingContent = { Text(stringResource(R.string.restore_desc)) },
                        modifier = Modifier.clickable { filePicker.launch(arrayOf("application/zip")) }
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.select_theme)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        0 to stringResource(R.string.system_default),
                        1 to stringResource(R.string.light),
                        2 to stringResource(R.string.dark)
                    ).forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = themeMode == value,
                                    role = Role.RadioButton,
                                    onClick = {
                                        viewModel.setThemeMode(value)
                                        showThemeDialog = false
                                    },
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = themeMode == value, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(stringResource(R.string.select_language)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    val langs = listOf(
                        "system" to stringResource(R.string.system_default),
                        "en" to stringResource(R.string.english),
                        "ja" to stringResource(R.string.japanese),
                        "zh" to stringResource(R.string.chinese),
                        "ar" to stringResource(R.string.arabic),
                        "nl" to stringResource(R.string.dutch)
                    )
                    langs.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = language == value,
                                    role = Role.RadioButton,
                                    onClick = {
                                        viewModel.setLanguage(value)
                                        showLangDialog = false
                                    },
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = language == value, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun getThemeString(mode: Int) = when(mode) {
    1 -> stringResource(R.string.light)
    2 -> stringResource(R.string.dark)
    else -> stringResource(R.string.system_default)
}

@Composable
fun getLangString(lang: String) = when(lang) {
    "en" -> stringResource(R.string.english)
    "ja" -> stringResource(R.string.japanese)
    "zh" -> stringResource(R.string.chinese)
    "ar" -> stringResource(R.string.arabic)
    "nl" -> stringResource(R.string.dutch)
    else -> stringResource(R.string.system_default)
}
