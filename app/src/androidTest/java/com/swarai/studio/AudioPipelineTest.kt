package com.swarai.studio

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swarai.studio.audio.AudioCodec
import com.swarai.studio.audio.AudioEditor
import com.swarai.studio.audio.EditSettings
import com.swarai.studio.audio.ExportFormat
import com.swarai.studio.audio.PcmAudio
import com.swarai.studio.audio.Wav
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real platform codecs (MediaCodec/MediaMuxer) used by Studio trim/fade/convert, fully offline. */
@RunWith(AndroidJUnit4::class)
class AudioPipelineTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "audio-test").apply { mkdirs() }

    private fun tone(seconds: Int) = PcmAudio(44100, 2, ShortArray(44100 * 2 * seconds) { i -> (8000 * sin(2 * PI * 330 * (i / 2) / 44100.0)).toInt().toShort() })

    @Test fun wavEditAndAacRoundTrip() = runBlocking {
        val src = File(dir, "tone.wav")
        Wav.write(tone(6), src)

        val trimmed = File(dir, "trimmed.wav")
        val trimmedMs = AudioEditor.render(src, EditSettings(1000, 4000, fadeInMs = 300, fadeOutMs = 300, gainDb = -3f), ExportFormat.WAV, trimmed)
        assertEquals(3000, trimmedMs)
        assertEquals(3000L, AudioCodec.decode(trimmed).durationMs)

        val m4a = File(dir, "tone.m4a")
        AudioEditor.convert(src, ExportFormat.M4A, m4a)
        assertTrue("AAC file too small", m4a.length() > 10_000)
        val decoded = AudioCodec.decode(m4a)
        assertTrue("decoded AAC duration ${decoded.durationMs}", abs(decoded.durationMs - 6000) < 300)
        assertTrue(abs(AudioCodec.durationMs(m4a) - 6000) < 300)
    }
}
