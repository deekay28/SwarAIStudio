package com.swarai.studio

import com.swarai.studio.export.Subtitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlesTest {
    private val lyrics = "[verse]\nपहली पंक्ति\nदूसरी पंक्ति थोड़ी लंबी है\n[inst]\n[chorus]\nतीसरी"

    @Test fun estimatesMonotonicTimingsInsideSong() {
        val lines = Subtitles.estimate(lyrics, 60_000)
        assertEquals(3, lines.size)
        assertTrue(lines.first().startMs >= 4_000)
        assertTrue(lines.zipWithNext().all { (a, b) -> a.endMs <= b.startMs + 1 })
        assertTrue(lines.last().endMs <= 60_000)
        assertEquals("दूसरी पंक्ति थोड़ी लंबी है", Subtitles.lineAt(lines, lines[1].startMs + 1)?.text)
    }

    @Test fun srtAndLrcFormats() {
        val lines = Subtitles.estimate(lyrics, 60_000)
        val srt = Subtitles.toSrt(lines)
        assertTrue(srt.startsWith("1\n00:00:"))
        assertTrue(srt.contains(" --> "))
        val lrc = Subtitles.toLrc(lines, "Song")
        assertTrue(lrc.contains("[ti:Song]"))
        assertTrue(Regex("""\[\d\d:\d\d\.\d\d]पहली""").containsMatchIn(lrc))
    }

    @Test fun emptyLyricsGiveNoLines() = assertTrue(Subtitles.estimate("[inst]", 10_000).isEmpty())
}
