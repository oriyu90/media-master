package com.example.viewer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Random-access, page-at-a-time byte reader backing the binary hex/ASCII
 * viewer. Never loads a whole file into memory: [readPage] only touches the
 * requested [PAGE_SIZE] window, so opening a multi-gigabyte file is safe.
 */
class HexPageReader private constructor(
    private val pfd: ParcelFileDescriptor,
    private val stream: FileInputStream,
    private val channel: FileChannel,
) : AutoCloseable {

    val size: Long = runCatching { channel.size() }.getOrDefault(0L)

    /** Reads one page of bytes; returns an empty array past EOF or on any I/O failure (never throws). */
    fun readPage(pageIndex: Int, pageSize: Int = PAGE_SIZE): ByteArray = runCatching {
        val offset = pageIndex.toLong() * pageSize
        if (offset < 0 || offset >= size) return@runCatching ByteArray(0)
        val length = minOf(pageSize.toLong(), size - offset).toInt()
        val buffer = ByteBuffer.allocate(length)
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, offset + buffer.position())
            if (n < 0) break
        }
        buffer.array().copyOf(buffer.position())
    }.getOrDefault(ByteArray(0))

    override fun close() {
        runCatching { stream.close() }
        runCatching { pfd.close() }
    }

    companion object {
        const val PAGE_SIZE = 4096

        /** Opens [uri] for random-access reads, or returns null if it can't be opened at all. */
        fun open(context: Context, uri: Uri): HexPageReader? = runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val stream = FileInputStream(pfd.fileDescriptor)
            HexPageReader(pfd, stream, stream.channel)
        }.getOrNull()
    }
}

/** Formats one page as classic `offset  hex..hex  ascii` lines, 16 bytes per line. */
object HexFormatter {
    fun formatLines(bytes: ByteArray, baseOffset: Long, bytesPerLine: Int = 16): List<String> {
        if (bytes.isEmpty()) return emptyList()
        return bytes.toList().chunked(bytesPerLine).mapIndexed { lineIndex, chunk ->
            val offset = baseOffset + lineIndex.toLong() * bytesPerLine
            val hex = chunk.joinToString(" ") { "%02X".format(it) }
                .padEnd(bytesPerLine * 3 - 1)
            val ascii = chunk.joinToString("") { b ->
                val c = b.toInt() and 0xFF
                if (c in 0x20..0x7E) c.toChar().toString() else "."
            }
            "%08X  %s  %s".format(offset, hex, ascii)
        }
    }
}
