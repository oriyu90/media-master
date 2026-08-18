package com.example.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
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
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext Result.failure()
            val fis = java.io.FileInputStream(pfd.fileDescriptor)
            val bis = BufferedInputStream(fis)
            val zis = ZipInputStream(bis)

            val root = Environment.getExternalStorageDirectory().absolutePath

            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val path = entry.name // e.g. "Pictures/MyAlbum/photo.jpg"
                    val destFile = File(root, path)
                    
                    destFile.parentFile?.mkdirs()
                    
                    FileOutputStream(destFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            
            zis.close()
            bis.close()
            fis.close()
            pfd.close()

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
