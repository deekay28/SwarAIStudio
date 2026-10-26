package com.swarai.studio.audio

import java.io.File

data class EditSettings(
    val startMs: Long,
    val endMs: Long,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val gainDb: Float = 0f,
    val normalize: Boolean = false,
)

enum class ExportFormat(val extension: String, val label: String, val mime: String) {
    WAV("wav", "WAV (lossless)", "audio/wav"),
    M4A("m4a", "AAC / M4A", "audio/mp4"),
}

object AudioEditor {
    fun apply(pcm: PcmAudio, edit: EditSettings): PcmAudio {
        var out = AudioEffects.trim(pcm, edit.startMs, edit.endMs)
        var gain = edit.gainDb
        if (edit.normalize) gain += AudioEffects.normalizeGainDb(AudioEffects.gainDb(out, gain))
        out = AudioEffects.gainDb(out, gain)
        return AudioEffects.fade(out, edit.fadeInMs, edit.fadeOutMs)
    }

    suspend fun render(source: File, edit: EditSettings, format: ExportFormat, output: File): Long =
        write(apply(AudioCodec.decode(source), edit), format, output)

    suspend fun convert(source: File, format: ExportFormat, output: File): Long =
        write(AudioCodec.decode(source), format, output)

    private suspend fun write(pcm: PcmAudio, format: ExportFormat, output: File): Long {
        when (format) {
            ExportFormat.WAV -> Wav.write(pcm, output)
            ExportFormat.M4A -> AudioCodec.encodeAac(pcm, output)
        }
        return pcm.durationMs
    }
}
