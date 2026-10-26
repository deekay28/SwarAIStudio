package com.swarai.studio.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val songId: Long? = null,
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

/** Plays app-private files only; no network streaming. */
class PlayerController(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var ticker: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startTicker() else update()
            }

            override fun onPlaybackStateChanged(playbackState: Int) = update()

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.value = _state.value.copy(isPlaying = false, error = "Can't play this file: ${error.errorCodeName}")
            }
        })
    }

    fun play(songId: Long, title: String, file: File, startMs: Long = 0) {
        if (_state.value.songId == songId && player.mediaItemCount > 0) {
            if (startMs > 0) player.seekTo(startMs)
            player.play()
            return
        }
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        player.setMediaItem(item, startMs)
        player.prepare()
        player.play()
        _state.value = PlaybackState(songId = songId, title = title, isPlaying = true)
    }

    fun toggle() = if (player.isPlaying) player.pause() else player.play()
    fun pause() = player.pause()
    fun seekTo(ms: Long) { player.seekTo(ms); update() }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _state.value = PlaybackState()
    }

    /** Called before a file is deleted or replaced. */
    fun releaseIfPlaying(songId: Long) { if (_state.value.songId == songId) stop() }

    private fun update() {
        _state.value = _state.value.copy(
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: _state.value.durationMs,
        )
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && player.isPlaying) {
                update()
                delay(200)
            }
        }
    }
}
