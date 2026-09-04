package com.example.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class RestoreWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriStr = inputData.getString("uri") ?: return@withContext Result.failure()
        try {
            val uri = Uri.parse(uriStr)
            val rootDir = Environment.getExternalStorageDirectory()

            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                ZipInputStream(BufferedInputStream(FileInputStream(pfd.fileDescriptor))).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Zip-slip guard: reject any entry that would resolve outside the backup root.
                            val destFile = BackupPaths.safeResolve(rootDir, entry.name)
                            if (destFile != null) {
                                destFile.parentFile?.mkdirs()
                                FileOutputStream(destFile).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return@withContext Result.failure()

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
