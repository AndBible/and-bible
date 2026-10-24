package net.bible.android.platform

import net.bible.sharedlogic.platform.LocaleSupport
import net.bible.sharedlogic.platform.LocaleValue
import java.util.Locale

object AndroidLocaleSupport : LocaleSupport {
    fun from(locale: Locale) = LocaleValue(locale.language, locale.country, locale.toLanguageTag(), locale.toString())
    override val default: LocaleValue get() = from(Locale.getDefault())
    override fun of(language: String, country: String) = from(Locale(language, country))
    override fun parse(legacy: String) = from(Locale(legacy))
    override fun displayLanguage(tag: String): String = Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it) }
    override fun defaultDisplayLanguage(): String = Locale.getDefault().let { it.getDisplayLanguage(it) }
    override fun formatFixed(value: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", value)
    override fun formatGrouped(value: Long): String = String.format(Locale.getDefault(), "%,d", value)
}
