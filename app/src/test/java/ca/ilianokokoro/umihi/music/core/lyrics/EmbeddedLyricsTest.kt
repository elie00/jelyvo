package ca.ilianokokoro.umihi.music.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class EmbeddedLyricsTest {
    private val text = "Première ligne\nSeconde ligne"

    private fun bytes(block: ByteArrayOutputStream.() -> Unit) = ByteArrayOutputStream().apply(block).toByteArray()

    private fun ByteArrayOutputStream.ascii(value: String) = write(value.toByteArray(Charsets.ISO_8859_1))

    private fun be32(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
    )

    private fun le32(value: Int) = be32(value).reversedArray()

    private fun synchsafe(value: Int) = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(), ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(), (value and 0x7F).toByte(),
    )

    private fun uslt(encoding: Int, charset: java.nio.charset.Charset, terminator: Int, bom: ByteArray = byteArrayOf()) = bytes {
        write(encoding)
        ascii("fra")
        write(bom)
        repeat(terminator) { write(0) }
        write(bom)
        write(text.toByteArray(charset))
    }

    private fun id3(major: Int, vararg frames: Pair<String, ByteArray>): ByteArray {
        val body = bytes {
            frames.forEach { (id, payload) ->
                ascii(id)
                write(if (major == 4) synchsafe(payload.size) else be32(payload.size))
                write(byteArrayOf(0, 0))
                write(payload)
            }
        }
        return bytes {
            ascii("ID3")
            write(byteArrayOf(major.toByte(), 0, 0))
            write(synchsafe(body.size))
            write(body)
            ascii("ÿûaudio frames")
        }
    }

    @Test
    fun `reads an mp3's lyrics in every text encoding ID3 allows`() {
        assertEquals(text, EmbeddedLyrics.fromBytes(id3(3, "USLT" to uslt(0, Charsets.ISO_8859_1, 1))))
        assertEquals(text, EmbeddedLyrics.fromBytes(id3(4, "USLT" to uslt(3, Charsets.UTF_8, 1))))
        assertEquals(text, EmbeddedLyrics.fromBytes(id3(4, "USLT" to uslt(2, Charsets.UTF_16BE, 2))))
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        assertEquals(
            text,
            EmbeddedLyrics.fromBytes(id3(3, "USLT" to uslt(1, Charsets.UTF_16BE, 2, bom))),
        )
    }

    @Test
    fun `finds the lyrics behind other frames, and only in the tag`() {
        val title = "TIT2" to bytes { write(3); ascii("Titre") }
        assertEquals(text, EmbeddedLyrics.fromBytes(id3(4, title, "USLT" to uslt(3, Charsets.UTF_8, 1))))
        assertNull(EmbeddedLyrics.fromBytes(id3(4, title)))
    }

    private fun flacWith(vararg comments: String) = bytes {
        ascii("fLaC")
        val block = bytes {
            write(le32(6)); ascii("vendor")
            write(le32(comments.size))
            comments.forEach {
                val encoded = it.toByteArray(Charsets.UTF_8)
                write(le32(encoded.size)); write(encoded)
            }
        }
        write(0x84)
        write(byteArrayOf((block.size ushr 16).toByte(), (block.size ushr 8).toByte(), block.size.toByte()))
        write(block)
    }

    @Test
    fun `reads a flac's lyrics comment, under either name`() {
        assertEquals(text, EmbeddedLyrics.fromBytes(flacWith("TITLE=x", "LYRICS=$text")))
        assertEquals(text, EmbeddedLyrics.fromBytes(flacWith("unsyncedlyrics=$text")))
        assertNull(EmbeddedLyrics.fromBytes(flacWith("TITLE=x")))
    }

    private fun box(type: ByteArray, payload: ByteArray) = bytes {
        write(be32(payload.size + 8)); write(type); write(payload)
    }

    private fun m4a(dataType: Int, moovFirst: Boolean = true): ByteArray {
        val value = text.toByteArray(Charsets.UTF_8)
        val data = box("data".toByteArray(), be32(dataType) + be32(0) + value)
        val lyr = box(byteArrayOf(0xA9.toByte()) + "lyr".toByteArray(), data)
        val moov = box("moov".toByteArray(), lyr)
        val ftyp = box("ftyp".toByteArray(), "M4A ".toByteArray() + be32(0))
        val mdat = box("mdat".toByteArray(), ByteArray(64))
        return if (moovFirst) ftyp + moov + mdat else ftyp + mdat + moov
    }

    @Test
    fun `reads an m4a's lyrics wherever its moov box sits`() {
        assertEquals(text, EmbeddedLyrics.fromBytes(m4a(1, moovFirst = true)))
        assertEquals(text, EmbeddedLyrics.fromBytes(m4a(1, moovFirst = false)))
    }

    @Test
    fun `a picture stored where text belongs is not a lyric`() {
        assertNull(EmbeddedLyrics.fromBytes(m4a(13)))
    }

    @Test
    fun `other files and truncated or lying tags are a null, never a crash`() {
        assertNull(EmbeddedLyrics.fromBytes(ByteArray(0)))
        assertNull(EmbeddedLyrics.fromBytes("OggS and something else".toByteArray()))
        val full = id3(4, "USLT" to uslt(3, Charsets.UTF_8, 1))
        (1 until full.size).forEach { EmbeddedLyrics.fromBytes(full.copyOf(it)) }
        val flac = flacWith("LYRICS=$text")
        (1 until flac.size).forEach { EmbeddedLyrics.fromBytes(flac.copyOf(it)) }
        val mp4 = m4a(1)
        (1 until mp4.size).forEach { EmbeddedLyrics.fromBytes(mp4.copyOf(it)) }
        // A frame claiming more bytes than the file has.
        val lying = id3(3, "USLT" to uslt(3, Charsets.UTF_8, 1)).also { it[14] = 0x7F }
        assertNull(EmbeddedLyrics.fromBytes(lying))
    }

    @Test
    fun `timed text keeps its times, and plain text says it is plain`() {
        val timed = EmbeddedLyrics.toLyrics("[00:01.00]Un\n[00:02.50]Deux")!!
        assertTrue(timed.isSynced)
        assertEquals(listOf<Long?>(1_000, 2_500), timed.lines.map { it.timeMs })

        val plain = EmbeddedLyrics.toLyrics(text)!!
        assertFalse(plain.isSynced)
        assertEquals(listOf("Première ligne", "Seconde ligne"), plain.lines.map { it.text })

        assertNull(EmbeddedLyrics.toLyrics("   \n  "))
    }

    @Test
    fun `word stamps are kept when the file has them`() {
        val lyrics = EmbeddedLyrics.toLyrics("[00:01.00]<00:01.00>Un <00:01.50>deux")!!
        assertTrue(lyrics.hasWordTiming)
    }
}
