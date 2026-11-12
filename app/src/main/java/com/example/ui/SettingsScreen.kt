package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.example.ui.theme.AccentPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, navController: NavHostController) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val desktopModeOverride by viewModel.desktopModeOverride.collectAsStateWithLifecycle()

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
    var showAccentDialog by remember { mutableStateOf(false) }
    var showDesktopDialog by remember { mutableStateOf(false) }

    Scaffold(
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
                // v2.1.0 (WP2): one-point accent colour. The trailing swatch
                // previews the live colour; the dialog below changes it.
                ListItem(
                    headlineContent = { Text(stringResource(R.string.accent_color)) },
                    supportingContent = { Text(getAccentName(accentColor)) },
                    trailingContent = {
                        AccentSwatch(color = Color(accentColor), size = 24.dp)
                    },
                    modifier = Modifier.clickable { showAccentDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.desktop_mode)) },
                    supportingContent = { Text(getDesktopModeString(desktopModeOverride)) },
                    modifier = Modifier.clickable { showDesktopDialog = true }
                )
            }
            item {
                DesktopModeStatusRow(override = desktopModeOverride)
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
                val context = LocalContext.current
                ListItem(
                    headlineContent = { Text(stringResource(R.string.default_apps_title)) },
                    supportingContent = { Text(stringResource(R.string.default_apps_desc)) },
                    modifier = Modifier.clickable {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        runCatching { context.startActivity(intent) }
                    }
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
                // v1.9.0 (MD3 remake): whole-row toggle with Switch role.
                com.example.ui.components.SwitchRow(
                    headline = stringResource(R.string.enable_backup),
                    supporting = stringResource(R.string.auto_backup),
                    checked = backupEnabled,
                    onCheckedChange = { viewModel.setBackupEnabled(it) },
                )
            }
            if (backupEnabled) {
                item {
                    val context = LocalContext.current
                    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                        uri?.let {
                            // Persist SAF permission so backups survive reboot (was missing → Critical).
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    it,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                )
                            }
                            viewModel.setBackupTargetPath(it.toString())
                        }
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
                        // v1.9.0 (MD3 remake): canonical TimePicker with
                        // Start/End tabs replaces the hand-rolled H/M number
                        // fields (which had no validation or error state).
                        val dialogContext = LocalContext.current
                        val is24Hour = remember {
                            android.text.format.DateFormat.is24HourFormat(dialogContext)
                        }
                        var timeTab by remember { mutableIntStateOf(0) }
                        val startState = rememberTimePickerState(
                            initialHour = backupStartTime / 60,
                            initialMinute = backupStartTime % 60,
                            is24Hour = is24Hour,
                        )
                        val endState = rememberTimePickerState(
                            initialHour = backupEndTime / 60,
                            initialMinute = backupEndTime % 60,
                            is24Hour = is24Hour,
                        )
                        AlertDialog(
                            onDismissRequest = { showTimeDialog = false },
                            title = { Text(stringResource(R.string.time_window)) },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                                    TabRow(selectedTabIndex = timeTab) {
                                        Tab(
                                            selected = timeTab == 0,
                                            onClick = { timeTab = 0 },
                                            text = { Text(stringResource(R.string.start_time)) },
                                        )
                                        Tab(
                                            selected = timeTab == 1,
                                            onClick = { timeTab = 1 },
                                            text = { Text(stringResource(R.string.end_time)) },
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (timeTab == 0) TimePicker(state = startState)
                                        else TimePicker(state = endState)
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    viewModel.setBackupStartTime(startState.hour * 60 + startState.minute)
                                    viewModel.setBackupEndTime(endState.hour * 60 + endState.minute)
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
                    com.example.ui.components.SwitchRow(
                        headline = stringResource(R.string.require_charging),
                        checked = backupRequiresCharging,
                        onCheckedChange = { viewModel.setBackupRequiresCharging(it) },
                    )
                }
                item {
                    com.example.ui.components.SwitchRow(
                        headline = stringResource(R.string.require_wifi),
                        checked = backupRequiresWifi,
                        onCheckedChange = { viewModel.setBackupRequiresWifi(it) },
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
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                        modifier = Modifier.fillMaxWidth().imePadding(),
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
                    com.example.ui.components.SwitchRow(
                        headline = stringResource(R.string.delete_previous),
                        checked = backupDeletePrevious,
                        onCheckedChange = { viewModel.setBackupDeletePrevious(it) },
                    )
                }
                item {
                    val context = LocalContext.current
                    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
                    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        uri?.let { pendingRestoreUri = it }
                    }
                    pendingRestoreUri?.let { pending ->
                        AlertDialog(
                            onDismissRequest = { pendingRestoreUri = null },
                            title = { Text(stringResource(R.string.restore_backup)) },
                            text = { Text(stringResource(R.string.restore_desc)) },
                            confirmButton = {
                                Button(onClick = {
                                    pendingRestoreUri = null
                                    val data = Data.Builder().putString("uri", pending.toString()).build()
                                    val req = OneTimeWorkRequestBuilder<RestoreWorker>().setInputData(data).build()
                                    WorkManager.getInstance(context).enqueue(req)
                                    android.widget.Toast.makeText(context, context.getString(R.string.restore_started), android.widget.Toast.LENGTH_SHORT).show()
                                }) { Text(stringResource(R.string.restore_backup)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.cancel)) }
                            },
                        )
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

    if (showAccentDialog) {
        AccentColorDialog(
            current = accentColor,
            onSelect = { viewModel.setAccentColor(it) },
            onReset = { viewModel.setAccentColor(AccentPalette.DEFAULT_AMBER) },
            onDismiss = { showAccentDialog = false },
        )
    }

    if (showDesktopDialog) {
        AlertDialog(
            onDismissRequest = { showDesktopDialog = false },
            title = { Text(stringResource(R.string.select_desktop_mode)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    val modes = listOf(
                        0 to stringResource(R.string.desktop_mode_auto),
                        1 to stringResource(R.string.desktop_mode_force_desktop),
                        2 to stringResource(R.string.desktop_mode_force_touch),
                    )
                    modes.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = desktopModeOverride == value,
                                    role = Role.RadioButton,
                                    onClick = {
                                        viewModel.setDesktopModeOverride(value)
                                        showDesktopDialog = false
                                    },
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = desktopModeOverride == value, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.desktop_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDesktopDialog = false }) { Text(stringResource(R.string.cancel)) }
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


/**
 * v1.8.0 (#7): shows whether the app is currently running in desktop/DeX
 * mode or normal touch mode, with the detected vendor shell. Read-only
 * status — the override dialog above changes behaviour.
 */
@Composable
private fun DesktopModeStatusRow(override: Int) {
    val isDesktop = isDesktopLayout(override)
    val vendor = androidx.compose.runtime.remember { com.example.desktop.DesktopMode.currentVendor() }
    val vendorName = androidx.compose.runtime.remember(vendor) { com.example.desktop.DesktopMode.vendorDisplayName(vendor) }
    val modeLabel = if (isDesktop) stringResource(com.example.R.string.mode_desktop) else stringResource(com.example.R.string.mode_touch)
    ListItem(
        headlineContent = { androidx.compose.material3.Text(stringResource(com.example.R.string.desktop_mode_status, "$modeLabel · $vendorName")) },
        supportingContent = { androidx.compose.material3.Text(stringResource(com.example.R.string.desktop_mode)) },
    )
}

@Composable
fun getDesktopModeString(mode: Int) = when (mode) {
    1 -> stringResource(R.string.desktop_mode_force_desktop)
    2 -> stringResource(R.string.desktop_mode_force_touch)
    else -> stringResource(R.string.desktop_mode_auto)
}

// ---------------------------------------------------------------------------
// v2.1.0 (WP2): accent colour picker
// ---------------------------------------------------------------------------

/** Human-readable name of [argb]; unknown (custom) colours show their hex code. */
@Composable
fun getAccentName(argb: Int): String = when (argb and 0xFFFFFF) {
    AccentPalette.DEFAULT_AMBER and 0xFFFFFF -> stringResource(R.string.accent_amber)
    AccentPalette.BLUE and 0xFFFFFF -> stringResource(R.string.accent_blue)
    AccentPalette.GREEN and 0xFFFFFF -> stringResource(R.string.accent_green)
    AccentPalette.PURPLE and 0xFFFFFF -> stringResource(R.string.accent_purple)
    AccentPalette.PINK and 0xFFFFFF -> stringResource(R.string.accent_pink)
    AccentPalette.ORANGE and 0xFFFFFF -> stringResource(R.string.accent_orange)
    AccentPalette.TEAL and 0xFFFFFF -> stringResource(R.string.accent_teal)
    AccentPalette.RED and 0xFFFFFF -> stringResource(R.string.accent_red)
    else -> AccentPalette.toHexString(argb)
}

/** Flat colour dot with a hairline ring so light swatches stay visible on light surfaces. */
@Composable
private fun AccentSwatch(color: Color, size: Dp, selected: Boolean = false) {
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
    )
}

/**
 * Accent picker dialog, modelled on the `select_theme` dialog above: the eight
 * presets are a selectable grid of colour dots (Role.RadioButton), followed by
 * a custom `#RRGGBB` field and a "reset to default" action. Every choice
 * applies immediately — the dialog stays open so the user can keep comparing.
 */
@Composable
private fun AccentColorDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var hexInput by remember { mutableStateOf(AccentPalette.toHexString(current)) }
    var hexTouched by remember { mutableStateOf(false) }
    val parsed = AccentPalette.parseHexColor(hexInput)
    val showError = hexTouched && parsed == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_accent_color)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                // Presets: 4 columns x 2 rows, each dot labelled for screen readers.
                Column(Modifier.selectableGroup()) {
                    AccentPalette.PRESETS.chunked(4).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            rowColors.forEach { preset ->
                                val selected = (preset and 0xFFFFFF) == (current and 0xFFFFFF)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .widthIn(min = 48.dp)
                                        .heightIn(min = 64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .selectable(
                                            selected = selected,
                                            role = Role.RadioButton,
                                            onClick = {
                                                hexInput = AccentPalette.toHexString(preset)
                                                hexTouched = false
                                                onSelect(preset)
                                            },
                                        )
                                        .padding(vertical = 8.dp),
                                ) {
                                    AccentSwatch(color = Color(preset), size = 32.dp, selected = selected)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = getAccentName(preset),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = stringResource(R.string.custom_color),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        // Hard-cap the length so a pasted blob can never grow
                        // unbounded; parsing itself never throws.
                        hexInput = input.take(9)
                        hexTouched = true
                    },
                    label = { Text(stringResource(R.string.hex_color_label)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.invalid_color_format)) }
                    } else {
                        null
                    },
                    trailingIcon = {
                        val preview = parsed
                        if (preview != null) AccentSwatch(color = Color(preview), size = 24.dp)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onReset()
                        hexInput = AccentPalette.toHexString(AccentPalette.DEFAULT_AMBER)
                        hexTouched = false
                    },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(stringResource(R.string.reset_to_default))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = parsed != null,
                onClick = {
                    parsed?.let(onSelect)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
