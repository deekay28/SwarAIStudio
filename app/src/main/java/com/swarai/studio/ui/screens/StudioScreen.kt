package com.swarai.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarai.studio.audio.ExportFormat
import com.swarai.studio.ui.Navigator
import com.swarai.studio.ui.components.Notice
import com.swarai.studio.ui.components.SaveAs
import com.swarai.studio.ui.components.SectionCard
import com.swarai.studio.ui.components.containerViewModel
import com.swarai.studio.ui.components.formatDuration
import com.swarai.studio.ui.components.rememberSaveAs
import com.swarai.studio.video.CoverArt
import com.swarai.studio.video.VideoOrientation
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(navigator: Navigator, songId: Long?) {
    val vm = containerViewModel(key = "studio-$songId") { StudioViewModel(it, songId) }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val song by vm.song.collectAsStateWithLifecycle()
    val project by vm.project.collectAsStateWithLifecycle()
    val wave by vm.waveform.collectAsStateWithLifecycle()
    val edit by vm.edit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val playback by vm.player.collectAsStateWithLifecycle()
    val cover by vm.coverPreview.collectAsStateWithLifecycle()
    val palette by vm.paletteIndex.collectAsStateWithLifecycle()
    val imported by vm.importedImage.collectAsStateWithLifecycle()
    val videoProgress by vm.videoProgress.collectAsStateWithLifecycle()
    val videoFile by vm.videoFile.collectAsStateWithLifecycle()
    val saveAs = rememberSaveAs { vm.message.value = it }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::importImage) }
    var orientation by remember { mutableStateOf(VideoOrientation.LANDSCAPE) }
    var showLyrics by remember { mutableStateOf(true) }
    var showVisualizer by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Studio", style = MaterialTheme.typography.headlineMedium)
        message?.let { Notice(it) }
        busy?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp)); Text("  $it") } }

        val s = song
        if (s == null) {
            SectionCard("Choose a song to edit") {
                if (songs.isEmpty()) Text("No songs yet. Generate or import one first.")
                songs.forEach { item ->
                    Text(item.title, Modifier.fillMaxWidth().clickable { vm.select(item.id) }.padding(vertical = 10.dp))
                }
            }
            return@Column
        }

        SectionCard(s.title, subtitle = "${s.format.uppercase()} · ${formatDuration(wave.durationMs.takeIf { it > 0 } ?: s.durationMs)} · ${s.providerName.ifBlank { s.sourceKind }}") {
            TextButton(onClick = { vm.song.value = null }) { Text("Choose another song") }
            when {
                wave.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                wave.error != null -> Notice(wave.error!!, warning = true)
                else -> {
                    val position = if (playback.songId == s.id) playback.positionMs else -1L
                    Waveform(wave.peaks, wave.durationMs, edit.startMs, edit.endMs, position)
                    if (wave.durationMs > 0) {
                        Text("Trim: ${formatDuration(edit.startMs)} – ${formatDuration(edit.endMs)}", style = MaterialTheme.typography.labelMedium)
                        RangeSlider(
                            value = edit.startMs.toFloat()..edit.endMs.toFloat().coerceAtLeast(edit.startMs.toFloat()),
                            onValueChange = { r -> vm.updateEdit { it.copy(startMs = r.start.toLong(), endMs = r.endInclusive.toLong()) } },
                            valueRange = 0f..wave.durationMs.toFloat(),
                        )
                        val clip = (edit.endMs - edit.startMs).coerceAtLeast(0)
                        Text("Fade in: ${edit.fadeInMs / 1000.0}s", style = MaterialTheme.typography.labelMedium)
                        Slider(edit.fadeInMs.toFloat(), { v -> vm.updateEdit { it.copy(fadeInMs = v.toLong()) } }, valueRange = 0f..minOf(15000f, clip.toFloat().coerceAtLeast(1f)))
                        Text("Fade out: ${edit.fadeOutMs / 1000.0}s", style = MaterialTheme.typography.labelMedium)
                        Slider(edit.fadeOutMs.toFloat(), { v -> vm.updateEdit { it.copy(fadeOutMs = v.toLong()) } }, valueRange = 0f..minOf(15000f, clip.toFloat().coerceAtLeast(1f)))
                        Text("Volume: ${"%+.1f".format(edit.gainDb)} dB", style = MaterialTheme.typography.labelMedium)
                        Slider(edit.gainDb, { v -> vm.updateEdit { it.copy(gainDb = (v * 2).roundToInt() / 2f) } }, valueRange = -12f..12f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(edit.normalize, { v -> vm.updateEdit { it.copy(normalize = v) } })
                            Text("Normalise peak to −1 dBFS")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = vm::preview) { Icon(Icons.Rounded.PlayArrow, null); Text(" From trim start") }
                        }
                        Text("Save edit as a new version (original is kept):", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportFormat.entries.forEach { f -> Button(onClick = { vm.saveVersion(f) }, enabled = busy == null) { Text(f.label) } }
                        }
                    }
                }
            }
        }

        SectionCard("Export audio", subtitle = "Saved wherever you choose (Downloads, Drive…). Edits above are applied, except for \"Original file\".") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveAs.launch("${s.title}.${s.format}", SaveAs.mimeForAudio(s.format)) { vm.exportAudio(null, it) } }) {
                    Text("Original file (${s.format.uppercase()})")
                }
                ExportFormat.entries.forEach { f ->
                    OutlinedButton(onClick = { saveAs.launch("${s.title}.${f.extension}", f.mime) { vm.exportAudio(f, it) } }, enabled = wave.error == null) {
                        Text(f.label)
                    }
                }
            }
            Notice("MP3 export is only available as the original file when the engine delivered MP3: Android has no built-in MP3 encoder. Use AAC/M4A or WAV for YouTube; both are accepted.")
        }

        SectionCard("AI tools", subtitle = engine?.let { "Using ${it.name}" } ?: "No engine selected") {
            val e = engine
            val pid = s.projectId
            if (pid != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navigator.createProject(pid) }, enabled = e?.available == true) { Text("New variations") }
                    OutlinedButton(onClick = { navigator.createProject(pid, instrumental = true) }, enabled = e?.capabilities?.instrumental == true) { Text("Instrumental version") }
                }
            } else {
                Text("Variations need the song's project (lyrics and style). Imported songs don't have one.", style = MaterialTheme.typography.bodySmall)
            }
            listOf(
                "repaint" to "Regenerate a section",
                "extend" to "Extend song",
                "extract" to "Stem separation",
                "cover" to "Cover / restyle",
            ).forEach { (op, label) ->
                val engineHas = e?.capabilities?.editOperations?.contains(op) == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Text(
                        if (engineHas) "Engine supports it; not in this app version yet" else "Not available with this engine",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionCard("YouTube details") {
            OutlinedTextField(s.ytTitle, { vm.updateYoutube(title = it.take(100)) }, label = { Text("Title (max 100)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.ytDescription, { vm.updateYoutube(description = it.take(5000)) }, label = { Text("Description") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.ytTags, { vm.updateYoutube(tags = it.take(500)) }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::suggestYoutube) { Text("Suggest") }
                Button(onClick = vm::saveYoutube) { Text("Save") }
                OutlinedButton(onClick = { saveAs.launch("${s.title}-youtube.txt", "text/plain") { vm.exportYoutubeText(it) } }) { Text("Export .txt") }
            }
            Text("Lyric subtitles (timing is estimated from line length; check before publishing):", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveAs.launch("${s.title}.srt", "application/octet-stream") { vm.exportSubtitles(it, lrc = false) } }) { Text("SRT") }
                OutlinedButton(onClick = { saveAs.launch("${s.title}.lrc", "text/plain") { vm.exportSubtitles(it, lrc = true) } }) { Text("LRC") }
            }
            Notice("AI-generated music: YouTube requires disclosing realistic altered or synthetic content. Monetisation and copyright clearance are not guaranteed; you are responsible for your lyrics and for the provider's terms.")
        }

        SectionCard("Cover artwork", subtitle = "Design covers are drawn on the phone from colours and your title (not an AI image model). Or use your own image.") {
            cover?.let { bmp ->
                Image(bmp.asImageBitmap(), "Cover preview", Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
            }
            if (imported == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt.palettes.forEachIndexed { i, colors ->
                        Box(
                            Modifier.size(if (palette == i) 34.dp else 28.dp).clip(CircleShape)
                                .background(Color(colors[1])).clickable { vm.setPalette(i) }
                        )
                    }
                    TextButton(onClick = vm::shuffleCover) { Text("Shuffle") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Use my image") }
                if (imported != null) TextButton(onClick = vm::clearImportedImage) { Text("Back to design") }
                OutlinedButton(onClick = { saveAs.launch("${s.title}-cover.png", "image/png") { vm.exportCover(it) } }) { Text("Export PNG") }
            }
        }

        SectionCard("Lyric video", subtitle = "Rendered on this phone with Media3. Cover + timed lyrics + audio visualizer.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoOrientation.entries.forEach { o -> FilterChip(selected = orientation == o, onClick = { orientation = o }, label = { Text(o.label) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Show lyrics", Modifier.weight(1f)); Switch(showLyrics, { showLyrics = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Audio visualizer", Modifier.weight(1f)); Switch(showVisualizer, { showVisualizer = it }) }
            val progress = videoProgress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rendering… $progress%", Modifier.weight(1f))
                    TextButton(onClick = vm::cancelVideo) { Text("Cancel") }
                }
            } else {
                Button(onClick = { vm.renderVideo(orientation, showLyrics, showVisualizer) }, enabled = wave.error == null && !wave.loading) { Text("Render video") }
            }
            videoFile?.let { file ->
                Text("Ready: ${file.name} (${file.length() / 1024} KB)", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { saveAs.launch("${s.title}-${orientation.name.lowercase()}.mp4", "video/mp4") { vm.exportVideo(it) } }) { Text("Export MP4") }
            }
            if (project == null && s.lyricsUsed.isBlank()) Text("No lyrics attached to this song, so the video shows the cover and visualizer only.", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Waveform(peaks: FloatArray, durationMs: Long, startMs: Long, endMs: Long, positionMs: Long) {
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.outlineVariant
    val head = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (peaks.isEmpty() || durationMs <= 0) return@Canvas
        val barW = size.width / peaks.size
        val mid = size.height / 2
        peaks.forEachIndexed { i, p ->
            val t = i.toFloat() / peaks.size * durationMs
            val color = if (t >= startMs && t <= endMs) primary else dim
            val h = (p * size.height * 0.9f).coerceAtLeast(2f)
            drawRoundRect(color, Offset(i * barW + barW * 0.15f, mid - h / 2), Size(barW * 0.7f, h), CornerRadius(barW / 2))
        }
        if (positionMs >= 0) {
            val x = positionMs.toFloat() / durationMs * size.width
            drawLine(head, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
        }
    }
}
