package com.swarai.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.swarai.studio.AppContainer
import com.swarai.studio.BuildConfig
import com.swarai.studio.data.AppSettings
import com.swarai.studio.data.SecretStore
import com.swarai.studio.engine.AceStepCompanionEngine
import com.swarai.studio.engine.BackendEngine
import com.swarai.studio.engine.BackendProvider
import com.swarai.studio.engine.DeepInfraDirectEngine
import com.swarai.studio.engine.EngineInfo
import com.swarai.studio.engine.NetworkPolicy
import com.swarai.studio.ui.components.Dropdown
import com.swarai.studio.ui.components.Notice
import com.swarai.studio.ui.components.SectionCard
import com.swarai.studio.ui.components.containerViewModel
import com.swarai.studio.ui.components.formatUsd
import com.swarai.studio.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val engines = MutableStateFlow<List<EngineInfo>>(emptyList())
    val testResult = MutableStateFlow<Pair<String, String>?>(null)
    val backendProviders = MutableStateFlow<List<BackendProvider>>(emptyList())
    val hasBackendToken = MutableStateFlow(c.secrets.has(SecretStore.BACKEND_TOKEN))
    val hasDeepInfraKey = MutableStateFlow(c.secrets.has(SecretStore.DEEPINFRA_KEY))
    val hasCompanionKey = MutableStateFlow(c.secrets.has(SecretStore.COMPANION_KEY))

    init { refresh() }

    fun refresh() = viewModelScope.launch { engines.value = c.engines.all().map { it.describe() } }

    fun select(id: String) = viewModelScope.launch { c.settings.setEngine(id); refresh() }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }

    fun saveBackend(url: String, token: String) = viewModelScope.launch {
        val error = if (url.isBlank()) null else runCatching { NetworkPolicy.validateBaseUrl(url, allowLocalHttp = true) }.exceptionOrNull()
        if (error != null) { testResult.value = BackendEngine.ID to (error.message ?: "Invalid address"); return@launch }
        c.settings.setBackendUrl(url)
        if (token.isNotBlank()) c.secrets.put(SecretStore.BACKEND_TOKEN, token)
        hasBackendToken.value = c.secrets.has(SecretStore.BACKEND_TOKEN)
        refresh()
    }

    fun setBackendProvider(id: String) = viewModelScope.launch { c.settings.setBackendProvider(id); refresh() }

    fun saveDeepInfra(key: String) = viewModelScope.launch {
        c.secrets.put(SecretStore.DEEPINFRA_KEY, key)
        hasDeepInfraKey.value = c.secrets.has(SecretStore.DEEPINFRA_KEY)
        refresh()
    }

    fun saveCompanion(url: String, key: String) = viewModelScope.launch {
        val error = if (url.isBlank()) null else runCatching { NetworkPolicy.validateBaseUrl(url, allowLocalHttp = true) }.exceptionOrNull()
        if (error != null) { testResult.value = AceStepCompanionEngine.ID to (error.message ?: "Invalid address"); return@launch }
        c.settings.setCompanionUrl(url)
        if (key.isNotBlank()) c.secrets.put(SecretStore.COMPANION_KEY, key)
        hasCompanionKey.value = c.secrets.has(SecretStore.COMPANION_KEY)
        refresh()
    }

    fun forget(name: String) = viewModelScope.launch {
        c.secrets.put(name, "")
        hasBackendToken.value = c.secrets.has(SecretStore.BACKEND_TOKEN)
        hasDeepInfraKey.value = c.secrets.has(SecretStore.DEEPINFRA_KEY)
        hasCompanionKey.value = c.secrets.has(SecretStore.COMPANION_KEY)
        refresh()
    }

    fun test(id: String) = viewModelScope.launch {
        testResult.value = id to "Testing…"
        val engine = c.engines.engine(id)
        val result = runCatching { engine.testConnection() }
        testResult.value = id to result.getOrElse { "Failed: ${it.message}" }
        if (engine is BackendEngine) backendProviders.value = runCatching { engine.providers() }.getOrDefault(emptyList())
        refresh()
    }
}

