package net.bible.sharedlogic.platform

/** A locale reduced to the fields shared code reads. */
data class LocaleValue(val language: String, val country: String, val languageTag: String, val asString: String)

/** Locale access. iOS provides this from NSLocale (current locale, BCP 47 tags, ICU number formatting). */
interface LocaleSupport {
    /** The current default locale, re-read on every access. */
    val default: LocaleValue
    fun of(language: String, country: String = ""): LocaleValue
    /** Parses a legacy persisted locale string such as `en_GB` or `fi`. */
    fun parse(legacy: String): LocaleValue
    /** Name of the language [tag] written in that language itself. */
    fun displayLanguage(tag: String): String
    fun defaultDisplayLanguage(): String
    /** [value] with exactly [decimals] fraction digits in the default locale. */
    fun formatFixed(value: Double, decimals: Int): String
    /** [value] with locale grouping separators. */
    fun formatGrouped(value: Long): String
}
