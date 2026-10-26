package com.swarai.studio.catalog

/**
 * Everything the user can pick is translated into plain-language style tags for the model.
 * Models follow these tags on a best-effort basis; nothing here guarantees a particular result.
 */
data class Genre(val id: String, val label: String, val group: String, val tags: String, val defaultBpm: Int? = null)

data class Mood(val id: String, val label: String, val tags: String)

data class Instrument(val id: String, val label: String, val tag: String)

data class Arrangement(val id: String, val label: String, val tags: String)

data class Language(
    val id: String,
    val label: String,
    val nativeLabel: String,
    /** Code sent to the model. Languages the model was not trained on are sung with the closest supported code. */
    val modelCode: String,
    val note: String = "",
)

enum class VocalPresentation(val label: String, val tag: String) {
    FEMALE("Female", "female vocal"),
    MALE("Male", "male vocal"),
    ANDROGYNOUS("Androgynous", "androgynous vocal"),
    CHILD_LIKE("Youthful", "bright youthful vocal"),
    DUET("Duet", "male and female duet vocals"),
    GROUP("Group / chorus", "group choir vocals"),
}

data class VoiceProfile(
    val id: String,
    val name: String,
    val presentation: VocalPresentation,
    val register: String,
    val qualities: List<String>,
    val delivery: String,
    val technique: String,
    val description: String,
) {
    val tags: String
        get() = listOf(presentation.tag, register, qualities.joinToString(" "), "$delivery singing", technique)
            .filter { it.isNotBlank() }
            .joinToString(", ")
}

object Catalog {
    val genres = listOf(
        Genre("bhojpuri_folk", "Bhojpuri folk", "Regional & devotional", "Bhojpuri folk song, dholak, harmonium, nagada, rustic, Purvanchal folk", 100),
        Genre("bhojpuri_devotional", "Bhojpuri devotional", "Regional & devotional", "Bhojpuri devotional bhakti song, harmonium, dholak, manjira, temple bells", 90),
        Genre("chhath_puja", "Chhath Puja geet", "Regional & devotional", "Chhath Puja folk devotional song, Bihar folk tradition, harmonium, dholak, shehnai, sunrise ghat atmosphere", 80),
        Genre("maithili_folk", "Maithili folk", "Regional & devotional", "Maithili folk song, Mithila tradition, harmonium, dholak, flute", 92),
        Genre("rajasthani_folk", "Rajasthani folk", "Regional & devotional", "Rajasthani folk, kamaicha, morchang, dholak, desert folk", 105),
        Genre("punjabi_folk", "Punjabi folk / bhangra", "Regional & devotional", "Punjabi folk bhangra, dhol, tumbi, energetic", 120),
        Genre("bhajan", "Bhajan", "Regional & devotional", "Hindi bhajan, devotional, harmonium, tabla, manjira, peaceful", 80),
        Genre("ghazal", "Ghazal", "Classical & Sufi", "Urdu ghazal, harmonium, tabla, sarangi, soulful, slow", 70),
        Genre("qawwali", "Qawwali", "Classical & Sufi", "qawwali, harmonium, tabla, dholak, handclaps, group chorus, building intensity", 110),
        Genre("sufi", "Sufi", "Classical & Sufi", "Sufi song, rabab, harmonium, tabla, spiritual, soaring", 90),
        Genre("hindustani_light", "Light classical", "Classical & Sufi", "Hindustani light classical, sitar, tabla, tanpura drone, raga-inspired", 75),
        Genre("bollywood_romantic", "Hindi film romantic", "Hindi film-inspired", "Hindi film romantic song, lush strings, acoustic guitar, tabla, melodic", 95),
        Genre("bollywood_dance", "Hindi film dance", "Hindi film-inspired", "Hindi film dance number, dhol, synth bass, energetic, festive", 125),
        Genre("bollywood_sad", "Hindi film sad", "Hindi film-inspired", "Hindi film sad song, piano, strings, emotional, slow", 72),
        Genre("wedding", "Wedding / shaadi", "Occasions", "Indian wedding song, dhol, shehnai, celebratory, festive, sangeet", 115),
        Genre("children", "Children's song", "Occasions", "children's song, simple catchy melody, playful, xylophone, ukulele", 110),
        Genre("pop", "Pop", "Global", "pop, catchy hook, polished production, synths, drums", 115),
        Genre("rock", "Rock", "Global", "rock, electric guitars, bass, live drums, driving", 130),
        Genre("rap", "Rap", "Global", "rap, rhythmic flow, punchy drums, 808 bass", 92),
        Genre("hiphop", "Hip-hop", "Global", "hip-hop, boom bap drums, samples, bass", 90),
        Genre("desi_hiphop", "Desi hip-hop", "Global", "desi hip-hop, Hindi rap, tabla samples, 808, trap hi-hats", 95),
        Genre("edm", "EDM", "Global", "EDM, festival build-up, drop, side-chained synths", 128),
        Genre("lofi", "Lo-fi", "Global", "lo-fi hip hop, mellow, vinyl crackle, soft keys, chill", 80),
        Genre("acoustic", "Acoustic", "Global", "acoustic, fingerpicked guitar, intimate, warm", 95),
        Genre("orchestral", "Orchestral", "Cinematic & ambient", "orchestral, full symphony orchestra, strings, brass, timpani", 90),
        Genre("cinematic", "Cinematic", "Cinematic & ambient", "cinematic score, epic, choir, percussion, trailer", 100),
        Genre("meditation", "Meditation", "Cinematic & ambient", "meditation music, ambient pads, singing bowls, flute, calm, slow", 60),
    )

