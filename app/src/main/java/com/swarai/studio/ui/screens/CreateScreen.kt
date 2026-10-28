package com.swarai.studio.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarai.studio.catalog.Catalog
import com.swarai.studio.engine.PromptBuilder
import com.swarai.studio.lyrics.LyricsTools
import com.swarai.studio.lyrics.SectionType
import com.swarai.studio.ui.Navigator
import com.swarai.studio.ui.Tab
import com.swarai.studio.ui.components.Badge
import com.swarai.studio.ui.components.Dropdown
import com.swarai.studio.ui.components.Notice
import com.swarai.studio.ui.components.SectionCard
import com.swarai.studio.ui.components.containerViewModel
import com.swarai.studio.ui.components.formatDuration
import com.swarai.studio.ui.components.formatUsd
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(navigator: Navigator, projectId: Long?, initialVoiceId: String?, instrumental: Boolean) {
    val vm = containerViewModel(key = "create-$projectId-$initialVoiceId-$instrumental") {
        CreateViewModel(it, projectId, initialVoiceId, instrumental)
    }
    val project by vm.project.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val revision by vm.lyricsRevision.collectAsStateWithLifecycle()

    var lyricsField by remember(revision) { mutableStateOf(TextFieldValue(project.lyrics)) }
    var showAssist by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var presetName by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importLyrics) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(vm::exportLyrics) }
    // Lets the "Generating song…" notification show on Android 13+. Generation works either way.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(Unit) { vm.refreshEngine() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Create song", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Badge(if (saved) "Saved" else "Saving…")
        }
        message?.let { Notice(it) }

        OutlinedTextField(
            value = project.title,
            onValueChange = { v -> vm.update { it.copy(title = v.take(120)) } },
            label = { Text("Song title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("title_field"),
        )

        // 1. Lyrics
        SectionCard("1 · Lyrics", subtitle = "Hindi, Bhojpuri, Maithili, English and more. Devanagari is fully supported.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Catalog.languages.forEach { lang ->
                    FilterChip(
                        selected = project.languageId == lang.id,
                        onClick = { vm.update { it.copy(languageId = lang.id) } },
                        label = { Text(if (lang.nativeLabel != lang.label) "${lang.label} · ${lang.nativeLabel}" else lang.label) },
                    )
                }
            }
            Catalog.language(project.languageId).note.takeIf { it.isNotEmpty() }?.let { Notice(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionType.entries.forEach { type ->
                    AssistChip(
                        onClick = {
                            val (text, cursor) = LyricsTools.insertSection(lyricsField.text, lyricsField.selection.start, type)
                            lyricsField = TextFieldValue(text, TextRange(cursor))
                            vm.setLyrics(text)
                        },
                        label = { Text("+ ${type.label}") },
                    )
                }
            }
            OutlinedTextField(
                value = lyricsField,
                onValueChange = { v ->
                    lyricsField = if (v.text.length > MAX_LYRICS) v.copy(text = v.text.take(MAX_LYRICS)) else v
                    vm.setLyrics(lyricsField.text)
                },
                label = { Text("Paste or type lyrics") },
                placeholder = { Text("[Verse]\nउगते सूरज के लाली…\n\n[Chorus]\n…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).testTag("lyrics_field"),
                enabled = !project.instrumental,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${lyricsField.text.length}/$MAX_LYRICS chars · ${LyricsTools.wordCount(lyricsField.text)} words · " +
                        "${LyricsTools.parseSections(lyricsField.text).size} sections",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { importLauncher.launch(arrayOf("text/plain")) }) { Icon(Icons.Rounded.FileUpload, null); Text(" Import") }
                TextButton(onClick = { exportLauncher.launch("${project.title.ifBlank { "lyrics" }}.txt") }) { Icon(Icons.Rounded.FileDownload, null); Text(" Export") }
                if (project.originalLyrics.isNotEmpty() && project.originalLyrics != project.lyrics) {
                    TextButton(onClick = vm::restoreOriginal) { Icon(Icons.Rounded.Restore, null); Text(" Original") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rhyme & pronunciation help", Modifier.weight(1f))
                Switch(checked = showAssist, onCheckedChange = { showAssist = it })
            }
            if (showAssist) LyricsAssist(lyricsField.text)
        }

        // 2. Style
        SectionCard("2 · Style", subtitle = "Styles guide the model; results are not guaranteed to match exactly.") {
            Catalog.genres.groupBy { it.group }.forEach { (group, genres) ->
                Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genres.forEach { g ->
                        FilterChip(
                            selected = project.genreId == g.id,
                            onClick = { vm.update { it.copy(genreId = g.id, bpm = it.bpm) } },
                            label = { Text(g.label) },
                        )
                    }
                }
            }
            Text("Mood", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Catalog.moods.forEach { m ->
                    FilterChip(selected = project.moodId == m.id, onClick = { vm.update { it.copy(moodId = m.id) } }, label = { Text(m.label) })
                }
            }
            LabeledSlider("Emotional intensity", project.intensity.toFloat(), 0f..100f, PromptBuilder.intensityWord(project.intensity)) { v ->
                vm.update { it.copy(intensity = v.roundToInt()) }
            }
        }

        // 3. Voice
        SectionCard("3 · Voice", subtitle = "Original voice descriptions, not copies of real singers.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Instrumental (no vocals)", Modifier.weight(1f))
                Switch(checked = project.instrumental, onCheckedChange = { v -> vm.update { it.copy(instrumental = v) } }, modifier = Modifier.testTag("instrumental_switch"))
            }
            if (!project.instrumental) {
                Dropdown(
                    label = "Voice profile",
                    items = Catalog.voices,
                    selected = Catalog.voice(project.voiceId),
                    itemLabel = { "${it.name} — ${it.presentation.label}, ${it.qualities.joinToString()}" },
                    onSelect = { v -> vm.update { it.copy(voiceId = v.id) } },
                )
                Text(Catalog.voice(project.voiceId).description, style = MaterialTheme.typography.bodySmall)
                LabeledSlider("Vocal / instrument balance", project.vocalBalance.toFloat(), 0f..100f,
                    PromptBuilder.balanceWord(project.vocalBalance).ifEmpty { "balanced mix" }) { v ->
                    vm.update { it.copy(vocalBalance = v.roundToInt()) }
                }
                TextButton(onClick = { navigator.tab(Tab.VOICES) }) { Text("Browse & preview voices") }
            }
        }

        // 4. Music controls
        SectionCard("4 · Music") {
            Text("Instruments", style = MaterialTheme.typography.labelMedium)
            val selected = project.instrumentIds.split(',').filter { it.isNotBlank() }.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Catalog.instruments.forEach { ins ->
                    FilterChip(
                        selected = ins.id in selected,
                        onClick = {
                            val next = if (ins.id in selected) selected - ins.id else selected + ins.id
                            vm.update { it.copy(instrumentIds = next.joinToString(",")) }
                        },
                        label = { Text(ins.label) },
                    )
                }
            }
            Text("Arrangement", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Catalog.arrangements.forEach { a ->
                    FilterChip(selected = project.arrangementId == a.id, onClick = { vm.update { it.copy(arrangementId = a.id) } }, label = { Text(a.label) })
                }
            }
            val e = engine
            val minD = e?.capabilities?.minDurationSec ?: 10
            val maxD = e?.capabilities?.maxDurationSec ?: 600
            LabeledSlider("Duration", project.durationSec.coerceIn(minD, maxD).toFloat(), minD.toFloat()..maxD.toFloat(),
                formatDuration(project.durationSec.coerceIn(minD, maxD) * 1000L)) { v ->
                vm.update { it.copy(durationSec = (v / 5).roundToInt() * 5) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set tempo (BPM)", Modifier.weight(1f))
                Switch(checked = project.bpm != null, onCheckedChange = { on ->
                    vm.update { it.copy(bpm = if (on) Catalog.genre(it.genreId).defaultBpm ?: 100 else null) }
                })
            }
            project.bpm?.let { bpm ->
                LabeledSlider("Tempo", bpm.toFloat(), 50f..200f, "$bpm BPM") { v -> vm.update { it.copy(bpm = v.roundToInt()) } }
            }
            Text("Candidates per generation", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..(e?.capabilities?.maxVariations ?: 4)).forEach { n ->
                    FilterChip(selected = project.variations == n, onClick = { vm.update { it.copy(variations = n) } }, label = { Text("$n") })
                }
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide advanced" else "Advanced prompt & key") }
            if (showAdvanced) {
                OutlinedTextField(
                    value = project.extraPrompt,
                    onValueChange = { v -> vm.update { it.copy(extraPrompt = v.take(300)) } },
                    label = { Text("Extra style words (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = project.keyScale.orEmpty(),
                    onValueChange = { v -> vm.update { it.copy(keyScale = v.filter { ch -> ch.isLetterOrDigit() || ch in " #b" }.take(12)) } },
                    label = { Text("Key (optional, e.g. C major, A minor)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Exact style text sent to the model:", style = MaterialTheme.typography.labelSmall)
                Text(PromptBuilder.caption(project), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }

        // Presets
        SectionCard("Presets", subtitle = "Save style, voice and music settings to reuse.") {
            presets.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.togglePresetFavorite(preset) }) {
                        Icon(if (preset.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorite preset")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(preset.name, fontWeight = FontWeight.Medium)
                        Text("${Catalog.genre(preset.genreId).label} · ${Catalog.voice(preset.voiceId).name}", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { vm.applyPreset(preset) }) { Text("Use") }
                    IconButton(onClick = { vm.deletePreset(preset) }) { Icon(Icons.Rounded.Delete, "Delete preset") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(presetName, { presetName = it.take(40) }, label = { Text("Preset name") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.savePreset(presetName); presetName = "" }) { Text("Save") }
            }
        }

        // 5. Generate
        SectionCard("5 · Generate") {
            val e = engine
            if (e == null) {
                Notice("No engine selected. Choose one in Settings.", warning = true)
                TextButton(onClick = { navigator.tab(Tab.SETTINGS) }) { Text("Open Settings") }
            } else {
                Text(e.name, fontWeight = FontWeight.SemiBold)
                Text("${e.kind.label} · ${e.model}", style = MaterialTheme.typography.bodySmall)
                Text("Your lyrics will be sent to: ${e.sendsDataTo}", style = MaterialTheme.typography.bodySmall)
                if (e.available) {
                    val spec = PromptBuilder.spec(project)
                    Text(
                        "Estimated cost: ${formatUsd(e.estimateUsd(spec))} for ${spec.variations} × ${formatDuration(e.clampDuration(spec.durationSec) * 1000L)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Notice(e.unavailableReason ?: "Unavailable", warning = true)
                }
                if (!e.isAiMusic) Notice("This is a diagnostic engine that returns a test tone, not music.", warning = true)
            }
            val running = job?.status == "QUEUED" || job?.status == "RUNNING"
            Button(
                onClick = { problem = vm.validate(); if (problem == null) confirm = true },
                enabled = !running,
                modifier = Modifier.fillMaxWidth().testTag("generate_button"),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Generating…" else "Generate song")
            }
            problem?.let { Notice(it, warning = true) }
            job?.let { j ->
                when (j.status) {
                    "QUEUED", "RUNNING" -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(j.progress.ifBlank { "Waiting…" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = vm::cancel) { Text("Cancel") }
                        }
                        Text("You can leave this screen; generation continues in the background.", style = MaterialTheme.typography.labelSmall)
                    }
                    "SUCCEEDED" -> Notice("Done! Your songs are saved in My Music.")
                    "FAILED" -> Notice(j.error ?: "Generation failed.", warning = true)
                    "CANCELLED" -> Notice("Cancelled. Some providers may still bill for work already started.")
                    else -> Unit
                }
            }
        }

        if (songs.isNotEmpty()) {
            SectionCard("Songs from this project") {
                songs.forEach { song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.play(song) }) { Icon(Icons.Rounded.PlayArrow, "Play") }
                        Column(Modifier.weight(1f)) {
                            Text(song.title)
                            Text("${formatDuration(song.durationMs)} · ${song.providerName}", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { navigator.studio(song.id) }) { Icon(Icons.Rounded.GraphicEq, "Open in Studio") }
                    }
                }
            }
        }
        Spacer(Modifier.padding(24.dp))
    }

    if (confirm) {
        val e = engine
        val spec = PromptBuilder.spec(project)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Generate with ${e?.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your lyrics and style text will be sent to ${e?.sendsDataTo}. The song title stays on this phone.")
                    if (e != null && !e.pricing.isFree) {
                        Text("Estimated cost: ${formatUsd(e.estimateUsd(spec))}. ${e.pricing.notes}", fontWeight = FontWeight.SemiBold)
                        Text("Charges are made by the provider to the account whose key is configured. Cancelling may not refund work already started.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("No per-song charge for this engine.")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { confirm = false; askForNotifications(); vm.generate() }, modifier = Modifier.testTag("confirm_generate")) {
                    Icon(Icons.Rounded.Check, null); Text(" Generate")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun LyricsAssist(lyrics: String) {
    val sections = LyricsTools.parseSections(lyrics)
    if (sections.isEmpty()) {
        Text("Add lyrics to see rhyme patterns and a romanised reading guide.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Notice("Letters show which line endings rhyme within a section (offline, rule-based). The grey line is a romanised reading guide for Devanagari.")
    sections.forEach { section ->
        Text(section.header.ifBlank { "Lyrics" }, style = MaterialTheme.typography.labelLarge)
        val scheme = LyricsTools.rhymeScheme(section.lines)
        section.lines.forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            Row {
                Text(scheme[i].ifEmpty { "·" }, Modifier.width(20.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Column {
                    Text(line, style = MaterialTheme.typography.bodySmall)
                    if (line.any { it in 'ऀ'..'ॿ' }) {
                        Text(LyricsTools.transliterate(line), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
