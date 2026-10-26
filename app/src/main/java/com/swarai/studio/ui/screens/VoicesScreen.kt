package com.swarai.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swarai.studio.AppContainer
import com.swarai.studio.catalog.Catalog
import com.swarai.studio.catalog.VocalPresentation
import com.swarai.studio.catalog.VoiceProfile
import com.swarai.studio.data.db.SongEntity
import com.swarai.studio.engine.EngineInfo
import com.swarai.studio.engine.GenerationSpec
import com.swarai.studio.ui.Navigator
import com.swarai.studio.ui.components.Badge
import com.swarai.studio.ui.components.Notice
import com.swarai.studio.ui.components.SectionCard
import com.swarai.studio.ui.components.containerViewModel
import com.swarai.studio.ui.components.formatUsd
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoicesViewModel(private val c: AppContainer) : ViewModel() {
    val previews = c.db.songs().observeVoicePreviews()
        .map { list -> list.groupBy { it.voicePreviewId!! }.mapValues { it.value.first() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val jobs = c.generation.recent().map { list -> list.filter { it.voicePreviewId != null && (it.status == "QUEUED" || it.status == "RUNNING") }.map { it.voicePreviewId!! }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val engine = MutableStateFlow<EngineInfo?>(null)
    val player = c.player.state
    val message = MutableStateFlow<String?>(null)

    fun refresh() = viewModelScope.launch {
        val id = c.engines.selectedId()
        engine.value = if (id.isEmpty()) null else c.engines.engine(id).describe()
    }

    fun previewSpec(voice: VoiceProfile, e: EngineInfo): GenerationSpec = GenerationSpec(
        caption = "${voice.tags}, short vocal showcase, gentle harmonium and tabla accompaniment, ${voice.delivery} style",
        lyrics = if (voice.presentation == VocalPresentation.DUET) DUET_SAMPLE else SAMPLE,
        languageCode = "hi",
        durationSec = e.clampDuration(30),
        variations = 1,
    )

    fun generate(voice: VoiceProfile) = viewModelScope.launch {
        val e = engine.value ?: return@launch
        c.generation.enqueue(null, e, previewSpec(voice, e), voicePreviewId = voice.id)
        message.value = "Generating a preview of ${voice.name}…"
    }

    fun play(song: SongEntity) {
        if (player.value.songId == song.id && player.value.isPlaying) c.player.pause()
        else c.player.play(song.id, song.title, File(song.filePath))
    }

    companion object {
        const val SAMPLE = "[verse]\nसाँझ ढले जब दीप जले\nमन में मीठी आस पले\n[chorus]\nगाओ रे, मन गाओ रे"
        const val DUET_SAMPLE = "[verse]\n(पुरुष) साँझ ढले जब दीप जले\n(स्त्री) मन में मीठी आस पले\n[chorus]\n(दोनों) गाओ रे, मन गाओ रे"
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(navigator: Navigator) {
    val vm = containerViewModel { VoicesViewModel(it) }
    val previews by vm.previews.collectAsStateWithLifecycle()
    val running by vm.jobs.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val playback by vm.player.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<VocalPresentation?>(null) }
    var confirmVoice by remember { mutableStateOf<VoiceProfile?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Voices", style = MaterialTheme.typography.headlineMedium)
            Notice("These are original voice descriptions that steer the model; they are not recordings of, or clones of, real singers. Previews are generated by your selected engine; nothing is pre-recorded, so a voice has no sample until you generate one.")
        }
        message?.let { item { Notice(it) } }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                VocalPresentation.entries.forEach { p -> FilterChip(selected = filter == p, onClick = { filter = p }, label = { Text(p.label) }) }
            }
        }
        items(Catalog.voices.filter { filter == null || it.presentation == filter }, key = { it.id }) { voice ->
            val preview = previews[voice.id]
            SectionCard(voice.name, subtitle = "${voice.presentation.label} · ${voice.register} · ${voice.delivery}") {
                Text(voice.description, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    voice.qualities.forEach { Badge(it) }
                    Badge(voice.technique, MaterialTheme.colorScheme.tertiary)
                }
                if (voice.presentation == VocalPresentation.DUET || voice.presentation == VocalPresentation.GROUP) {
                    Text("Duet/group singing is requested in the prompt; the model may still render a single voice.", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navigator.createProject(null, voiceId = voice.id) }) { Text("Use voice") }
                    when {
                        preview != null -> {
                            val playing = playback.songId == preview.id && playback.isPlaying
                            IconButton(onClick = { vm.play(preview) }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play preview") }
                            Text("Preview by ${preview.providerName}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        }
                        voice.id in running -> Text("Generating preview…", style = MaterialTheme.typography.labelSmall)
                        else -> OutlinedButton(onClick = { confirmVoice = voice }, enabled = engine?.available == true) { Text("Generate preview") }
                    }
                }
            }
        }
    }

    confirmVoice?.let { voice ->
        val e = engine
        AlertDialog(
            onDismissRequest = { confirmVoice = null },
            title = { Text("Generate a preview of ${voice.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (e != null) {
                        Text("A short sample (${e.clampDuration(30)} s) will be generated by ${e.name}. Sample lyrics are sent to ${e.sendsDataTo}.")
                        Text("Estimated cost: ${formatUsd(e.estimateUsd(vm.previewSpec(voice, e)))}")
                    }
                }
            },
            confirmButton = { Button(onClick = { vm.generate(voice); confirmVoice = null }) { Text("Generate") } },
            dismissButton = { TextButton(onClick = { confirmVoice = null }) { Text("Cancel") } },
        )
    }
}