    val moods = listOf(
        Mood("joyful", "Joyful", "joyful, uplifting"),
        Mood("devotional", "Devotional", "devotional, reverent, spiritual"),
        Mood("romantic", "Romantic", "romantic, tender"),
        Mood("sad", "Sad", "sad, melancholic, heartfelt"),
        Mood("energetic", "Energetic", "energetic, high energy"),
        Mood("festive", "Festive", "festive, celebratory"),
        Mood("calm", "Calm", "calm, peaceful, soothing"),
        Mood("nostalgic", "Nostalgic", "nostalgic, wistful"),
        Mood("powerful", "Powerful", "powerful, anthemic"),
        Mood("playful", "Playful", "playful, light-hearted"),
        Mood("dark", "Dark", "dark, intense, brooding"),
    )

    val instruments = listOf(
        Instrument("dholak", "Dholak", "dholak"),
        Instrument("tabla", "Tabla", "tabla"),
        Instrument("dhol", "Dhol", "dhol"),
        Instrument("harmonium", "Harmonium", "harmonium"),
        Instrument("shehnai", "Shehnai", "shehnai"),
        Instrument("bansuri", "Bansuri flute", "bansuri flute"),
        Instrument("sitar", "Sitar", "sitar"),
        Instrument("sarangi", "Sarangi", "sarangi"),
        Instrument("santoor", "Santoor", "santoor"),
        Instrument("manjira", "Manjira", "manjira cymbals"),
        Instrument("nagada", "Nagada", "nagada drums"),
        Instrument("acoustic_guitar", "Acoustic guitar", "acoustic guitar"),
        Instrument("electric_guitar", "Electric guitar", "electric guitar"),
        Instrument("piano", "Piano", "piano"),
        Instrument("strings", "Strings", "string section"),
        Instrument("synth", "Synths", "synthesizers"),
        Instrument("bass_808", "808 bass", "808 bass"),
        Instrument("drums", "Drum kit", "drum kit"),
    )

    val arrangements = listOf(
        Arrangement("full_band", "Full arrangement", "full arrangement"),
        Arrangement("minimal", "Minimal", "minimal arrangement, sparse"),
        Arrangement("unplugged", "Unplugged", "unplugged, acoustic arrangement"),
        Arrangement("folk_ensemble", "Folk ensemble", "traditional folk ensemble"),
        Arrangement("orchestral_build", "Orchestral build", "gradual orchestral build-up"),
        Arrangement("electronic", "Electronic", "electronic production"),
    )

    val languages = listOf(
        Language("hi", "Hindi", "हिन्दी", "hi"),
        Language("bho", "Bhojpuri", "भोजपुरी", "hi", "Sung with Hindi pronunciation; the model has no separate Bhojpuri mode."),
        Language("mai", "Maithili", "मैथिली", "hi", "Sung with Hindi pronunciation; the model has no separate Maithili mode."),
        Language("awa", "Awadhi", "अवधी", "hi", "Sung with Hindi pronunciation."),
        Language("en", "English", "English", "en"),
        Language("ur", "Urdu", "اردو", "ur", "Support depends on the model."),
        Language("pa", "Punjabi", "ਪੰਜਾਬੀ", "pa", "Support depends on the model."),
        Language("bn", "Bengali", "বাংলা", "bn", "Support depends on the model."),
        Language("mr", "Marathi", "मराठी", "mr", "Support depends on the model."),
        Language("ta", "Tamil", "தமிழ்", "ta", "Support depends on the model."),
        Language("te", "Telugu", "తెలుగు", "te", "Support depends on the model."),
    )

