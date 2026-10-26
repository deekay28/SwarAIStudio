package com.swarai.studio.audio

import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Interleaved 16-bit PCM. Pure Kotlin so every edit is unit-testable on the JVM. */
class PcmAudio(val sampleRate: Int, val channels: Int, val samples: ShortArray) {
    val frames: Int get() = samples.size / channels
    val durationMs: Long get() = frames * 1000L / sampleRate

    fun frameAt(ms: Long): Int = (ms * sampleRate / 1000).toInt().coerceIn(0, frames)
}

object AudioEffects {
    fun trim(pcm: PcmAudio, startMs: Long, endMs: Long): PcmAudio {
        val start = pcm.frameAt(startMs)
        val end = max(start, pcm.frameAt(endMs))
        return PcmAudio(pcm.sampleRate, pcm.channels, pcm.samples.copyOfRange(start * pcm.channels, end * pcm.channels))
    }

    /** Linear-in-amplitude fades; durations longer than the clip are clamped. */
    fun fade(pcm: PcmAudio, fadeInMs: Long, fadeOutMs: Long): PcmAudio {
        val out = pcm.samples.copyOf()
        val inFrames = min(pcm.frameAt(fadeInMs), pcm.frames)
        val outFrames = min(pcm.frameAt(fadeOutMs), pcm.frames)
        for (f in 0 until inFrames) {
            val g = f.toFloat() / inFrames
            for (c in 0 until pcm.channels) out[f * pcm.channels + c] = (out[f * pcm.channels + c] * g).toInt().toShort()
        }
        for (i in 0 until outFrames) {
            val f = pcm.frames - 1 - i
            val g = i.toFloat() / outFrames
            for (c in 0 until pcm.channels) out[f * pcm.channels + c] = (out[f * pcm.channels + c] * g).toInt().toShort()
        }
        return PcmAudio(pcm.sampleRate, pcm.channels, out)
    }

    fun gainDb(pcm: PcmAudio, db: Float): PcmAudio {
        if (db == 0f) return pcm
        val factor = 10.0.pow(db / 20.0).toFloat()
        val out = ShortArray(pcm.samples.size) { i ->
            (pcm.samples[i] * factor).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return PcmAudio(pcm.sampleRate, pcm.channels, out)
    }

    fun peakDb(pcm: PcmAudio): Float {
        var peak = 1
        for (s in pcm.samples) peak = max(peak, abs(s.toInt()))
        return (20 * kotlin.math.log10(peak / 32767.0)).toFloat()
    }

    /** Gain that brings the peak to [targetDb] (e.g. -1 dBFS). */
    fun normalizeGainDb(pcm: PcmAudio, targetDb: Float = -1f): Float = targetDb - peakDb(pcm)

    /** Peak amplitude (0..1) per bucket, for drawing a waveform. */
    fun peaks(pcm: PcmAudio, buckets: Int): FloatArray {
        if (pcm.frames == 0 || buckets <= 0) return FloatArray(max(0, buckets))
        val result = FloatArray(buckets)
        val framesPerBucket = max(1, pcm.frames / buckets)
        for (b in 0 until buckets) {
            val startFrame = b * pcm.frames / buckets
            val endFrame = min(pcm.frames, startFrame + framesPerBucket)
            var peak = 0
            for (i in startFrame * pcm.channels until endFrame * pcm.channels) peak = max(peak, abs(pcm.samples[i].toInt()))
            result[b] = peak / 32768f
        }
        return result
    }
}

object Wav {
    fun write(pcm: PcmAudio, out: OutputStream) {
        val dataBytes = pcm.samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + dataBytes); put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(pcm.channels.toShort())
            putInt(pcm.sampleRate); putInt(pcm.sampleRate * pcm.channels * 2); putShort((pcm.channels * 2).toShort()); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(dataBytes)
        }
        out.write(header.array())
        val chunk = ByteBuffer.allocate(8192 * 2).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (i < pcm.samples.size) {
            (chunk as java.nio.Buffer).clear()
            val n = min(8192, pcm.samples.size - i)
            for (k in 0 until n) chunk.putShort(pcm.samples[i + k])
            out.write(chunk.array(), 0, n * 2)
            i += n
        }
    }

    fun write(pcm: PcmAudio, file: File) = file.outputStream().buffered().use { write(pcm, it) }

    /** Reads 16-bit PCM WAV files; returns null for other encodings so the caller can fall back to MediaCodec. */
    fun read(input: InputStream): PcmAudio? {
        val data = DataInputStream(input.buffered())
        val riff = ByteArray(12)
        data.readFully(riff)
        if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" || String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var channels = 0
        var rate = 0
        var bits = 0
        var format = 0
        val chunkHeader = ByteArray(8)
        while (true) {
            if (data.read(chunkHeader) < 8) return null
            val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (id == "fmt ") {
                val fmt = ByteArray(size).also { data.readFully(it) }
                val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                format = bb.short.toInt(); channels = bb.short.toInt(); rate = bb.int; bb.int; bb.short; bits = bb.short.toInt()
            } else if (id == "data") {
                if (format != 1 || bits != 16 || channels <= 0) return null
                val bytes = ByteArray(size).also { data.readFully(it) }
                val shorts = ShortArray(size / 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                return PcmAudio(rate, channels, shorts)
            } else {
                data.skipBytes(size + (size and 1))
            }
        }
    }
}
