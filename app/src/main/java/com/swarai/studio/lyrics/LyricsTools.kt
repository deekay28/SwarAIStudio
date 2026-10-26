package com.swarai.studio.lyrics

import java.text.Normalizer

enum class SectionType(val tag: String, val label: String) {
    INTRO("intro", "Intro"),
    VERSE("verse", "Verse"),
    PRE_CHORUS("pre-chorus", "Pre-chorus"),
    CHORUS("chorus", "Chorus"),
    BRIDGE("bridge", "Bridge"),
    INSTRUMENTAL("inst", "Instrumental break"),
    OUTRO("outro", "Outro"),
}

data class LyricSection(val type: SectionType?, val header: String, val lines: List<String>)

object LyricsTools {
    private val headerRegex = Regex("""^\s*[\[(]\s*([^\])]+?)\s*[\])]\s*:?\s*$""")
    private val bareHeaderRegex = Regex("""^\s*(intro|verse|pre-?chorus|chorus|hook|bridge|outro|antara|mukhda|sthayi)\s*\d*\s*:\s*$""", RegexOption.IGNORE_CASE)

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n')

    fun sectionTypeOf(name: String): SectionType? {
        val n = name.lowercase().replace(Regex("""\s*\d+$"""), "").trim()
        return when (n) {
            "intro" -> SectionType.INTRO
            "verse", "antara", "अंतरा" -> SectionType.VERSE
            "pre-chorus", "prechorus", "pre chorus" -> SectionType.PRE_CHORUS
            "chorus", "hook", "mukhda", "sthayi", "मुखड़ा", "स्थायी" -> SectionType.CHORUS
            "bridge" -> SectionType.BRIDGE
            "inst", "instrumental", "interlude", "break" -> SectionType.INSTRUMENTAL
            "outro", "end" -> SectionType.OUTRO
            else -> null
        }
    }

    private fun headerName(line: String): String? =
        headerRegex.matchEntire(line)?.groupValues?.get(1)
            ?: bareHeaderRegex.matchEntire(line)?.let { line.trim().trimEnd(':').trim() }

    fun parseSections(lyrics: String): List<LyricSection> {
        val sections = mutableListOf<LyricSection>()
        var header = ""
        var type: SectionType? = null
        var lines = mutableListOf<String>()
        fun flush() {
            if (header.isNotEmpty() || lines.any { it.isNotBlank() }) {
                sections += LyricSection(type, header, lines.dropLastWhile { it.isBlank() })
            }
        }
        for (raw in normalize(lyrics).lines()) {
            val name = headerName(raw)
            if (name != null) {
                flush()
                header = name
                type = sectionTypeOf(name)
                lines = mutableListOf()
            } else if (raw.isNotBlank() || lines.isNotEmpty()) {
                lines += raw.trimEnd()
            }
        }
        flush()
        return sections
    }

    /** Rewrites section headers into the [verse]/[chorus] form ACE-Step understands. Lyric lines are untouched. */
    fun toModelFormat(lyrics: String): String = normalize(lyrics).lines().joinToString("\n") { line ->
        val name = headerName(line)
        if (name == null) line.trimEnd() else {
            val type = sectionTypeOf(name)
            val number = Regex("""(\d+)$""").find(name.trim())?.value
            if (type != null) "[${type.tag}${number?.let { " $it" } ?: ""}]" else "[${name.trim()}]"
        }
    }.trim()

    fun insertSection(lyrics: String, cursor: Int, type: SectionType): Pair<String, Int> {
        val pos = cursor.coerceIn(0, lyrics.length)
        val before = lyrics.substring(0, pos)
        val prefix = if (before.isEmpty() || before.endsWith("\n\n")) "" else if (before.endsWith("\n")) "\n" else "\n\n"
        val insert = "$prefix[${type.label}]\n"
        return (before + insert + lyrics.substring(pos)) to (pos + insert.length)
    }

    fun singableLines(lyrics: String): List<String> =
        parseSections(lyrics).filter { it.type != SectionType.INSTRUMENTAL }.flatMap { it.lines }.filter { it.isNotBlank() }

    fun wordCount(lyrics: String): Int = singableLines(lyrics).sumOf { line -> line.split(Regex("""\s+""")).count { it.isNotBlank() } }

