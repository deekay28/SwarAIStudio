package com.swarai.studio.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AudioCodecException(message: String) : Exception(message)

/** Decoding and encoding with the platform's MediaCodec. No third-party native code. */
object AudioCodec {
    private const val TIMEOUT_US = 10_000L
    /** Edits are done in memory; 12 minutes of stereo 48 kHz is ~140 MB, so cap to stay within the heap. */
    const val MAX_EDIT_MS = 12 * 60 * 1000L

    fun durationMs(file: File): Long = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(file.absolutePath)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                release()
            }
        }
    }.getOrDefault(0L)

    suspend fun decode(file: File): PcmAudio = withContext(Dispatchers.Default) {
        if (file.extension.equals("wav", true)) {
            file.inputStream().use { Wav.read(it) }?.let { return@withContext it }
        }
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        try {
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioCodecException("No audio track found")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            if (inputFormat.containsKey(MediaFormat.KEY_DURATION) && inputFormat.getLong(MediaFormat.KEY_DURATION) / 1000 > MAX_EDIT_MS) {
                throw AudioCodecException("Songs longer than 12 minutes can't be edited on the phone.")
            }
            val codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val out = ShortBuilder()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            try {
                while (!outputDone) {
                    ensureActive()
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val buffer = codec.getInputBuffer(inIndex)!!
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = codec.outputFormat
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        outIndex >= 0 -> {
                            if (info.size > 0) {
                                val buffer = codec.getOutputBuffer(outIndex)!!
                                (buffer as java.nio.Buffer).position(info.offset).limit(info.offset + info.size)
                                val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                out.append(shorts)
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
            } finally {
                codec.stop()
                codec.release()
            }
            PcmAudio(sampleRate, channels, out.toArray())
        } finally {
            extractor.release()
        }
    }

    /** AAC-LC in an .m4a (MPEG-4) container, playable everywhere and accepted by YouTube. */
    suspend fun encodeAac(pcm: PcmAudio, output: File, bitrate: Int = 192_000) = withContext(Dispatchers.Default) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, pcm.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        output.parentFile?.mkdirs()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        var position = 0
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        (buffer as java.nio.Buffer).clear()
                        val count = minOf(buffer.remaining() / 2, pcm.samples.size - position)
                        val ptsUs = position.toLong() / pcm.channels * 1_000_000L / pcm.sampleRate
                        if (count <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm.samples, position, count)
                            codec.queueInputBuffer(inIndex, 0, count * 2, ptsUs, 0)
                            position += count
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (info.size > 0 && !isConfig && muxerStarted) {
                            (buffer as java.nio.Buffer).position(info.offset).limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }

    private class ShortBuilder {
        private var data = ShortArray(1 shl 20)
        private var size = 0

        fun append(buffer: java.nio.ShortBuffer) {
            val n = buffer.remaining()
            if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
            buffer.get(data, size, n)
            size += n
        }

        fun toArray(): ShortArray = data.copyOf(size)
    }
}
