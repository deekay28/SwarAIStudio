package com.swarai.studio.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarai.studio.AppContainer
import com.swarai.studio.audio.AudioCodec
import com.swarai.studio.audio.AudioEditor
import com.swarai.studio.audio.AudioEffects
import com.swarai.studio.audio.EditSettings
import com.swarai.studio.audio.ExportFormat
import com.swarai.studio.data.db.ProjectEntity
import com.swarai.studio.data.db.SongEntity
import com.swarai.studio.engine.EngineInfo
import com.swarai.studio.export.ExportManager
import com.swarai.studio.export.Subtitles
import com.swarai.studio.video.CoverArt
import com.swarai.studio.video.LyricVideoRenderer
import com.swarai.studio.video.VideoOrientation
import com.swarai.studio.video.VideoProject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WaveformState(val peaks: FloatArray = FloatArray(0), val visualPeaks: FloatArray = FloatArray(0), val durationMs: Long = 0, val loading: Boolean = false, val error: String? = null)

class StudioViewModel(private val c: AppContainer, initialSongId: Long?) : ViewModel() {
    val songs = c.db.songs().observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val song = MutableStateFlow<SongEntity?>(null)
    val project = MutableStateFlow<ProjectEntity?>(null)
    val waveform = MutableStateFlow(WaveformState())
    val edit = MutableStateFlow(EditSettings(0, 0))
    val busy = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val engine = MutableStateFlow<EngineInfo?>(null)
    val player = c.player.state

    // Cover & video
    val paletteIndex = MutableStateFlow(0)
    val coverSeed = MutableStateFlow(1)
    val importedImage = MutableStateFlow<File?>(null)
    val coverPreview = MutableStateFlow<Bitmap?>(null)
    val videoProgress = MutableStateFlow<Int?>(null)
    val videoFile = MutableStateFlow<File?>(null)
    private var videoJob: Job? = null

    init {
        viewModelScope.launch {
            val id = c.engines.selectedId()
            engine.value = if (id.isEmpty()) null else c.engines.engine(id).describe()
        }
        initialSongId?.let { select(it) }
    }

    fun select(songId: Long) = viewModelScope.launch {
        val s = c.db.songs().get(songId) ?: return@launch
        song.value = s
        project.value = s.projectId?.let { c.db.projects().get(it) }
        videoFile.value = null
        importedImage.value = null
        loadWaveform(s)
        refreshCover()
    }

    private suspend fun loadWaveform(s: SongEntity) {
        waveform.value = WaveformState(loading = true)
        val result = runCatching {
            withContext(Dispatchers.Default) {
                val pcm = AudioCodec.decode(File(s.filePath))
                val seconds = (pcm.durationMs / 1000).toInt().coerceAtLeast(1)
                WaveformState(AudioEffects.peaks(pcm, 240), AudioEffects.peaks(pcm, seconds * PEAKS_PER_SECOND), pcm.durationMs)
            }
        }
        waveform.value = result.getOrElse { WaveformState(error = "Can't read this audio for editing: ${it.message}") }
        edit.value = EditSettings(0, waveform.value.durationMs)
    }

    fun updateEdit(transform: (EditSettings) -> EditSettings) { edit.value = transform(edit.value) }

    fun preview() {
        val s = song.value ?: return
        c.player.play(s.id, s.title, File(s.filePath), edit.value.startMs)
    }

    private fun isUnedited(e: EditSettings) =
        e.startMs == 0L && e.endMs >= waveform.value.durationMs && e.fadeInMs == 0L && e.fadeOutMs == 0L && e.gainDb == 0f && !e.normalize

    fun saveVersion(format: ExportFormat) = viewModelScope.launch {
        val s = song.value ?: return@launch
        busy.value = "Rendering edit…"
        runCatching {
            val out = File(c.editsDir, "edit-${s.id}-${System.currentTimeMillis()}.${format.extension}")
            val duration = withContext(Dispatchers.Default) { AudioEditor.render(File(s.filePath), edit.value, format, out) }
            val newId = c.db.songs().insert(
                s.copy(
                    id = 0, title = "${s.title} (edit)", filePath = out.absolutePath, format = format.extension, durationMs = duration,
                    sourceKind = "edited", parentSongId = s.id, favorite = false, createdAt = System.currentTimeMillis(),
                )
            )
            newId
        }.onSuccess { id -> message.value = "Saved as a new version. The original is unchanged."; select(id) }
            .onFailure { message.value = "Edit failed: ${it.message}" }
        busy.value = null
    }

    /** Exports the current edit (or the untouched original) in the chosen format. */
    fun exportAudio(format: ExportFormat?, uri: Uri) = viewModelScope.launch {
        val s = song.value ?: return@launch
        busy.value = "Exporting…"
        runCatching {
            if (format == null || (isUnedited(edit.value) && s.format.equals(format.extension, true))) {
                c.export.copyToUri(File(s.filePath), uri)
            } else {
                val tmp = File(c.app.cacheDir, "export.${format.extension}")
                withContext(Dispatchers.Default) { AudioEditor.render(File(s.filePath), edit.value, format, tmp) }
                c.export.copyToUri(tmp, uri)
                tmp.delete()
            }
        }.onSuccess { message.value = "Exported." }.onFailure { message.value = "Export failed: ${it.message}" }
        busy.value = null
    }

    // ---- YouTube metadata ----

    fun updateYoutube(title: String? = null, description: String? = null, tags: String? = null) {
        val s = song.value ?: return
        song.value = s.copy(ytTitle = title ?: s.ytTitle, ytDescription = description ?: s.ytDescription, ytTags = tags ?: s.ytTags)
    }

