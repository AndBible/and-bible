package net.bible.sharedui.app.chooser

import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.platform.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ChooserHostTest {
    private class IntSettings(private val values: Map<String, Int>) : AppSettings {
        override fun getString(key: String, default: String?): String? = default
        override fun getLong(key: String, default: Long): Long = default
        override fun getInt(key: String, default: Int): Int = values[key] ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = default
        override fun getDouble(key: String, default: Double): Double = default
        override fun getFloat(key: String, default: Float): Float = default
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) = Unit
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    @Test
    fun maxLettersAppendsWholeWordsUntilTheLimitThenEllipsis() {
        assertEquals("one two ...", maxDictionaryLettersWholeWords("one two three", max = 7))
    }

    @Test
    fun maxLettersKeepsShortTextWholeWithTrailingSpace() {
        assertEquals("short ", maxDictionaryLettersWholeWords("short", 50))
    }

    @Test
    fun cleanUpStripsTheLeadingKeyAndFlattensNewlines() {
        // The newline became a space and the key was cut off the front, so the remainder keeps that space: classic's
        // own behaviour, ported verbatim (the leading blank is only trimmed by the UI).
        assertEquals(" is patient ", cleanUpDictionarySnippet("Love\nis patient", key = "Love"))
    }

    @Test
    fun cleanUpOnlyStripsAKeyAtTheStart() {
        assertEquals("a Love b ", cleanUpDictionarySnippet("a Love b", key = "Love"))
    }

    @Test
    fun routeTypeWinsOverTheStoredFilter() {
        val settings = IntSettings(mapOf("selected_document_filter_no" to 2))
        assertEquals(DocTypeFilter.BIBLE, chooseDocumentInitialTypeFilter("BIBLE", settings))
        assertEquals(DocTypeFilter.COMMENTARY, chooseDocumentInitialTypeFilter("COMMENTARY", settings))
    }

    @Test
    fun noRouteTypeUsesTheStoredFilterIndex() {
        val settings = IntSettings(mapOf("selected_document_filter_no" to 2))
        assertEquals(DocTypeFilter.entries[2], chooseDocumentInitialTypeFilter(null, settings))
    }

    @Test
    fun anOutOfRangeStoredFilterFallsBackToAll() {
        val settings = IntSettings(mapOf("selected_document_filter_no" to 999))
        assertEquals(DocTypeFilter.ALL, chooseDocumentInitialTypeFilter(null, settings))
    }
}
