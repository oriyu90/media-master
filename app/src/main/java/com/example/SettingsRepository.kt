package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val SERVER_URL = stringPreferencesKey("server_url")
        val MEDIA_FOLDERS = stringSetPreferencesKey("media_folders")
        val PINNED_FOLDERS = stringSetPreferencesKey("pinned_folders")
        
        val BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val BACKUP_START_TIME = intPreferencesKey("backup_start_time")
        val BACKUP_END_TIME = intPreferencesKey("backup_end_time")
        val BACKUP_REQUIRES_CHARGING = booleanPreferencesKey("backup_requires_charging")
        val BACKUP_REQUIRES_WIFI = booleanPreferencesKey("backup_requires_wifi")
        val BACKUP_WIFI_SSID = stringPreferencesKey("backup_wifi_ssid")
        val BACKUP_TARGET_PATH = stringPreferencesKey("backup_target_path")
        val BACKUP_DELETE_PREVIOUS = booleanPreferencesKey("backup_delete_previous")
    }

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: 0
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE] ?: "system"
    }
    
    val serverUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: ""
    }
    
    val mediaFoldersFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[MEDIA_FOLDERS] ?: emptySet()
    }
    val pinnedFoldersFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PINNED_FOLDERS] ?: emptySet()
    }
    val backupEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[BACKUP_ENABLED] ?: false }
    val backupStartTimeFlow: Flow<Int> = context.dataStore.data.map { it[BACKUP_START_TIME] ?: 0 }
    val backupEndTimeFlow: Flow<Int> = context.dataStore.data.map { it[BACKUP_END_TIME] ?: 360 } // 6 AM
    val backupRequiresChargingFlow: Flow<Boolean> = context.dataStore.data.map { it[BACKUP_REQUIRES_CHARGING] ?: true }
    val backupRequiresWifiFlow: Flow<Boolean> = context.dataStore.data.map { it[BACKUP_REQUIRES_WIFI] ?: true }
    val backupWifiSsidFlow: Flow<String> = context.dataStore.data.map { it[BACKUP_WIFI_SSID] ?: "" }
    val backupTargetPathFlow: Flow<String> = context.dataStore.data.map { it[BACKUP_TARGET_PATH] ?: "" }
    val backupDeletePreviousFlow: Flow<Boolean> = context.dataStore.data.map { it[BACKUP_DELETE_PREVIOUS] ?: false }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE] = language
        }
    }
    
    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }
    
    suspend fun setMediaFolders(folders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[MEDIA_FOLDERS] = folders
        }
    }
    suspend fun setPinnedFolders(folders: Set<String>) {
        context.dataStore.edit { preferences -> preferences[PINNED_FOLDERS] = folders }
    }

    /** Atomically add/remove a single entry in a string-set preference (read-modify-write inside [edit]). */
    suspend fun updateMediaFolders(add: String? = null, remove: String? = null) {
        context.dataStore.edit { preferences ->
            val current = preferences[MEDIA_FOLDERS] ?: emptySet()
            preferences[MEDIA_FOLDERS] = current.let { s -> (if (add != null) s + add else s).let { if (remove != null) it - remove else it } }
        }
    }

    suspend fun updatePinnedFolders(add: String? = null, remove: String? = null) {
        context.dataStore.edit { preferences ->
            val current = preferences[PINNED_FOLDERS] ?: emptySet()
            preferences[PINNED_FOLDERS] = current.let { s -> (if (add != null) s + add else s).let { if (remove != null) it - remove else it } }
        }
    }
    suspend fun setBackupEnabled(value: Boolean) { context.dataStore.edit { it[BACKUP_ENABLED] = value } }
    suspend fun setBackupStartTime(value: Int) { context.dataStore.edit { it[BACKUP_START_TIME] = value } }
    suspend fun setBackupEndTime(value: Int) { context.dataStore.edit { it[BACKUP_END_TIME] = value } }
    suspend fun setBackupRequiresCharging(value: Boolean) { context.dataStore.edit { it[BACKUP_REQUIRES_CHARGING] = value } }
    suspend fun setBackupRequiresWifi(value: Boolean) { context.dataStore.edit { it[BACKUP_REQUIRES_WIFI] = value } }
    suspend fun setBackupWifiSsid(value: String) { context.dataStore.edit { it[BACKUP_WIFI_SSID] = value } }
    suspend fun setBackupTargetPath(value: String) { context.dataStore.edit { it[BACKUP_TARGET_PATH] = value } }
    suspend fun setBackupDeletePrevious(value: Boolean) { context.dataStore.edit { it[BACKUP_DELETE_PREVIOUS] = value } }
}
