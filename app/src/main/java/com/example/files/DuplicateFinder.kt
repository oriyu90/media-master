package com.example.files

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.MediaFile
import java.io.File
import java.security.MessageDigest

/**
 * Duplicate detection domain (MVI Model layer).
 *
 * Size pre-grouping plus head(+tail) sampled hashing. A null hash means
 * "unreadable" — callers must never group null hashes together.
 * Pure I/O: call off the main thread.
 */
class DuplicateFinder(private val resolver: ContentResolver) {

    /** Head sample (first 256KB). */
    fun calculatePartialHash(mediaFile: MediaFile): String? =
        sampleStream(mediaFile, headBytes = 256 * 1024L, tailBytes = 0L)

    /** Head + tail sampled hash (first 256KB + last 256KB + size mixed in). */
    fun calculateSampledHash(mediaFile: MediaFile): String? =
        sampleStream(mediaFile, headBytes = 256 * 1024L, tailBytes = 256 * 1024L)

    private fun sampleStream(mediaFile: MediaFile, headBytes: Long, tailBytes: Long): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            if (mediaFile.contentUri != null && mediaFile.contentUri != Uri.EMPTY) {
                resolver.openInputStream(mediaFile.contentUri)?.use { input ->
                    var remaining = headBytes
                    while (remaining > 0) {
                        val chunk = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (chunk == -1) break
                        md.update(buffer, 0, chunk)
                        remaining -= chunk
                    }
                } ?: return null
                if (tailBytes > 0) {
                    // Tail sampling via FileDescriptor seek when possible; skip silently otherwise.
                    try {
                        resolver.openFileDescriptor(mediaFile.contentUri, "r")?.use { pfd ->
                            val fileSize = pfd.statSize.takeIf { it > 0 } ?: mediaFile.size
                            val tailStart = maxOf(0L, fileSize - tailBytes)
                            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { raw ->
                                // Re-open for tail: descriptor streams support skip reliably for local files.
                                var toSkip = tailStart - headBytes.coerceAtMost(fileSize)
                                while (toSkip > 0) {
                                    val skipped = raw.skip(toSkip)
                                    if (skipped <= 0) break
                                    toSkip -= skipped
                                }
                                var remainingTail = minOf(tailBytes, fileSize - tailStart)
                                while (remainingTail > 0) {
                                    val chunk = raw.read(buffer, 0, minOf(buffer.size.toLong(), remainingTail).toInt())
                                    if (chunk == -1) break
                                    md.update(buffer, 0, chunk)
                                    remainingTail -= chunk
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Tail unavailable (remote provider) — head-only hash still valid.
                    }
                }
            } else {
                val file = File(mediaFile.path)
                if (!file.isFile || !file.canRead()) return null
                file.inputStream().use { input ->
                    var remaining = headBytes
                    while (remaining > 0) {
                        val chunk = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (chunk == -1) break
                        md.update(buffer, 0, chunk)
                        remaining -= chunk
                    }
                }
                if (tailBytes > 0) {
                    java.io.RandomAccessFile(file, "r").use { raf ->
                        val tailStart = maxOf(0L, raf.length() - tailBytes)
                        raf.seek(tailStart)
                        var remainingTail = raf.length() - tailStart
                        while (remainingTail > 0) {
                            val chunk = raf.read(buffer, 0, minOf(buffer.size.toLong(), remainingTail).toInt())
                            if (chunk == -1) break
                            md.update(buffer, 0, chunk)
                            remainingTail -= chunk
                        }
                    }
                }
            }
            // Mix size in so same-prefix files of different lengths never collide.
            md.update(mediaFile.size.toString().toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
