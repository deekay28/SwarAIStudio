package com.swarai.studio

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Types Hindi, Bhojpuri and English lyrics into the real editor and checks the autosaved project. */
@RunWith(AndroidJUnit4::class)
class LyricsPersistenceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val hindi = "[Verse]\nतुम ही हो, अब तुम ही हो"
    private val bhojpuri = "[Chorus]\nकांच ही बांस के बहंगिया, बहंगी लचकत जाए"
    private val english = "[Bridge]\nUnder the evening sky"

    @Test fun autosavesMultilingualLyrics() {
        val title = "Test song ${System.currentTimeMillis()}"
        rule.onNodeWithTag("tab_create").performClick()
        rule.onNodeWithTag("title_field").performTextInput(title)
        rule.onNodeWithTag("lyrics_field").performTextInput("$hindi\n\n$bhojpuri\n\n$english")
        val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SwarApp).container
        var saved = false
        val deadline = System.currentTimeMillis() + 10_000
        while (!saved && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            saved = runBlocking {
                container.db.projects().observeAll().first().any { p ->
                    p.title == title && p.lyrics.contains("तुम ही हो") && p.lyrics.contains("बहंगी लचकत") && p.lyrics.contains("evening sky")
                }
            }
        }
        assertTrue("Project with multilingual lyrics was not autosaved", saved)
    }
}
