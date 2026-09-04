package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val themeMode = repository.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val language = repository.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "system"
    )

    val serverUrl = repository.serverUrlFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val mediaFolders = repository.mediaFoldersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )
    val pinnedFolders = repository.pinnedFoldersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )
    val backupEnabled = repository.backupEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val backupStartTime = repository.backupStartTimeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val backupEndTime = repository.backupEndTimeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 360)
    val backupRequiresCharging = repository.backupRequiresChargingFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val backupRequiresWifi = repository.backupRequiresWifiFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val backupWifiSsid = repository.backupWifiSsidFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val backupTargetPath = repository.backupTargetPathFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val backupDeletePrevious = repository.backupDeletePreviousFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { repository.setLanguage(lang) }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch { repository.setServerUrl(url) }
    }

    fun addMediaFolder(folderPath: String) {
        viewModelScope.launch { repository.updateMediaFolders(add = folderPath) }
    }

    fun removeMediaFolder(folderPath: String) {
        viewModelScope.launch { repository.updateMediaFolders(remove = folderPath) }
    }

    fun addPinnedFolder(folderPath: String) {
        viewModelScope.launch { repository.updatePinnedFolders(add = folderPath) }
    }

    fun removePinnedFolder(folderPath: String) {
        viewModelScope.launch { repository.updatePinnedFolders(remove = folderPath) }
    }
    fun setBackupEnabled(value: Boolean) { viewModelScope.launch { repository.setBackupEnabled(value) } }
    fun setBackupStartTime(value: Int) { viewModelScope.launch { repository.setBackupStartTime(value) } }
    fun setBackupEndTime(value: Int) { viewModelScope.launch { repository.setBackupEndTime(value) } }
    fun setBackupRequiresCharging(value: Boolean) { viewModelScope.launch { repository.setBackupRequiresCharging(value) } }
    fun setBackupRequiresWifi(value: Boolean) { viewModelScope.launch { repository.setBackupRequiresWifi(value) } }
    fun setBackupWifiSsid(value: String) { viewModelScope.launch { repository.setBackupWifiSsid(value) } }
    fun setBackupTargetPath(value: String) { viewModelScope.launch { repository.setBackupTargetPath(value) } }
    fun setBackupDeletePrevious(value: Boolean) { viewModelScope.launch { repository.setBackupDeletePrevious(value) } }
}