@Composable
fun SettingsScreen() {
    val vm = containerViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engines by vm.engines.collectAsStateWithLifecycle()
    val test by vm.testResult.collectAsStateWithLifecycle()
    val providers by vm.backendProviders.collectAsStateWithLifecycle()
    val hasToken by vm.hasBackendToken.collectAsStateWithLifecycle()
    val hasDeepInfra by vm.hasDeepInfraKey.collectAsStateWithLifecycle()
    val hasCompanionKey by vm.hasCompanionKey.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Music engine", subtitle = "Choose where songs are generated. Nothing is sent anywhere until you press Generate.") {
            engines.forEach { e ->
                Row(verticalAlignment = Alignment.Top) {
                    RadioButton(selected = settings.engineId == e.id, onClick = { vm.select(e.id) }, modifier = Modifier.testTag("engine_${e.id}"))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontWeight = FontWeight.SemiBold)
                        Text(e.kind.label + if (e.worksWithoutInternet) " · works without internet" else " · needs internet", style = MaterialTheme.typography.labelSmall)
                        Text("Sends lyrics to: ${e.sendsDataTo}", style = MaterialTheme.typography.bodySmall)
                        if (e.available) {
                            Text("Cost: ${if (e.pricing.isFree) "no per-song fee" else "${formatUsd(e.pricing.usdPerSecond * 60)} per minute of audio"} · ${e.pricing.notes}", style = MaterialTheme.typography.bodySmall)
                            if (e.capabilities.languagesNote.isNotBlank()) Text("Languages: ${e.capabilities.languagesNote}", style = MaterialTheme.typography.bodySmall)
                            if (e.license.isNotBlank()) Text("Licence: ${e.license}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(e.unavailableReason ?: "Not available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        SectionCard("SwarAI server (recommended)", subtitle = "Your own backend keeps provider API keys off the phone. See docs/PROVIDER_SETUP.md.") {
            var url by remember(settings.backendUrl) { mutableStateOf(settings.backendUrl) }
            var token by remember { mutableStateOf("") }
            OutlinedTextField(url, { url = it }, label = { Text("Server address (https://…)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text(if (hasToken) "Access token (saved — type to replace)" else "Access token") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveBackend(url, token); token = "" }) { Text("Save") }
                OutlinedButton(onClick = { vm.test(BackendEngine.ID) }) { Text("Test") }
                if (hasToken) OutlinedButton(onClick = { vm.forget(SecretStore.BACKEND_TOKEN) }) { Text("Forget token") }
            }
            if (providers.size > 1) {
                Dropdown("Provider on the server", providers, providers.firstOrNull { it.id == settings.backendProviderId } ?: providers.first(),
                    { it.name }, { vm.setBackendProvider(it.id) })
            }
            TestResult(test, BackendEngine.ID)
        }

        SectionCard("DeepInfra — your own key", subtitle = "Simplest setup: no server needed. ACE-Step 1.5 XL, about \$0.18 per 3-minute song, billed by DeepInfra.") {
            var key by remember { mutableStateOf("") }
            Notice("Your key is encrypted with this phone's Keystore and is only sent to api.deepinfra.com. Anyone with your unlocked phone could use it, so set a spending limit in your DeepInfra account.", warning = true)
            OutlinedTextField(key, { key = it }, label = { Text(if (hasDeepInfra) "API key (saved — type to replace)" else "DeepInfra API key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveDeepInfra(key); key = "" }, enabled = key.isNotBlank()) { Text("Save") }
                OutlinedButton(onClick = { vm.test(DeepInfraDirectEngine.ID) }) { Text("Test (free)") }
                if (hasDeepInfra) OutlinedButton(onClick = { vm.forget(SecretStore.DEEPINFRA_KEY) }) { Text("Forget key") }
            }
            TestResult(test, DeepInfraDirectEngine.ID)
        }

        SectionCard("Your computer (offline over Wi-Fi)", subtitle = "Run the official ACE-Step 1.5 API server on a PC with a GPU, on the same Wi-Fi. Start it with: uv run acestep-api --host 0.0.0.0") {
            var url by remember(settings.companionUrl) { mutableStateOf(settings.companionUrl) }
            var key by remember { mutableStateOf("") }
            OutlinedTextField(url, { url = it }, label = { Text("Computer address, e.g. http://192.168.1.20:8001") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text(if (hasCompanionKey) "API key (saved)" else "API key (optional, ACESTEP_API_KEY)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveCompanion(url, key); key = "" }) { Text("Save") }
                OutlinedButton(onClick = { vm.test(AceStepCompanionEngine.ID) }) { Text("Test") }
            }
            Notice("Plain http:// is allowed only for private network addresses (192.168.x.x, 10.x.x.x, 172.16–31.x.x, Tailscale 100.64–127.x.x, *.local).")
            TestResult(test, AceStepCompanionEngine.ID)
        }

        SectionCard("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = settings.themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }

        SectionCard("Privacy & data") {
            Text(
                "• Lyrics, projects and songs are stored only on this phone.\n" +
                    "• Lyrics and style text are sent only when you press Generate, and only to the engine shown.\n" +
                    "• The SwarAI server deletes generated audio after you download it (or after 1 hour) and does not log lyrics.\n" +
                    "• Hosted providers have their own data policies (DeepInfra, fal.ai).\n" +
                    "• No analytics, ads or tracking. Backups are disabled so keys are never copied to the cloud.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("About") {
            Text("SwarAI Studio ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
            Text(
                "Music generation uses ACE-Step 1.5 (MIT licence) through the engine you choose. AI-generated songs may resemble existing works by chance; " +
                    "you are responsible for how you publish them. YouTube monetisation and copyright clearance are not guaranteed.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TestResult(result: Pair<String, String>?, id: String) {
    if (result?.first == id) Notice(result.second, warning = result.second.startsWith("Failed") || result.second.contains("must"))
}
