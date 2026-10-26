package com.swarai.studio.ui.components

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Launches the system "save as" picker (Storage Access Framework) and runs [action] on the chosen location. */
class SaveAs internal constructor(
    private val scope: CoroutineScope,
    private val launchers: Map<String, ManagedActivityResultLauncher<String, Uri?>>,
    private val pending: androidx.compose.runtime.MutableState<(suspend (Uri) -> Unit)?>,
    private val onError: (String) -> Unit,
) {
    fun launch(fileName: String, mime: String, action: suspend (Uri) -> Unit) {
        val launcher = launchers[mime] ?: launchers.getValue("application/octet-stream")
        pending.value = action
        launcher.launch(fileName)
    }

    internal fun deliver(uri: Uri?) {
        val action = pending.value ?: return
        pending.value = null
        if (uri == null) return
        scope.launch { runCatching { action(uri) }.onFailure { onError(it.message ?: "Export failed") } }
    }

    companion object {
        val MIMES = listOf(
            "audio/mpeg", "audio/wav", "audio/mp4", "audio/flac", "audio/ogg",
            "video/mp4", "image/png", "text/plain", "application/json", "application/octet-stream",
        )

        fun mimeForAudio(format: String) = when (format.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }
}

@Composable
fun rememberSaveAs(onError: (String) -> Unit): SaveAs {
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<(suspend (Uri) -> Unit)?>(null) }
    val holder = remember { arrayOfNulls<SaveAs>(1) }
    val launchers = SaveAs.MIMES.associateWith { mime ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri -> holder[0]?.deliver(uri) }
    }
    val saveAs = remember(launchers) { SaveAs(scope, launchers, pending, onError) }
    holder[0] = saveAs
    return saveAs
}
