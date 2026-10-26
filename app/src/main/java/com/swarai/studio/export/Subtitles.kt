package com.swarai.studio.export

import com.swarai.studio.lyrics.LyricsTools
import java.util.Locale

data class TimedLine(val startMs: Long, val endMs: Long, val text: String)

/**
 * Lyric timing. Providers do not return word timings to this app, so timings are ESTIMATED by spreading
 * sung lines across the song in proportion to their length, after an intro allowance. The UI says so.
 */
object Subtitles {
    fun estimate(lyrics: String, durationMs: Long, introFraction: Double = 0.08, outroFraction: Double = 0.06): List<TimedLine> {
        val lines = LyricsTools.singableLines(lyrics)
        if (lines.isEmpty() || durationMs <= 0) return emptyList()
        val start = (durationMs * introFraction).toLong()
        val end = (durationMs * (1 - outroFraction)).toLong()
        val weights = lines.map { it.length.coerceAtLeast(4).toDouble() }
        val total = weights.sum()
        var cursor = start.toDouble()
        return lines.mapIndexed { index, text ->
            val span = (end - start) * weights[index] / total
            val line = TimedLine(cursor.toLong(), (cursor + span).toLong(), text)
            cursor += span
            line
        }
    }

    fun lineAt(lines: List<TimedLine>, positionMs: Long): TimedLine? =
        lines.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }

    fun toSrt(lines: List<TimedLine>): String = buildString {
        lines.forEachIndexed { i, line ->
            append(i + 1).append('\n')
            append(srtTime(line.startMs)).append(" --> ").append(srtTime(line.endMs)).append('\n')
            append(line.text).append("\n\n")
        }
    }

    fun toLrc(lines: List<TimedLine>, title: String): String = buildString {
        append("[ti:").append(title).append("]\n")
        append("[re:SwarAI Studio (estimated timing)]\n")
        lines.forEach { append('[').append(lrcTime(it.startMs)).append(']').append(it.text).append('\n') }
    }

    private fun srtTime(ms: Long): String =
        String.format(Locale.US, "%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)

    private fun lrcTime(ms: Long): String =
        String.format(Locale.US, "%02d:%02d.%02d", ms / 60_000, ms / 1000 % 60, ms % 1000 / 10)
}