    // ---- Rhyme assistance ----

    private const val VIRAMA = '्'
    private val trailingPunct = Regex("""[\s\p{Punct}।॥।॥]+$""")

    private fun isDevanagari(ch: Char) = ch in 'ऀ'..'ॿ'
    private fun isDevConsonant(ch: Char) = ch in 'क'..'ह' || ch in 'क़'..'य़'
    private fun isDevVowelSign(ch: Char) = ch in 'ा'..'ौ' || ch == 'ॢ' || ch == 'ॣ'

    /** A comparable key for the end of a line: the final syllable sound for Devanagari, final vowel+coda for Latin. */
    fun rhymeKey(line: String): String? {
        val word = line.replace(trailingPunct, "").split(Regex("""\s+""")).lastOrNull()?.lowercase() ?: return null
        if (word.isEmpty()) return null
        return if (word.any(::isDevanagari)) devanagariRhymeKey(word) else latinRhymeKey(word)
    }

    private fun devanagariRhymeKey(word: String): String {
        // Strip nasalisation marks, which rhyme loosely in folk lyrics.
        val w = word.filterNot { it == 'ं' || it == 'ँ' || it == '़' }
        var i = w.length - 1
        val vowel = StringBuilder()
        while (i >= 0 && isDevVowelSign(w[i])) { vowel.insert(0, w[i]); i-- }
        if (i >= 0 && isDevConsonant(w[i])) {
            // Final consonant with its vowel sign; independent vowels stand alone.
            return w[i].toString() + vowel
        }
        return w.takeLast(2)
    }

    private fun latinRhymeKey(word: String): String {
        val letters = word.filter { it.isLetter() }
        if (letters.isEmpty()) return word
        val vowels = "aeiouy"
        var i = letters.length - 1
        while (i >= 0 && letters[i] !in vowels) i--
        while (i > 0 && letters[i - 1] in vowels) i--
        return if (i < 0) letters.takeLast(2) else letters.substring(i)
    }

    /** Returns a rhyme letter (A, B, C...) per sung line in each section, or "" when it doesn't rhyme with anything. */
    fun rhymeScheme(lines: List<String>): List<String> {
        val keys = lines.map { rhymeKey(it) }
        val counts = keys.filterNotNull().groupingBy { it }.eachCount()
        val letters = mutableMapOf<String, Char>()
        var next = 'A'
        return keys.map { key ->
            if (key == null || (counts[key] ?: 0) < 2) "" else letters.getOrPut(key) { next++ }.toString()
        }
    }

    // ---- Pronunciation assistance (Devanagari -> readable Latin) ----

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "gh", '\u095B' to "z", '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y",
    )
    private val nuktaForms = mapOf('क' to '\u0958', 'ख' to '\u0959', 'ग' to '\u095A', 'ज' to '\u095B', 'ड' to '\u095C', 'ढ' to '\u095D', 'फ' to '\u095E')
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
    )
    private val vowelSigns = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o",
    )

    fun transliterate(text: String): String {
        val src = Normalizer.normalize(text, Normalizer.Form.NFC)
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            var ch = src[i]
            if (i + 1 < src.length && src[i + 1] == '़') {
                nuktaForms[ch]?.let { ch = it; i++ }
            }
            val cons = consonants[ch]
            when {
                cons != null -> {
                    out.append(cons)
                    val next = src.getOrNull(i + 1)
                    when {
                        next == VIRAMA -> i++
                        next != null && vowelSigns.containsKey(next) -> { out.append(vowelSigns[next]); i++ }
                        else -> {
                            // Schwa deletion: drop the inherent 'a' at the end of a word.
                            val endOfWord = next == null || !isDevanagari(next) ||
                                (next == 'ं' && src.getOrNull(i + 2)?.let { !isDevanagari(it) } != false)
                            if (!endOfWord || out.length <= cons.length) out.append('a')
                        }
                    }
                }
                vowels.containsKey(ch) -> out.append(vowels[ch])
                ch == 'ं' || ch == 'ँ' -> out.append('n')
                ch == 'ः' -> out.append('h')
                ch == '।' || ch == '॥' -> out.append('.')
                ch in '०'..'९' -> out.append('0' + (ch - '०'))
                isDevanagari(ch) -> Unit
                else -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }
}
