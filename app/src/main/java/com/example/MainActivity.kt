package com.example

import android.os.Bundle
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.Data
import com.example.backup.BackupWorker
import java.util.concurrent.TimeUnit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.MainNavigation
import com.example.ui.theme.MyApplicationTheme

import com.example.playback.PlaybackManager

class MainActivity : AppCompatActivity() {
    companion object {
        var shouldEnterPiP = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldEnterPiP && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
        }
    }
    private val fileViewModel: FileViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackManager.initialize(this)
        val imageLoader = coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
            }
            .build()
        coil.Coil.setImageLoader(imageLoader)
        enableEdgeToEdge()
        lifecycleScope.launch {
            settingsViewModel.backupEnabled.collectLatest { enabled ->
                val wm = WorkManager.getInstance(applicationContext)
                if (enabled) {
                    val constraints = Constraints.Builder()
                        .setRequiresCharging(settingsViewModel.backupRequiresCharging.value)
                        .setRequiredNetworkType(if (settingsViewModel.backupRequiresWifi.value) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
                        .build()
                        
                    val data = Data.Builder()
                        .putString("target_path", settingsViewModel.backupTargetPath.value)
                        .putBoolean("delete_previous", settingsViewModel.backupDeletePrevious.value)
                        .putInt("start_time", settingsViewModel.backupStartTime.value)
                        .putInt("end_time", settingsViewModel.backupEndTime.value)
                        .build()
                        
                    val req = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .setInputData(data)
                        .build()
                        
                    wm.enqueueUniquePeriodicWork("BackupWork", ExistingPeriodicWorkPolicy.UPDATE, req)
                } else {
                    wm.cancelUniqueWork("BackupWork")
                }
            }
        }

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val language by settingsViewModel.language.collectAsState()

            LaunchedEffect(language) {
                val localeList = if (language == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language)
                }
                AppCompatDelegate.setApplicationLocales(localeList)
            }

            val useDarkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            
            MyApplicationTheme(darkTheme = useDarkTheme) {
                MainNavigation(fileViewModel, settingsViewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackManager.release()
    }
}
