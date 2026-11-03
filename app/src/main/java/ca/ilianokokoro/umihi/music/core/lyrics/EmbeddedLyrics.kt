/*
 * The FLAC and MP4 readers adapted from BitChord's EmbeddedLyrics
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.core.lyrics

import android.content.Context
import androidx.core.net.toUri
import ca.ilianokokoro.umihi.music.models.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * The lyrics already inside an audio file: `USLT` in an MP3's ID3 tag, `LYRICS` in a FLAC's comments,
 * `©lyr` in an M4A. Asking servers for them would be a round trip to fetch what is on the device.
 *
 * Never throws: a file in another container, or with no lyrics in it, is a null and the caller falls
 * back to the network. Every length below is stated by the file itself, so each is checked first.
 */
object EmbeddedLyrics {
    /** Tags sit near the start in all three, or the file is one this will not spend memory on. */
    private const val MAX_TAG_BYTES = 8 * 1024 * 1024
    private const val MAX_COMMENTS = 4_096

    suspend fun forUri(context: Context, uri: String): Lyrics? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { stream ->
                fromBytes(stream.readAtMost(MAX_TAG_BYTES))?.let(::toLyrics)
            }
        }.getOrNull()
    }

    /** Word-timed where the text has the stamps for it, then by line, then as plain text. */
    internal fun toLyrics(raw: String): Lyrics? {
        val enhanced = EnhancedLrc.parse(raw)
        if (enhanced.isNotEmpty()) return Lyrics(enhanced, isSynced = true)
        val timed = Lyrics.parseLrc(raw).filter { it.timeMs != null }
        if (timed.any { it.text.isNotBlank() }) return Lyrics(timed, isSynced = true)
        val plain = Lyrics.parsePlain(raw)
        return plain.takeIf { lines -> lines.any { it.text.isNotBlank() } }
            ?.let { Lyrics(it, isSynced = false) }
    }

    internal fun fromBytes(head: ByteArray): String? = runCatching {
        when {
            head.startsWith(ID3_MAGIC) -> id3(head)
            head.startsWith(FLAC_MAGIC) -> flac(head)
            head.isMp4() -> mp4(head)
            else -> null
        }
    }.getOrNull()?.takeIf(String::isNotBlank)

    // ---- ID3 (MP3) ----------------------------------------------------------

    private fun id3(bytes: ByteArray): String? {
        if (bytes.size < 10) return null
        val major = bytes[3].toInt() and 0xFF
        val flags = bytes[5].toInt() and 0xFF
        val tagEnd = minOf(10 + synchsafe(bytes, 6), bytes.size)
        // Whole-tag unsynchronisation only existed up to 2.3; 2.4 marks it frame by frame.
        val tag = if (flags and 0x80 != 0 && major < 4) removeUnsync(bytes, tagEnd) else bytes
        val end = minOf(tagEnd, tag.size)
        var pos = 10
        if (flags and 0x40 != 0 && major >= 3) {
            pos += if (major == 4) synchsafe(tag, pos) else readU32(tag, pos).toInt() + 4
        }
        val idLength = if (major == 2) 3 else 4
        val headerLength = if (major == 2) 6 else 10
        while (pos + headerLength <= end) {
            val id = String(tag, pos, idLength, Charsets.ISO_8859_1)
            if (id[0].code == 0) return null
            val size = when (major) {
                2 -> ((tag[pos + 3].toInt() and 0xFF) shl 16) or
                    ((tag[pos + 4].toInt() and 0xFF) shl 8) or (tag[pos + 5].toInt() and 0xFF)
                3 -> readU32(tag, pos + 4).toInt()
                else -> synchsafe(tag, pos + 4)
            }
            val start = pos + headerLength
            if (size <= 0 || start + size > end) return null
            if (id == "USLT" || id == "ULT") return unsyncedLyrics(tag, start, start + size)
            pos = start + size
        }
        return null
    }

    /** Encoding (1), language (3), a descriptor ended by a terminator of that encoding, the text. */
    private fun unsyncedLyrics(bytes: ByteArray, start: Int, end: Int): String? {
        if (end - start < 5) return null
        val encoding = bytes[start].toInt() and 0xFF
        val wide = encoding == 1 || encoding == 2
        var at = start + 4
        if (wide) {
            while (at + 1 < end && (bytes[at].toInt() != 0 || bytes[at + 1].toInt() != 0)) at += 2
            at += 2
        } else {
            while (at < end && bytes[at].toInt() != 0) at++
            at += 1
        }
        if (at >= end) return null
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        return String(bytes, at, end - at, charset).trimEnd('\u0000')
    }

    private fun removeUnsync(bytes: ByteArray, end: Int): ByteArray {
        val out = ByteArrayOutputStream(end)
        var i = 0
        while (i < end) {
            out.write(bytes[i].toInt())
            if (bytes[i].toInt() and 0xFF == 0xFF && i + 1 < end && bytes[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun synchsafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or (b[off + 3].toInt() and 0x7F)

    // ---- FLAC ---------------------------------------------------------------

    private fun flac(bytes: ByteArray): String? {
        var pos = FLAC_MAGIC.size
        while (pos + 4 <= bytes.size) {
            val flags = bytes[pos].toInt() and 0xFF
            val length = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or (bytes[pos + 3].toInt() and 0xFF)
            val start = pos + 4
            if (start + length > bytes.size) return null
            if (flags and 0x7F == FLAC_VORBIS_COMMENT) return vorbisComment(bytes, start, start + length)
            if (flags and 0x80 != 0) return null
            pos = start + length
        }
        return null
    }

    /** Every length in here is little-endian: the one part of FLAC that borrowed Ogg's layout. */
    private fun vorbisComment(bytes: ByteArray, start: Int, end: Int): String? {
        var pos = start
        fun u32(): Int? {
            if (pos + 4 > end) return null
            val value = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or ((bytes[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return value
        }
        val vendor = u32() ?: return null
        if (vendor < 0) return null
        pos += vendor
        val count = u32() ?: return null
        repeat(count.coerceIn(0, MAX_COMMENTS)) {
            val length = u32() ?: return null
            if (length < 0 || pos + length > end) return null
            val entry = String(bytes, pos, length, Charsets.UTF_8)
            pos += length
            val name = entry.substringBefore('=').uppercase()
            val value = entry.substringAfter('=', "")
            if ((name == "LYRICS" || name == "UNSYNCEDLYRICS") && value.isNotBlank()) return value
        }
        return null
    }

    // ---- MP4 / M4A ----------------------------------------------------------

    /**
     * Scoped to `moov`: four bytes turn up in audio data often enough that searching the whole file
     * would eventually read a frame as a tag.
     */
    private fun mp4(bytes: ByteArray): String? {
        val moov = topLevelBox(bytes, "moov") ?: return null
        val end = moov.last + 1
        var at = moov.first
        while (true) {
            val found = bytes.indexOf(LYR_ATOM, at, end) ?: return null
            val data = bytes.indexOf(DATA_ATOM, found, end) ?: return null
            dataText(bytes, data, end)?.let { return it }
            at = found + LYR_ATOM.size
        }
    }

    /** `moov` may come after the audio, so this steps box to box rather than assuming a place. */
    private fun topLevelBox(bytes: ByteArray, type: String): IntRange? {
        var pos = 0
        while (pos + 8 <= bytes.size) {
            val declared = readU32(bytes, pos)
            var headerLength = 8
            var size = declared
            if (declared == 1L) {
                if (pos + 16 > bytes.size) return null
                size = readU64(bytes, pos + 8)
                headerLength = 16
            } else if (declared == 0L) {
                size = (bytes.size - pos).toLong()
            }
            if (size < headerLength || size > Int.MAX_VALUE) return null
            val end = (pos + size).coerceAtMost(bytes.size.toLong()).toInt()
            if (String(bytes, pos + 4, 4, Charsets.ISO_8859_1) == type) return pos until end
            if (pos + size > bytes.size) return null
            pos += size.toInt()
        }
        return null
    }

    /** Version and flags, locale, then the value. Type 1 is text; a cover's 13 or 14 is not a lyric. */
    private fun dataText(bytes: ByteArray, dataAt: Int, end: Int): String? {
        val start = dataAt - 4
        if (start < 0 || dataAt + 12 > end) return null
        val size = readU32(bytes, start)
        if (size <= 16 || start + size > end) return null
        if (readU32(bytes, dataAt + 4).toInt() != 1) return null
        return String(bytes, dataAt + 12, (start + size).toInt() - (dataAt + 12), Charsets.UTF_8)
    }

    // ---- Bytes --------------------------------------------------------------

    private fun InputStream.readAtMost(max: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(max, 1 shl 16))
        val buffer = ByteArray(1 shl 16)
        var total = 0
        while (total < max) {
            val read = read(buffer, 0, minOf(buffer.size, max - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.isMp4(): Boolean =
        size > 12 && this[4] == 'f'.code.toByte() && this[5] == 't'.code.toByte() &&
            this[6] == 'y'.code.toByte() && this[7] == 'p'.code.toByte()

    private fun ByteArray.indexOf(needle: ByteArray, from: Int, until: Int): Int? {
        val last = minOf(until, size) - needle.size
        var i = from.coerceAtLeast(0)
        outer@ while (i <= last) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        return null
    }

    private fun readU32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    private fun readU64(b: ByteArray, off: Int): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (b[off + i].toLong() and 0xFF)
        return value
    }

    private const val FLAC_VORBIS_COMMENT = 4
    private val ID3_MAGIC = "ID3".toByteArray(Charsets.US_ASCII)
    private val FLAC_MAGIC = "fLaC".toByteArray(Charsets.US_ASCII)
    private val DATA_ATOM = "data".toByteArray(Charsets.ISO_8859_1)
    private val LYR_ATOM = byteArrayOf(0xA9.toByte()) + "lyr".toByteArray(Charsets.ISO_8859_1)
}
