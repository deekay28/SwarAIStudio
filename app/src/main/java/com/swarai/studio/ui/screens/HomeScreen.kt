package com.swarai.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swarai.studio.AppContainer
import com.swarai.studio.catalog.Catalog
import com.swarai.studio.data.db.GenerationJobEntity
import com.swarai.studio.data.db.SongEntity
import com.swarai.studio.engine.EngineInfo
import com.swarai.studio.ui.Navigator
import com.swarai.studio.ui.Tab
import com.swarai.studio.ui.components.Notice
import com.swarai.studio.ui.components.SectionCard
import com.swarai.studio.ui.components.containerViewModel
import com.swarai.studio.ui.components.formatDuration
import com.swarai.studio.ui.theme.Rani
import com.swarai.studio.ui.theme.Saffron
import com.swarai.studio.ui.theme.Violet
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val projects = c.db.projects().observeAll().map { it.take(5) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val songs = c.db.songs().observeLibrary().map { it.take(5) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val jobs = c.generation.recent().map { list -> list.filter { it.status == "QUEUED" || it.status == "RUNNING" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val engine = MutableStateFlow<EngineInfo?>(null)

    fun refreshEngine() = viewModelScope.launch {
        val id = c.engines.selectedId()
        engine.value = if (id.isEmpty()) null else c.engines.engine(id).describe()
    }

    fun play(song: SongEntity) = c.player.play(song.id, song.title, File(song.filePath))
    fun cancel(job: GenerationJobEntity) = viewModelScope.launch { c.generation.cancel(job.id) }
}

@Composable
fun HomeScreen(navigator: Navigator) {
    val vm = containerViewModel { HomeViewModel(it) }
    val projects by vm.projects.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshEngine() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Violet.copy(alpha = 0.55f), Rani.copy(alpha = 0.45f), Saffron.copy(alpha = 0.35f))), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text("SwarAI Studio", style = MaterialTheme.typography.headlineMedium)
                Text("स्वर · Turn your lyrics into songs", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Paste lyrics → Choose style → Choose voice → Generate → Listen → Save → Export",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { navigator.createProject(null) }) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create a new song")
                }
            }
        }
        item {
            SectionCard("Music engine") {
                val e = engine
                when {
                    e == null -> {
                        Notice("No music engine chosen yet. Pick one in Settings: your own SwarAI server, a DeepInfra key, or ACE-Step on your computer. Everything else in the app works offline.")
                        TextButton(onClick = { navigator.tab(Tab.SETTINGS) }) { Text("Open Settings") }
                    }
                    !e.available -> {
                        Text(e.name, fontWeight = FontWeight.SemiBold)
                        Notice(e.unavailableReason ?: "Not available", warning = true)
                        TextButton(onClick = { navigator.tab(Tab.SETTINGS) }) { Text("Fix in Settings") }
                    }
                    else -> {
                        Text(e.name, fontWeight = FontWeight.SemiBold)
                        Text("${e.kind.label} · sends lyrics to: ${e.sendsDataTo}", style = MaterialTheme.typography.bodySmall)
                        if (!e.isAiMusic) Notice("Diagnostic engine: produces a test tone, not music.", warning = true)
                    }
                }
            }
        }
        if (jobs.isNotEmpty()) {
            item {
                SectionCard("Generating now") {
                    jobs.forEach { job ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${job.engineName}: ${job.progress.ifBlank { job.status }}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.cancel(job) }) { Text("Cancel") }
                            }
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        item {
            SectionCard("Recent projects") {
                if (projects.isEmpty()) Text("No projects yet. Your drafts save automatically.", style = MaterialTheme.typography.bodySmall)
                projects.forEach { p ->
                    Column(Modifier.fillMaxWidth().clickable { navigator.createProject(p.id) }.padding(vertical = 6.dp)) {
                        Text(p.title.ifBlank { "Untitled" }, fontWeight = FontWeight.Medium)
                        Text(
                            "${Catalog.genre(p.genreId).label} · ${Catalog.language(p.languageId).label} · " +
                                p.lyrics.lineSequence().firstOrNull { it.isNotBlank() && !it.trim().startsWith("[") }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            SectionCard("Recent songs") {
                if (songs.isEmpty()) Text("Generated and imported songs appear here and play offline.", style = MaterialTheme.typography.bodySmall)
                SongRows(songs, vm::play)
            }
        }
    }
}

@Composable
private fun SongRows(songs: List<SongEntity>, onPlay: (SongEntity) -> Unit) {
    songs.forEach { song ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPlay(song) }) { Icon(Icons.Rounded.PlayArrow, "Play ${song.title}") }
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatDuration(song.durationMs)} · ${song.format.uppercase()}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