    val voices = listOf(
        VoiceProfile("folk_female_warm", "Gaon Ki Awaaz", VocalPresentation.FEMALE, "alto", listOf("warm", "earthy"), "folk", "open-throated folk singing with ornamentation", "Warm, earthy female folk voice suited to Bhojpuri, Maithili and Chhath geet."),
        VoiceProfile("folk_male_resonant", "Mitti Ka Sur", VocalPresentation.MALE, "baritone", listOf("resonant", "rustic"), "folk", "strong chest voice", "Resonant rustic male folk voice for village and festival songs."),
        VoiceProfile("devotional_female_soft", "Aarti", VocalPresentation.FEMALE, "mezzo-soprano", listOf("soft", "serene"), "devotional", "gentle sustained notes", "Soft, serene devotional female voice for bhajans and aartis."),
        VoiceProfile("devotional_male_deep", "Sankalp", VocalPresentation.MALE, "bass-baritone", listOf("deep", "calm"), "devotional", "steady chant-like phrasing", "Deep, calm male voice for bhajans and meditative chants."),
        VoiceProfile("classical_female_bright", "Raagini", VocalPresentation.FEMALE, "soprano", listOf("bright", "agile"), "classical", "light classical murki and meend ornaments", "Bright, agile voice with light-classical ornaments."),
        VoiceProfile("ghazal_male_husky", "Shaam", VocalPresentation.MALE, "baritone", listOf("husky", "velvety"), "classical", "expressive slow phrasing", "Husky, velvety male voice for ghazal and Sufi."),
        VoiceProfile("sufi_powerful", "Junoon", VocalPresentation.MALE, "tenor", listOf("powerful", "soaring"), "devotional", "high belting with long sustained notes", "Powerful soaring voice for qawwali and Sufi peaks."),
        VoiceProfile("film_female_sweet", "Chandni", VocalPresentation.FEMALE, "soprano", listOf("sweet", "clear"), "contemporary", "smooth legato playback singing", "Sweet, clear contemporary female voice for Hindi film-style songs."),
        VoiceProfile("film_male_romantic", "Saawan", VocalPresentation.MALE, "tenor", listOf("warm", "breathy"), "contemporary", "soft romantic crooning", "Warm, breathy male voice for romantic songs."),
        VoiceProfile("pop_androgynous", "Neon", VocalPresentation.ANDROGYNOUS, "mid-range", listOf("airy", "modern"), "contemporary", "light falsetto accents", "Airy, modern androgynous pop voice."),
        VoiceProfile("rap_male", "Gully Flow", VocalPresentation.MALE, "mid-range", listOf("punchy", "gritty"), "contemporary", "rhythmic rap delivery", "Punchy, gritty rap delivery."),
        VoiceProfile("rap_female", "Tez", VocalPresentation.FEMALE, "mid-range", listOf("crisp", "confident"), "contemporary", "fast rhythmic rap delivery", "Crisp, confident female rap delivery."),
        VoiceProfile("youthful_bright", "Chhotu Tara", VocalPresentation.CHILD_LIKE, "high", listOf("bright", "cheerful"), "contemporary", "simple melodic singing", "Bright, cheerful youthful voice for children's songs (adult-performed style)."),
        VoiceProfile("duet_folk", "Jodi", VocalPresentation.DUET, "mixed", listOf("warm", "playful"), "folk", "call-and-response between male and female voices", "Male–female folk duet with call and response."),
        VoiceProfile("group_chorus", "Sangat", VocalPresentation.GROUP, "mixed", listOf("full", "resonant"), "devotional", "group chorus responses", "Group chorus for kirtan, qawwali refrains and wedding songs."),
    )

    fun genre(id: String) = genres.firstOrNull { it.id == id } ?: genres.first()
    fun mood(id: String) = moods.firstOrNull { it.id == id } ?: moods.first()
    fun voice(id: String) = voices.firstOrNull { it.id == id } ?: voices.first()
    fun arrangement(id: String) = arrangements.firstOrNull { it.id == id } ?: arrangements.first()
    fun language(id: String) = languages.firstOrNull { it.id == id } ?: languages.first()
    fun instrumentsFor(ids: String): List<Instrument> {
        val wanted = ids.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return instruments.filter { it.id in wanted }
    }
}
