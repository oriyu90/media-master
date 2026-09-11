package com.example.viewer

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Charset-sniffing text reader for the universal document viewer.
 *
 * Android/Java has no bundled universal charset detector, and files opened
 * from arbitrary storage (SD card, network share, another app's share sheet)
 * are not guaranteed to be UTF-8 — Shift_JIS is still common for Japanese
 * text files. This does a lightweight BOM + UTF-8-validity + Shift_JIS
 * heuristic sniff instead of pulling in a new dependency. It never throws:
 * worst case for an exotic/binary-ish encoding is best-effort text with the
 * platform's standard U+FFFD replacement, not a crash.
 */
object TextCharsetReader {

    /** Full loads are capped so a huge/misdetected file can't blow up memory. */
    const val DEFAULT_MAX_BYTES = 5 * 1024 * 1024 // 5 MB

    private val UTF8: Charset = Charsets.UTF_8
    private val UTF16LE: Charset = Charsets.UTF_16LE
    private val UTF16BE: Charset = Charsets.UTF_16BE
    private val SHIFT_JIS: Charset? = runCatching { Charset.forName("Shift_JIS") }.getOrNull()

    data class Result(
        val text: String,
        val charsetName: String,
        val truncated: Boolean,
        val bytesRead: Long,
    )

    /** Reads up to [maxBytes] starting at [skipBytes] from [uri]. Returns null only if the file can't be opened at all. */
    fun read(context: Context, uri: Uri, maxBytes: Int = DEFAULT_MAX_BYTES, skipBytes: Long = 0L): Result? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                if (skipBytes > 0) skipFully(raw, skipBytes)
                val bytes = readUpTo(raw, maxBytes + 1)
                val truncated = bytes.size > maxBytes
                val clipped = if (truncated) bytes.copyOf(maxBytes) else bytes
                val (charset, bodyOffset) = detectCharset(clipped)
                val text = String(clipped, bodyOffset, clipped.size - bodyOffset, charset)
                Result(text, charset.name(), truncated, clipped.size.toLong())
            }
        }.getOrNull()

    private fun skipFully(stream: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val n = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) break
            remaining -= n
        }
    }

    private fun readUpTo(stream: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val toRead = minOf(buffer.size, limit - total)
            val n = stream.read(buffer, 0, toRead)
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun ByteArray.hasBom(vararg bom: Int): Boolean =
        size >= bom.size && bom.indices.all { (this[it].toInt() and 0xFF) == bom[it] }

    /** Exposed `internal` (not `private`) so unit tests can exercise the sniffing logic without an Android Context. */
    internal fun detectCharset(bytes: ByteArray): Pair<Charset, Int> = when {
        bytes.hasBom(0xEF, 0xBB, 0xBF) -> UTF8 to 3
        bytes.hasBom(0xFF, 0xFE) -> UTF16LE to 2
        bytes.hasBom(0xFE, 0xFF) -> UTF16BE to 2
        isValidUtf8(bytes) -> UTF8 to 0
        SHIFT_JIS != null && looksLikeShiftJis(bytes) -> SHIFT_JIS to 0
        else -> UTF8 to 0
    }

    /** Strict UTF-8 structural validity check (no decoding, just byte-sequence shape). */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val extra = when {
                b0 <= 0x7F -> 0
                b0 and 0xE0 == 0xC0 -> 1
                b0 and 0xF0 == 0xE0 -> 2
                b0 and 0xF8 == 0xF0 -> 3
                else -> return false
            }
            if (i + extra >= bytes.size) return extra == 0 // truncated multibyte at EOF: tolerate (chunk boundary)
            for (j in 1..extra) {
                if ((bytes[i + j].toInt() and 0xC0) != 0x80) return false
            }
            i += extra + 1
        }
        return true
    }

    /** Heuristic: most double-byte sequences fall in the Shift_JIS lead/trail ranges. */
    private fun looksLikeShiftJis(bytes: ByteArray): Boolean {
        var i = 0
        var twoByteSequences = 0
        var highBitBytes = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            if (b0 <= 0x7F) {
                i++
                continue
            }
            highBitBytes++
            val isLead = (b0 in 0x81..0x9F) || (b0 in 0xE0..0xFC)
            if (!isLead || i + 1 >= bytes.size) return false
            val b1 = bytes[i + 1].toInt() and 0xFF
            val isTrail = (b1 in 0x40..0x7E) || (b1 in 0x80..0xFC)
            if (!isTrail) return false
            twoByteSequences++
            i += 2
        }
        // Pure ASCII would already have matched isValidUtf8; require we actually saw some
        // high-bit content that consistently parsed as Shift_JIS pairs.
        return highBitBytes > 0 && twoByteSequences > 0
    }
}