    fun suggestYoutube() {
        val s = song.value ?: return
        song.value = s.copy(
            ytTitle = s.ytTitle.ifBlank { s.title },
            ytDescription = ExportManager.suggestDescription(project.value, s),
            ytTags = ExportManager.suggestTags(project.value),
        )
    }

    fun saveYoutube() = viewModelScope.launch {
        song.value?.let { c.db.songs().update(it); message.value = "YouTube details saved." }
    }

    fun exportYoutubeText(uri: Uri) = viewModelScope.launch {
        val s = song.value ?: return@launch
        runCatching { c.export.writeText(uri, c.export.youtubeText(s)) }.onSuccess { message.value = "Details exported." }
            .onFailure { message.value = it.message }
    }

    private fun lyricsForTiming(): String = project.value?.lyrics?.takeIf { it.isNotBlank() } ?: song.value?.lyricsUsed.orEmpty()

    fun exportSubtitles(uri: Uri, lrc: Boolean) = viewModelScope.launch {
        val s = song.value ?: return@launch
        val lines = Subtitles.estimate(lyricsForTiming(), waveform.value.durationMs.takeIf { it > 0 } ?: s.durationMs)
        if (lines.isEmpty()) { message.value = "This song has no lyrics to time."; return@launch }
        runCatching { c.export.writeText(uri, if (lrc) Subtitles.toLrc(lines, s.title) else Subtitles.toSrt(lines)) }
            .onSuccess { message.value = "Subtitles exported (estimated timing — check before publishing)." }
            .onFailure { message.value = it.message }
    }

    // ---- Cover art ----

    fun setPalette(index: Int) { paletteIndex.value = index; refreshCover() }
    fun shuffleCover() { coverSeed.value = coverSeed.value + 1; refreshCover() }

    fun importImage(uri: Uri) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val dest = File(c.coversDir, "import-${song.value?.id ?: 0}.img")
                c.app.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                dest
            }
        }.onSuccess { importedImage.value = it; refreshCover() }.onFailure { message.value = "Could not import image: ${it.message}" }
    }

    fun clearImportedImage() { importedImage.value = null; refreshCover() }

    private fun subtitleLine(): String {
        val p = project.value
        return listOfNotNull(p?.let { com.swarai.studio.catalog.Catalog.genre(it.genreId).label }, "SwarAI Studio").joinToString(" · ")
    }

    fun coverBitmap(orientation: VideoOrientation): Bitmap {
        val s = song.value
        val title = s?.ytTitle?.ifBlank { null } ?: s?.title ?: "SwarAI"
        val img = importedImage.value
        return if (img != null) {
            CoverArt.fromImage(c.app, Uri.fromFile(img), title, subtitleLine(), orientation.width, orientation.height, withText = true)
        } else {
            CoverArt.design(title, subtitleLine(), paletteIndex.value, coverSeed.value, orientation.width, orientation.height)
        }
    }

    private fun refreshCover() = viewModelScope.launch {
        coverPreview.value = runCatching { withContext(Dispatchers.Default) { coverBitmap(VideoOrientation.LANDSCAPE) } }.getOrNull()
    }

    fun exportCover(uri: Uri) = viewModelScope.launch {
        runCatching {
            val file = withContext(Dispatchers.Default) { CoverArt.save(coverBitmap(VideoOrientation.LANDSCAPE), File(c.app.cacheDir, "cover.png")) }
            c.export.copyToUri(file, uri)
        }.onSuccess { message.value = "Cover exported (1280×720 PNG)." }.onFailure { message.value = it.message }
    }

    // ---- Lyric video ----

    fun renderVideo(orientation: VideoOrientation, showLyrics: Boolean, showVisualizer: Boolean) {
        val s = song.value ?: return
        videoJob?.cancel()
        videoJob = viewModelScope.launch {
            videoProgress.value = 0
            videoFile.value = null
            runCatching {
                val background = withContext(Dispatchers.Default) {
                    CoverArt.save(coverBitmap(orientation), File(c.videosDir, "bg-${s.id}-${orientation.name}.png"))
                }
                val durationMs = waveform.value.durationMs.takeIf { it > 0 } ?: s.durationMs
                val project = VideoProject(
                    audio = File(s.filePath), background = background, title = s.title,
                    lines = if (showLyrics) Subtitles.estimate(lyricsForTiming(), durationMs) else emptyList(),
                    peaks = waveform.value.visualPeaks, peaksPerSecond = PEAKS_PER_SECOND, durationMs = durationMs,
                    orientation = orientation, showVisualizer = showVisualizer, showLyrics = showLyrics,
                )
                LyricVideoRenderer(c.app).render(project, File(c.videosDir, "video-${s.id}-${orientation.name.lowercase()}.mp4")) {
                    videoProgress.value = it
                }
            }.onSuccess { videoFile.value = it; message.value = "Video ready. Lyric timing is estimated." }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) message.value = "Video failed: ${it.message}" }
            videoProgress.value = null
        }
    }

    fun cancelVideo() { videoJob?.cancel(); videoProgress.value = null }

    fun exportVideo(uri: Uri) = viewModelScope.launch {
        val file = videoFile.value ?: return@launch
        runCatching { c.export.copyToUri(file, uri) }.onSuccess { message.value = "Video exported." }.onFailure { message.value = it.message }
    }

    companion object {
        const val PEAKS_PER_SECOND = 25
    }
}
