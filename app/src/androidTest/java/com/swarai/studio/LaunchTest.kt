package com.swarai.studio

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Evidence that the APK installs, launches and every main tab opens. */
@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launchesAndOpensEveryTab() {
        rule.onNodeWithText("SwarAI Studio").assertExists()
        listOf("create" to "Create song", "music" to "My Music", "studio" to "Studio", "voices" to "Voices", "settings" to "Settings", "home" to "Recent projects")
            .forEach { (tab, text) ->
                rule.onNodeWithTag("tab_$tab").performClick()
                rule.waitForIdle()
                rule.waitUntil(5000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
            }
    }

    @Test fun settingsListsAllEnginesHonestly() {
        rule.onNodeWithTag("tab_settings").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("On-device generation").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("No music model that sings full songs can currently run on Android phones.", substring = true).assertExists()
    }
}
