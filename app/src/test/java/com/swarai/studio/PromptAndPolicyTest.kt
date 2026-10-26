package com.swarai.studio

import com.swarai.studio.catalog.Catalog
import com.swarai.studio.data.db.ProjectEntity
import com.swarai.studio.engine.EngineException
import com.swarai.studio.engine.NetworkPolicy
import com.swarai.studio.engine.PromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAndPolicyTest {
    @Test fun captionCombinesSelections() {
        val p = ProjectEntity(title = "Chhath", genreId = "chhath_puja", voiceId = "folk_female_warm", languageId = "bho",
            instrumentIds = "dholak,shehnai", moodId = "devotional")
        val caption = PromptBuilder.caption(p)
        assertTrue(caption.contains("Chhath Puja"))
        assertTrue(caption.contains("female vocal"))
        assertTrue(caption.contains("Bhojpuri lyrics"))
        assertTrue(caption.contains("dholak, shehnai"))
        val spec = PromptBuilder.spec(p.copy(lyrics = "Mukhda:\nजय छठी मईया"))
        assertEquals("hi", spec.languageCode) // Bhojpuri is sung with the Hindi code
        assertEquals("[chorus]\nजय छठी मईया", spec.lyrics)
    }

    @Test fun instrumentalDropsVoiceAndLyrics() {
        val p = ProjectEntity(title = "x", instrumental = true, lyrics = "ignored")
        assertFalse(PromptBuilder.caption(p).contains("vocal,"))
        assertEquals("", PromptBuilder.spec(p).lyrics)
    }

    @Test fun catalogHasRequiredStylesAndNoSingerNames() {
        val ids = Catalog.genres.map { it.id }
        listOf("bhojpuri_folk", "bhojpuri_devotional", "chhath_puja", "bhajan", "ghazal", "qawwali", "sufi", "maithili_folk",
            "pop", "rock", "rap", "hiphop", "edm", "lofi", "acoustic", "orchestral", "cinematic", "meditation", "children", "wedding")
            .forEach { assertTrue("missing $it", it in ids) }
        assertFalse(Catalog.genres.any { it.tags.contains("style of", ignoreCase = true) || it.tags.contains("sinha", ignoreCase = true) })
    }

    @Test fun networkPolicyAllowsHttpsAndLanOnly() {
        assertEquals("https://api.example.com", NetworkPolicy.validateBaseUrl("https://api.example.com/", allowLocalHttp = true))
        assertEquals("http://192.168.1.20:8001", NetworkPolicy.validateBaseUrl("http://192.168.1.20:8001", allowLocalHttp = true))
        assertTrue(NetworkPolicy.isLocalHost("100.101.1.2"))
        assertTrue(NetworkPolicy.isLocalHost("studio-pc.local"))
        assertFalse(NetworkPolicy.isLocalHost("8.8.8.8"))
        assertFalse(NetworkPolicy.isLocalHost("172.32.0.1"))
        assertThrows(EngineException::class.java) { NetworkPolicy.validateBaseUrl("http://example.com", allowLocalHttp = true) }
        assertThrows(EngineException::class.java) { NetworkPolicy.validateBaseUrl("http://192.168.1.2", allowLocalHttp = false) }
        assertThrows(EngineException::class.java) { NetworkPolicy.validateBaseUrl("ftp://x.y", allowLocalHttp = true) }
    }
}
