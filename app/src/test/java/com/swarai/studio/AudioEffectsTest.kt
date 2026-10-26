package com.swarai.studio

import com.swarai.studio.audio.AudioEditor
import com.swarai.studio.audio.AudioEffects
import com.swarai.studio.audio.EditSettings
import com.swarai.studio.audio.PcmAudio
import com.swarai.studio.audio.Wav
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEffectsTest {
    private fun tone(seconds: Int, rate: Int = 8000, channels: Int = 2, amp: Double = 10000.0) =
        PcmAudio(rate, channels, ShortArray(rate * seconds * channels) { i -> (amp * sin(2 * PI * 440 * (i / channels) / rate)).toInt().toShort() })

    @Test fun trimKeepsRequestedRange() {
        val out = AudioEffects.trim(tone(4), 1000, 3000)
        assertEquals(2000, out.durationMs)
        assertEquals(2, out.channels)
    }

    @Test fun fadeStartsAndEndsSilent() {
        val out = AudioEffects.fade(tone(2), 500, 500)
        assertEquals(0, out.samples[0].toInt())
        assertTrue(abs(out.samples[out.samples.size - 1].toInt()) < 50)
        val mid = out.samples.size / 2
        assertTrue((mid - 50 until mid + 50).any { abs(out.samples[it].toInt()) > 5000 })
    }

    @Test fun gainClipsInsteadOfWrapping() {
        val loud = AudioEffects.gainDb(tone(1, amp = 30000.0), 12f)
        assertTrue(loud.samples.all { it >= Short.MIN_VALUE && it <= Short.MAX_VALUE })
        assertTrue(loud.samples.any { it == Short.MAX_VALUE })
    }

    @Test fun normaliseReachesTargetPeak() {
        val quiet = tone(1, amp = 3000.0)
        val out = AudioEditor.apply(quiet, EditSettings(0, quiet.durationMs, normalize = true))
        assertEquals(-1f, AudioEffects.peakDb(out), 0.2f)
    }

    @Test fun wavRoundTrip() {
        val pcm = tone(1, rate = 22050, channels = 1)
        val bytes = ByteArrayOutputStream().also { Wav.write(pcm, it) }.toByteArray()
        assertEquals(44 + pcm.samples.size * 2, bytes.size)
        val back = Wav.read(ByteArrayInputStream(bytes))!!
        assertEquals(22050, back.sampleRate)
        assertEquals(1, back.channels)
        assertArrayEquals(pcm.samples, back.samples)
    }

    @Test fun peaksAreNormalised() {
        val peaks = AudioEffects.peaks(tone(2), 100)
        assertEquals(100, peaks.size)
        assertTrue(peaks.all { it in 0f..1f })
        assertTrue(peaks.average() > 0.25)
    }
}
