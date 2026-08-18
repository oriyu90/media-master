package com.example.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targetPathString = inputData.getString("target_path") ?: return@withContext Result.failure()
        val deletePrevious = inputData.getBoolean("delete_previous", false)
        val startTime = inputData.getInt("start_time", 0)
        val endTime = inputData.getInt("end_time", 360)

        // Check time window
        val calendar = java.util.Calendar.getInstance()
        val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        
        val inWindow = if (startTime <= endTime) {
            currentMinutes in startTime..endTime
        } else {
            currentMinutes >= startTime || currentMinutes <= endTime
        }
        
        // If it's a scheduled run and we're not in the window, just return success (will try again next interval)
        if (!inputData.getBoolean("is_manual", false) && !inWindow) {
            return@withContext Result.success()
        }

        try {
            val targetUri = Uri.parse(targetPathString)
            val dir = DocumentFile.fromTreeUri(context, targetUri) ?: return@withContext Result.failure()
            
            if (deletePrevious) {
                for (file in dir.listFiles()) {
                    if (file.name?.startsWith("MediaMaster_Backup_") == true) {
                        file.delete()
                    }
                }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = dir.createFile("application/zip", "MediaMaster_Backup_$timestamp.zip") ?: return@withContext Result.failure()
            
            val pfd = context.contentResolver.openFileDescriptor(backupFile.uri, "w") ?: return@withContext Result.failure()
            val fos = java.io.FileOutputStream(pfd.fileDescriptor)
            val bos = BufferedOutputStream(fos)
            val zos = ZipOutputStream(bos)

            val projection = arrayOf(MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )
            
            val cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                null,
                null,
                null
            )
            
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val relCol = it.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val nameCol = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                
                while (it.moveToNext()) {
                    val path = it.getString(dataCol) ?: continue
                    val id = it.getLong(idCol)
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                    
                    // Exclude Android/data and Android/obb which are app files
                    if (path.contains("/Android/data/") || path.contains("/Android/obb/")) continue
                    
                    val relPath = if (relCol >= 0) it.getString(relCol) else File(path).parentFile?.name ?: "Unknown"
                    val name = if (nameCol >= 0) it.getString(nameCol) else File(path).name
                    
                    val zipEntryName = "${relPath?.trimEnd('/')}/$name".replace("//", "/")
                    
                    try {
                        val entry = ZipEntry(zipEntryName)
                        zos.putNextEntry(entry)
                        context.contentResolver.openInputStream(uri)?.use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            zos.close()
            bos.close()
            fos.close()
            pfd.close()

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
