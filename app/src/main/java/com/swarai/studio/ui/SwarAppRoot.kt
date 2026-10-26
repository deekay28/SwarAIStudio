package com.swarai.studio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swarai.studio.AppContainer
import com.swarai.studio.player.PlayerController
import com.swarai.studio.ui.components.formatDuration
import com.swarai.studio.ui.screens.CreateScreen
import com.swarai.studio.ui.screens.HomeScreen
import com.swarai.studio.ui.screens.MusicScreen
import com.swarai.studio.ui.screens.SettingsScreen
import com.swarai.studio.ui.screens.StudioScreen
import com.swarai.studio.ui.screens.VoicesScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Rounded.Home),
    CREATE("create", "Create", Icons.Rounded.AddCircle),
    MUSIC("music", "My Music", Icons.Rounded.LibraryMusic),
    STUDIO("studio", "Studio", Icons.Rounded.GraphicEq),
    VOICES("voices", "Voices", Icons.Rounded.RecordVoiceOver),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}

class Navigator(private val nav: NavHostController) {
    fun tab(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun createProject(projectId: Long?, voiceId: String? = null, instrumental: Boolean = false) {
        val args = buildList {
            if (projectId != null) add("projectId=$projectId")
            if (voiceId != null) add("voiceId=$voiceId")
            if (instrumental) add("instrumental=true")
        }.joinToString("&")
        nav.navigate("create" + if (args.isNotEmpty()) "?$args" else "") { launchSingleTop = true }
    }

    fun studio(songId: Long) = nav.navigate("studio?songId=$songId") { launchSingleTop = true }
}

@Composable
fun SwarAppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val navigator = Navigator(nav)
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore('?')
    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(container.player)
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigator.tab(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("tab_${tab.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
            composable(Tab.HOME.route) { HomeScreen(navigator) }
            composable(
                "create?projectId={projectId}&voiceId={voiceId}&instrumental={instrumental}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("voiceId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("instrumental") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val args = entry.arguments
                CreateScreen(
                    navigator = navigator,
                    projectId = args?.getLong("projectId")?.takeIf { it > 0 },
                    initialVoiceId = args?.getString("voiceId"),
                    instrumental = args?.getBoolean("instrumental") ?: false,
                )
            }
            composable(Tab.MUSIC.route) { MusicScreen(navigator) }
            composable(
                "studio?songId={songId}",
                arguments = listOf(navArgument("songId") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                StudioScreen(navigator, songId = entry.arguments?.getLong("songId")?.takeIf { it > 0 })
            }
            composable(Tab.VOICES.route) { VoicesScreen(navigator) }
            composable(Tab.SETTINGS.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun MiniPlayer(player: PlayerController) {
    val state by player.state.collectAsStateWithLifecycle()
    if (state.songId == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = player::toggle, modifier = Modifier.testTag("mini_play")) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play")
                }
                Column(Modifier.weight(1f)) {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = player::stop) { Icon(Icons.Rounded.Close, "Close player") }
            }
            if (state.durationMs > 0) {
                Slider(
                    value = state.positionMs.toFloat().coerceIn(0f, state.durationMs.toFloat()),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..state.durationMs.toFloat(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
