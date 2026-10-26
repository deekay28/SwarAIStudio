package com.swarai.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swarai.studio.AppContainer
import com.swarai.studio.audio.AudioCodec
import com.swarai.studio.catalog.Catalog
import com.swarai.studio.data.db.ProjectEntity
import com.swarai.studio.data.db.SongEntity
import com.swarai.studio.ui.Navigator
import com.swarai.studio.ui.components.Badge
import com.swarai.studio.ui.components.Notice
import com.swarai.studio.ui.components.SaveAs
import com.swarai.studio.ui.components.containerViewModel
import com.swarai.studio.ui.components.formatDuration
import com.swarai.studio.ui.components.rememberSaveAs
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(val c: AppContainer) : ViewModel() {
    val songs = c.db.songs().observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val projects = c.db.projects().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val player = c.player.state
    val message = MutableStateFlow<String?>(null)

    fun play(song: SongEntity) {
        val file = File(song.filePath)
        if (!file.exists()) { message.value = "The audio file for \"${song.title}\" is missing."; return }
        if (player.value.songId == song.id && player.value.isPlaying) c.player.pause() else c.player.play(song.id, song.title, file)
    }

    fun rename(song: SongEntity, title: String) = viewModelScope.launch { c.db.songs().update(song.copy(title = title.trim().take(120).ifBlank { song.title })) }
    fun toggleFavorite(song: SongEntity) = viewModelScope.launch { c.db.songs().update(song.copy(favorite = !song.favorite)) }

    fun delete(song: SongEntity) = viewModelScope.launch {
        c.player.releaseIfPlaying(song.id)
        c.db.songs().delete(song)
        File(song.filePath).delete()
    }

    fun export(song: SongEntity, uri: Uri) = viewModelScope.launch {
        runCatching { c.export.copyToUri(File(song.filePath), uri) }
            .onSuccess { message.value = "Exported \"${song.title}\"." }
            .onFailure { message.value = it.message }
    }

    fun importAudio(uri: Uri) = viewModelScope.launch {
        runCatching {
            val file = c.export.importAudio(uri, c.importsDir)
            c.db.songs().insert(
                SongEntity(
                    title = "Imported ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())}",
                    filePath = file.absolutePath, format = file.extension, durationMs = AudioCodec.durationMs(file),
                    sourceKind = "imported", engineId = "", providerName = "Imported file", isAiGenerated = false,
                )
            )
        }.onSuccess { message.value = "Audio imported." }.onFailure { message.value = it.message }
    }

    fun deleteProject(project: ProjectEntity) = viewModelScope.launch { c.db.projects().delete(project) }

    fun exportProject(project: ProjectEntity, uri: Uri) = viewModelScope.launch {
        runCatching {
            val songs = c.db.songs().all().filter { it.projectId == project.id }
            c.export.writeText(uri, c.export.encodeProject(c.export.projectFile(project, songs)))
        }.onSuccess { message.value = "Project file exported (settings and lyrics; audio is exported separately)." }
            .onFailure { message.value = it.message }
    }

    fun importProject(uri: Uri) = viewModelScope.launch {
        runCatching { c.db.projects().insert(c.export.decodeProject(c.export.readText(uri))) }
            .onSuccess { message.value = "Project imported." }
            .onFailure { message.value = it.message }
    }
}

private enum class Filter(val label: String) { ALL("All"), FAVORITES("Favorites"), GENERATED("AI generated"), EDITED("Edited"), IMPORTED("Imported") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(navigator: Navigator) {
    val vm = containerViewModel { MusicViewModel(it) }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val playback by vm.player.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showProjects by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var renaming by remember { mutableStateOf<SongEntity?>(null) }
    var deleting by remember { mutableStateOf<SongEntity?>(null) }
    val saveAs = rememberSaveAs { vm.message.value = it }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importAudio) }
    val importProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importProject) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("My Music", style = MaterialTheme.typography.headlineMedium)
            Text("Everything here is stored on this phone and works offline.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showProjects, onClick = { showProjects = false }, label = { Text("Songs (${songs.size})") })
                FilterChip(selected = showProjects, onClick = { showProjects = true }, label = { Text("Projects (${projects.size})") })
            }
        }
        message?.let { item { Notice(it) } }
        if (!showProjects) {
            item {
                OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, label = { Text("Search songs") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Filter.entries.take(3).forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Filter.entries.drop(3).forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                    OutlinedButton(onClick = { importAudio.launch(arrayOf("audio/*")) }) { Icon(Icons.Rounded.FileUpload, null); Text(" Import audio") }
                }
            }
            val visible = songs.filter { s ->
                (query.isBlank() || s.title.contains(query, ignoreCase = true)) && when (filter) {
                    Filter.ALL -> true
                    Filter.FAVORITES -> s.favorite
                    Filter.GENERATED -> s.sourceKind == "generated"
                    Filter.EDITED -> s.sourceKind == "edited"
                    Filter.IMPORTED -> s.sourceKind == "imported"
                }
            }
            if (visible.isEmpty()) item { Text("No songs yet. Create one, or import an audio file.", style = MaterialTheme.typography.bodyMedium) }
            items(visible, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    playing = playback.songId == song.id && playback.isPlaying,
                    onPlay = { vm.play(song) },
                    onFavorite = { vm.toggleFavorite(song) },
                    onRename = { renaming = song },
                    onStudio = { navigator.studio(song.id) },
                    onExport = {
                        saveAs.launch("${song.title}.${song.format}", SaveAs.mimeForAudio(song.format)) { uri -> vm.export(song, uri) }
                    },
                    onDelete = { deleting = song },
                )
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navigator.createProject(null) }) { Text("New project") }
                    OutlinedButton(onClick = { importProject.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) { Text("Import project file") }
                }
            }
            items(projects, key = { it.id }) { project ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().clickable { navigator.createProject(project.id) }.padding(14.dp)) {
                        Text(project.title.ifBlank { "Untitled" }, fontWeight = FontWeight.SemiBold)
                        Text("${Catalog.genre(project.genreId).label} · ${Catalog.language(project.languageId).label} · ${Catalog.voice(project.voiceId).name}",
                            style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { navigator.createProject(project.id) }) { Text("Open") }
                            TextButton(onClick = {
                                saveAs.launch("${project.title.ifBlank { "project" }}.swarai.json", "application/json") { uri -> vm.exportProject(project, uri) }
                            }) { Text("Export") }
                            TextButton(onClick = { vm.deleteProject(project) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { song ->
        var name by remember(song.id) { mutableStateOf(song.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename song") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { Button(onClick = { vm.rename(song, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { song ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${song.title}\"?") },
            text = { Text("The audio file is removed from this phone. Exported copies are not affected.") },
            confirmButton = { Button(onClick = { vm.delete(song); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SongRow(
    song: SongEntity,
    playing: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onStudio: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlay) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play") }
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatDuration(song.durationMs)} · ${song.format.uppercase()}", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    when {
                        song.sourceKind == "edited" -> Badge("Edited")
                        song.isAiGenerated -> Badge("AI")
                        song.sourceKind == "imported" -> Badge("Imported", MaterialTheme.colorScheme.tertiary)
                        else -> Badge("Test tone", MaterialTheme.colorScheme.error)
                    }
                }
                if (song.providerName.isNotBlank()) {
                    Text(song.providerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(onClick = onFavorite) { Icon(if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite") }
            Column {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open in Studio") }, onClick = { menu = false; onStudio() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Export audio file") }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
