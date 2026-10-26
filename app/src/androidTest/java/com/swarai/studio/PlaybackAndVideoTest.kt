package com.swarai.studio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swarai.studio.audio.AudioCodec
import com.swarai.studio.audio.AudioEffects
import com.swarai.studio.audio.PcmAudio
import com.swarai.studio.audio.Wav
import com.swarai.studio.export.Subtitles
import com.swarai.studio.video.CoverArt
import com.swarai.studio.video.LyricVideoRenderer
import com.swarai.studio.video.VideoOrientation
import com.swarai.studio.video.VideoProject
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackAndVideoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = (context.applicationContext as SwarApp).container
    private val dir = File(context.cacheDir, "pv-test").apply { mkdirs() }

    private fun toneFile(seconds: Int): File = File(dir, "tone$seconds.wav").also { f ->
        Wav.write(PcmAudio(44100, 2, ShortArray(44100 * 2 * seconds) { i -> (9000 * sin(2 * PI * 262 * (i / 2) / 44100.0)).toInt().toShort() }), f)
    }

    @Test fun playsLocalFileWithoutNetwork() {
        val file = toneFile(5)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { container.player.play(-42, "Test", file) }
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline && container.player.state.value.positionMs < 300) Thread.sleep(200)
        val state = container.player.state.value
        instrumentation.runOnMainSync { container.player.stop() }
        assertTrue("player did not advance: $state", state.positionMs >= 300 || state.isPlaying)
    }

    @Test fun rendersLyricVideoOnDevice() = runBlocking {
        val audio = toneFile(4)
        val pcm = AudioCodec.decode(audio)
        val bg = CoverArt.save(CoverArt.design("परीक्षण गीत", "SwarAI test", 0, 1, 1280, 720), File(dir, "bg.png"))
        val project = VideoProject(
            audio = audio, background = bg, title = "Test",
            lines = Subtitles.estimate("[verse]\nपहली पंक्ति\nSecond line", pcm.durationMs),
            peaks = AudioEffects.peaks(pcm, 4 * 25), peaksPerSecond = 25, durationMs = pcm.durationMs,
            orientation = VideoOrientation.LANDSCAPE,
        )
        val out = withTimeout(180_000) { LyricVideoRenderer(context).render(project, File(dir, "test.mp4")) {} }
        assertTrue("video too small: ${out.length()}", out.length() > 20_000)
        assertTrue("video duration ${AudioCodec.durationMs(out)}", AudioCodec.durationMs(out) in 3500..4600)
    }
}
