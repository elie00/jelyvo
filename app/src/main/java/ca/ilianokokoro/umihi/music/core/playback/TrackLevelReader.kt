package ca.ilianokokoro.umihi.music.core.playback

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import ca.ilianokokoro.umihi.music.automix.LevelMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Decodes a track through the same data source as playback, so downloads, the cache and resolved
 * stream addresses all work, and keeps only its loudness.
 */
@OptIn(UnstableApi::class)
class TrackLevelReader(private val dataSourceFactory: DataSource.Factory) {

    /** One level per frame, or null for a track this device cannot decode or that is too long. */
    suspend fun read(uri: Uri): FloatArray? = withContext(Dispatchers.IO) {
        val source = DataSourceAdapter(dataSourceFactory, uri)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext null
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION) &&
                format.getLong(MediaFormat.KEY_DURATION) > MAX_DURATION_US
            ) {
                return@withContext null
            }
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()
            decode(extractor, decoder, format)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            runCatching { source.close() }
        }
    }

    private suspend fun decode(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        inputFormat: MediaFormat,
    ): FloatArray {
        val info = MediaCodec.BufferInfo()
        var meter = LevelMeter(
            sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
        )
        var isFloat = false
        var inputDone = false
        while (true) {
            coroutineContext.ensureActive()
            if (!inputDone) {
                val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = decoder.getInputBuffer(index) ?: continue
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(
                            index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val output = decoder.outputFormat
                    isFloat = output.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        output.getInteger(MediaFormat.KEY_PCM_ENCODING) ==
                        AudioFormat.ENCODING_PCM_FLOAT
                    // The decoder knows better than the container, which can be wrong about both.
                    meter = LevelMeter(
                        sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    )
                }

                else -> if (index >= 0) {
                    decoder.getOutputBuffer(index)?.let { buffer ->
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        if (isFloat) {
                            val samples = buffer.asFloatBuffer()
                            while (samples.hasRemaining()) meter.add(samples.get())
                        } else {
                            val samples = buffer.asShortBuffer()
                            while (samples.hasRemaining()) meter.add(samples.get() / 32768f)
                        }
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return meter.finish()
                    }
                }
            }
        }
    }

    /** MediaExtractor asks for bytes by position; a Media3 source reads forward from an opening. */
    private class DataSourceAdapter(
        private val factory: DataSource.Factory,
        private val uri: Uri,
    ) : MediaDataSource() {
        private var source: DataSource? = null
        private var position = -1L
        private var length = C.LENGTH_UNSET.toLong()

        private fun openAt(target: Long) {
            source?.close()
            val opened = factory.createDataSource()
            source = opened
            val available = opened.open(DataSpec.Builder().setUri(uri).setPosition(target).build())
            if (available != C.LENGTH_UNSET.toLong()) length = target + available
            position = target
        }

        override fun readAt(offset: Long, buffer: ByteArray, start: Int, size: Int): Int {
            if (size == 0) return 0
            if (length != C.LENGTH_UNSET.toLong() && offset >= length) return -1
            if (source == null || position != offset) openAt(offset)
            val read = source!!.read(buffer, start, size)
            if (read == C.RESULT_END_OF_INPUT) return -1
            position += read
            return read
        }

        override fun getSize(): Long {
            if (length == C.LENGTH_UNSET.toLong() && source == null) openAt(0)
            return length
        }

        override fun close() {
            source?.close()
            source = null
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        /** Mixes that long are not songs, and decoding one would take a long while. */
        const val MAX_DURATION_US = 15L * 60L * 1_000_000L
    }
}
