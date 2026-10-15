package com.example

import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.MainNavigation
import com.example.ui.theme.MediaMasterTheme
import com.example.deeplink.DeepLinks
import com.example.deeplink.PickRequest
import kotlinx.coroutines.flow.MutableStateFlow

import com.example.playback.PlaybackManager

class MainActivity : AppCompatActivity() {
    companion object {
        var shouldEnterPiP = false
    }

    /**
     * Route requested by an incoming external intent (custom `mediamaster://`
     * scheme or a standard VIEW/EDIT intent). `null` means "open normally".
     * Consumed by [MainNavigation] once storage access is granted.
     */
    private val pendingDeepLink = MutableStateFlow<String?>(null)

    /**
     * v1.12.0 file-manager picking: non-null while serving another app's
     * `ACTION_GET_CONTENT` / `ACTION_OPEN_DOCUMENT`. Takes precedence over
     * [pendingDeepLink] — a picker launch never becomes a normal deep link.
     */
    private val pendingPick = MutableStateFlow<PickRequest?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pick = PickRequest.parse(intent)
        if (pick != null) {
            pendingPick.value = pick
            pendingDeepLink.value = null
        } else {
            DeepLinks.resolve(intent)?.let { pendingDeepLink.value = it }
        }
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
        // v1.12.0: picker requests win over deep links (cold start included).
        pendingPick.value = PickRequest.parse(intent)
        if (pendingPick.value == null) {
            pendingDeepLink.value = DeepLinks.resolve(intent)
        }
        PlaybackManager.initialize(this)
        // v1.8.0 (#3): centralised Coil cache so grid thumbnails and viewer
        // images reuse decoded thumbnails instead of full-res re-decode on
        // every scroll/pager settle (perceived as slow load / blank-until-load).
        // Video frames use a 1s microsecond hint; all local, no network fetch.
        val imageLoader = coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
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
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val language by settingsViewModel.language.collectAsStateWithLifecycle()

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
            
            MediaMasterTheme(darkTheme = useDarkTheme) {
                MainNavigation(
                    fileViewModel = fileViewModel,
                    settingsViewModel = settingsViewModel,
                    deepLinkRoute = pendingDeepLink,
                    onDeepLinkHandled = { pendingDeepLink.value = null },
                    pickRequest = pendingPick,
                    // v1.12.0 picker result: hand the granted URI back to the
                    // requesting app and close. `null` data = user cancelled.
                    onPickFinished = { uri, mimeType ->
                        if (uri != null) {
                            val result = Intent().apply {
                                data = uri
                                type = mimeType
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                // Mirror a write grant only when the requester
                                // asked for one (GET_CONTENT is read-only).
                                if (intent?.flags?.and(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                }
                            }
                            setResult(RESULT_OK, result)
                        } else {
                            setResult(RESULT_CANCELED)
                        }
                        pendingPick.value = null
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackManager.release()
    }
}
