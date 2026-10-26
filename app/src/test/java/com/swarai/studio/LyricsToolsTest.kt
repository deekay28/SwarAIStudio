package com.swarai.studio

import com.swarai.studio.lyrics.LyricsTools
import com.swarai.studio.lyrics.SectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsToolsTest {
    private val bhojpuri = """
        [Mukhda]
        कांच ही बांस के बहंगिया
        बहंगी लचकत जाए

        Verse 1:
        होई ना बलम जी कहरिया
        बहंगी घाटे पहुंचाए
    """.trimIndent()

    @Test fun parsesHindiAndEnglishSectionHeaders() {
        val sections = LyricsTools.parseSections(bhojpuri)
        assertEquals(2, sections.size)
        assertEquals(SectionType.CHORUS, sections[0].type)
        assertEquals(SectionType.VERSE, sections[1].type)
        assertEquals("कांच ही बांस के बहंगिया", sections[0].lines[0])
    }

    @Test fun modelFormatRewritesOnlyHeaders() {
        val out = LyricsTools.toModelFormat(bhojpuri)
        assertTrue(out.startsWith("[chorus]\nकांच ही बांस के बहंगिया"))
        assertTrue(out.contains("[verse 1]"))
        assertTrue(out.contains("बहंगी घाटे पहुंचाए"))
    }

    @Test fun devanagariIsPreservedThroughNormalisation() {
        val decomposed = "\u0915\u093C" // क + nukta
        val text = LyricsTools.normalize("[verse]\n$decomposed")
        assertTrue(text.contains("\u0958") || text.contains(decomposed))
        assertEquals(4, LyricsTools.singableLines(bhojpuri).size)
    }

    @Test fun insertSectionAddsSpacingAndMovesCursor() {
        val (text, cursor) = LyricsTools.insertSection("line one", 8, SectionType.CHORUS)
        assertEquals("line one\n\n[Chorus]\n", text)
        assertEquals(text.length, cursor)
    }

    @Test fun rhymeSchemeFindsHindiRhymes() {
        val lines = listOf("मन में दीप जलाए", "सपनों को सजाए", "कोई और बात", "चलो चलें साथ")
        val scheme = LyricsTools.rhymeScheme(lines)
        assertEquals(scheme[0], scheme[1])
        assertNotEquals("", scheme[0])
        assertEquals(scheme[2], scheme[3])
        assertNotEquals(scheme[0], scheme[2])
    }

    @Test fun rhymeSchemeEnglish() {
        val scheme = LyricsTools.rhymeScheme(listOf("I see the light", "into the night", "random words here"))
        assertEquals("A", scheme[0]); assertEquals("A", scheme[1]); assertEquals("", scheme[2])
    }

    @Test fun transliterationIsReadable() {
        assertEquals("namaste", LyricsTools.transliterate("नमस्ते"))
        assertEquals("jay ho chhathee maiyaa", LyricsTools.transliterate("जय हो छठी मैया").replace("ai", "ai"))
        assertEquals("ghar", LyricsTools.transliterate("घर"))
        assertEquals("Hello", LyricsTools.transliterate("Hello"))
    }
}
