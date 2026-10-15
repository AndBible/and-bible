package net.bible.sharedcore.platform.speak

/** Native region per bare language, used when the device locale does not match (taken from the Swift app). */
private val nativeRegion = mapOf(
    "en" to "en-GB", "fr" to "fr-FR", "de" to "de-DE", "es" to "es-ES", "pt" to "pt-PT", "it" to "it-IT",
    "nl" to "nl-NL", "sv" to "sv-SE", "fi" to "fi-FI", "ru" to "ru-RU", "zh" to "zh-CN",
)

/** Languages with no voices of their own that read acceptably with another language's voices. */
private val substitute = mapOf("grc" to "el")

/**
 * Voice language tags to try, best first: an explicitly requested regional tag, the matching device
 * locale, the language's native region, then the bare language. Used by platforms whose TTS does not
 * resolve voices itself (iOS).
 */
fun speakVoiceCandidates(languageTag: String, deviceLocaleTag: String): List<String> {
    val language = languageTag.substringBefore('-').lowercase().let { substitute[it] ?: it }
    val requested = if (substitute.containsKey(languageTag.substringBefore('-').lowercase())) language else languageTag
    val device = deviceLocaleTag.takeIf { it.substringBefore('-').lowercase() == language }
    return listOfNotNull(
        requested.takeIf { it.contains('-') },
        device,
        nativeRegion[language],
        language,
    ).distinct()
}
