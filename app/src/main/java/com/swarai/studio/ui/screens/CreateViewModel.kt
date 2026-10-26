package com.swarai.studio.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarai.studio.AppContainer
import com.swarai.studio.data.db.GenerationJobEntity
import com.swarai.studio.data.db.PresetEntity
import com.swarai.studio.data.db.ProjectEntity
import com.swarai.studio.data.db.SongEntity
import com.swarai.studio.engine.EngineInfo
import com.swarai.studio.engine.PromptBuilder
import com.swarai.studio.lyrics.LyricsTools
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val MAX_LYRICS = 6000

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CreateViewModel(
    private val c: AppContainer,
    projectId: Long?,
    initialVoiceId: String?,
    instrumental: Boolean,
) : ViewModel() {
    private val _project = MutableStateFlow(ProjectEntity(title = ""))
    val project: StateFlow<ProjectEntity> = _project.asStateFlow()
    private val _saved = MutableStateFlow(true)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()
    val message = MutableStateFlow<String?>(null)
    /** Bumped when lyrics change from outside the text field (load, import, restore). */
    val lyricsRevision = MutableStateFlow(0)
    val engine = MutableStateFlow<EngineInfo?>(null)
    private val activeJobId = MutableStateFlow<Long?>(null)
    val job: StateFlow<GenerationJobEntity?> = activeJobId.flatMapLatest { id -> if (id == null) flowOf<GenerationJobEntity?>(null) else c.generation.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val projectIdFlow = MutableStateFlow<Long?>(null)
    val songs: StateFlow<List<SongEntity>> = projectIdFlow.flatMapLatest { id -> if (id == null) emptyFlow<List<SongEntity>>() else c.db.songs().observeForProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val presets = c.db.presets().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val existing = projectId?.let { c.db.projects().get(it) }
            if (existing != null) {
                _project.value = existing.let { p ->
                    p.copy(voiceId = initialVoiceId ?: p.voiceId, instrumental = instrumental || p.instrumental)
                }
                projectIdFlow.value = existing.id
                if (initialVoiceId != null || instrumental) scheduleSave()
            } else {
                _project.value = ProjectEntity(title = "", voiceId = initialVoiceId ?: "folk_female_warm", instrumental = instrumental)
            }
            lyricsRevision.value++
            refreshEngine()
        }
    }

    fun refreshEngine() = viewModelScope.launch {
        val id = c.engines.selectedId()
        engine.value = if (id.isEmpty()) null else c.engines.engine(id).describe()
    }

    fun update(transform: (ProjectEntity) -> ProjectEntity) {
        _project.value = transform(_project.value)
        scheduleSave()
    }

    fun setLyrics(text: String) = update { p ->
        val clipped = text.take(MAX_LYRICS)
        // Keep the first block of lyrics the user pasted, so edits can always be undone.
        val pasted = clipped.length - p.lyrics.length > 20
        p.copy(lyrics = clipped, originalLyrics = if (p.originalLyrics.isEmpty() && pasted) clipped else p.originalLyrics)
    }

    fun restoreOriginal() {
        update { it.copy(lyrics = it.originalLyrics) }
        lyricsRevision.value++
    }

    private fun scheduleSave() {
        _saved.value = false
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            saveNow()
        }
    }

    suspend fun saveNow(): Long? {
        val p = _project.value
        if (p.id == 0L && p.title.isBlank() && p.lyrics.isBlank()) { _saved.value = true; return null }
        val stamped = p.copy(updatedAt = System.currentTimeMillis(), title = p.title)
        val id = if (p.id == 0L) c.db.projects().insert(stamped) else { c.db.projects().update(stamped); p.id }
        if (_project.value.id == 0L) _project.value = _project.value.copy(id = id)
        projectIdFlow.value = id
        _saved.value = true
        return id
    }

    fun importLyrics(uri: Uri) = viewModelScope.launch {
        runCatching { c.export.readText(uri) }
            .onSuccess { text ->
                update { p -> p.copy(lyrics = LyricsTools.normalize(text).take(MAX_LYRICS), originalLyrics = LyricsTools.normalize(text).take(MAX_LYRICS)) }
                lyricsRevision.value++
                message.value = "Lyrics imported. The original text is preserved."
            }
            .onFailure { message.value = it.message }
    }

    fun exportLyrics(uri: Uri) = viewModelScope.launch {
        runCatching { c.export.writeText(uri, _project.value.lyrics) }
            .onSuccess { message.value = "Lyrics exported." }
            .onFailure { message.value = it.message }
    }

    fun savePreset(name: String) = viewModelScope.launch {
        val p = _project.value
        c.db.presets().insert(
            PresetEntity(
                name = name.ifBlank { "Preset" }, genreId = p.genreId, moodId = p.moodId, voiceId = p.voiceId,
                instrumentIds = p.instrumentIds, arrangementId = p.arrangementId, intensity = p.intensity,
                vocalBalance = p.vocalBalance, bpm = p.bpm, durationSec = p.durationSec, extraPrompt = p.extraPrompt,
            )
        )
        message.value = "Preset saved."
    }

    fun applyPreset(preset: PresetEntity) = update {
        it.copy(
            genreId = preset.genreId, moodId = preset.moodId, voiceId = preset.voiceId, instrumentIds = preset.instrumentIds,
            arrangementId = preset.arrangementId, intensity = preset.intensity, vocalBalance = preset.vocalBalance,
            bpm = preset.bpm, durationSec = preset.durationSec, extraPrompt = preset.extraPrompt,
        )
    }

    fun togglePresetFavorite(preset: PresetEntity) = viewModelScope.launch { c.db.presets().update(preset.copy(favorite = !preset.favorite)) }
    fun deletePreset(preset: PresetEntity) = viewModelScope.launch { c.db.presets().delete(preset) }

    /** Returns a problem to show the user, or null when the request may proceed to cost confirmation. */
    fun validate(): String? {
        val p = _project.value
        val e = engine.value ?: return "Choose a music engine in Settings first."
        if (!e.available) return e.unavailableReason ?: "The selected engine is not available."
        if (!p.instrumental && LyricsTools.singableLines(p.lyrics).isEmpty()) return "Add some lyrics, or switch on Instrumental."
        if (p.instrumental && !e.capabilities.instrumental) return "${e.name} can't make instrumentals."
        return null
    }

    fun generate() = viewModelScope.launch {
        val e = engine.value ?: return@launch
        val id = saveNow() ?: run {
            update { it.copy(title = "Untitled song") }
            saveNow()
        }
        val p = _project.value
        val spec = PromptBuilder.spec(p).let { it.copy(durationSec = e.clampDuration(it.durationSec), variations = it.variations.coerceAtMost(e.capabilities.maxVariations)) }
        activeJobId.value = c.generation.enqueue(id, e, spec)
    }

    fun cancel() = viewModelScope.launch { activeJobId.value?.let { c.generation.cancel(it) } }

    fun play(song: SongEntity) = c.player.play(song.id, song.title, File(song.filePath))

    override fun onCleared() {
        // Flush any pending draft so nothing typed is lost when leaving the screen.
        if (!_saved.value) c.appScope.launch { saveNow() }
    }
}
