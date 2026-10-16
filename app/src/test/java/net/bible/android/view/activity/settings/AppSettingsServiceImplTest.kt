/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.settings

import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.SystemBarSettingChanges
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.DictOption
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeDictionaryOptionsProvider(
    override val wordLookupDictOptions: List<DictOption> = emptyList(),
    override val greekDictOptions: List<DictOption> = emptyList(),
    override val hebrewDictOptions: List<DictOption> = emptyList(),
    override val greekMorphOptions: List<DictOption> = emptyList(),
) : DictionaryOptionsProvider

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class AppSettingsServiceImplTest {
    @After fun tearDown() = DatabaseResetter.resetDatabase()

    @Test fun realReadersKeepOnyxDefaultAndPersistedChoices() {
        val originalBrand = android.os.Build.BRAND
        val originalChoice = CommonUtils.settings.getString("display_color_mode", null)
        try {
            for (brand in listOf("onyx", "google")) {
                org.robolectric.shadows.ShadowBuild.setBrand(brand)
                CommonUtils.settings.removeString("display_color_mode")
                val expected = if (brand == "onyx") "monochrome" else "normal"
                assertEquals(expected, CommonUtils.settings.displayColorMode.value)
                assertEquals(expected, AppSettingsServiceImpl().snapshot.value.displayColorMode)
                for (choice in listOf("bw", "monochrome")) {
                    AppSettingsServiceImpl().setString("display_color_mode", choice)
                    assertEquals(choice, CommonUtils.settings.displayColorMode.value)
                    assertEquals(choice, AppSettingsServiceImpl().snapshot.value.displayColorMode)
                }
            }
        } finally {
            org.robolectric.shadows.ShadowBuild.setBrand(originalBrand)
            CommonUtils.settings.setString("display_color_mode", originalChoice)
        }
    }

    private val abcOptions = listOf(DictOption("A", "A"), DictOption("B", "B"), DictOption("C", "C"))

    // --- (a) PreferenceStore routing: realShared keys must NOT land in CommonUtils.settings ---

    @Test fun realSharedKey_discreteMode_writesToRealSharedPreferences_notSettingsDb() {
        val service = AppSettingsServiceImpl()

        service.setBool("discrete_mode", true)

        assertTrue(CommonUtils.realSharedPreferences.getBoolean("discrete_mode", false))
        // The classic PreferenceStore never touches CommonUtils.settings for this key.
        assertFalse(CommonUtils.settings.getBoolean("discrete_mode", false))
        assertTrue(service.snapshot.value.discreteMode)
    }

    @Test fun normalKey_einkMode_writesToSettingsDb_notRealShared() {
        val service = AppSettingsServiceImpl()

        service.setBool("eink_mode", true)

        assertTrue(CommonUtils.settings.getBoolean("eink_mode", false))
        assertFalse(CommonUtils.realSharedPreferences.getBoolean("eink_mode", false))
        assertTrue(service.snapshot.value.einkMode)
    }

    @Test fun writingHideStatusBarAnnouncesIt() {
        var seen = 0
        val subscription = SystemBarSettingChanges.changes.subscribe { seen++ }
        try {
            AppSettingsServiceImpl().setBool("hide_status_bar", true)
            assertEquals(1, seen)
            AppSettingsServiceImpl().setBool("volume_keys_scroll", true)
            assertEquals("other keys do not announce it", 1, seen)
        } finally {
            subscription.cancel()
            CommonUtils.settings.setBoolean("hide_status_bar", false)
        }
    }

    // --- (b) Inverse multi-select round trip ---

    @Test fun inverseSet_presentsEnabledAsAllOptionsMinusStoredDisabled() {
        CommonUtils.settings.setStringSet("disabled_word_lookup_dictionaries", setOf("B"))
        val service = AppSettingsServiceImpl(FakeDictionaryOptionsProvider(wordLookupDictOptions = abcOptions))

        assertEquals(setOf("A", "C"), service.snapshot.value.enabledWordLookupDicts)
    }

    @Test fun inverseSet_writeConvertsPositiveSelectionBackToDisabledSet() {
        CommonUtils.settings.setStringSet("disabled_word_lookup_dictionaries", setOf("B"))
        val service = AppSettingsServiceImpl(FakeDictionaryOptionsProvider(wordLookupDictOptions = abcOptions))

        // User now selects only "A" as enabled -> B and C become disabled.
        service.setStringSet("disabled_word_lookup_dictionaries", setOf("A"))

        assertEquals(setOf("B", "C"), CommonUtils.settings.getStringSet("disabled_word_lookup_dictionaries", emptySet()))
        assertEquals(setOf("A"), service.snapshot.value.enabledWordLookupDicts)
    }

    @Test fun inverseSet_defaultDisabledIsEmpty_soAllOptionsStartEnabled() {
        val service = AppSettingsServiceImpl(FakeDictionaryOptionsProvider(wordLookupDictOptions = abcOptions))

        assertEquals(setOf("A", "B", "C"), service.snapshot.value.enabledWordLookupDicts)
    }

    // --- static-array-backed inverse prefs (bookmark modal buttons) exercise the same conversion
    // without needing the dictionary-options seam. ---

    @Test fun bibleBookmarkModalButtons_inverseRoundTrip() {
        val service = AppSettingsServiceImpl()
        val all = service.snapshot.value.bibleBookmarkModalOptions.map { it.initials }.toSet()
        assertTrue(all.contains("BOOKMARK"))

        // User deselects only "BOOKMARK" -> the enabled (positive) set sent is all-minus-BOOKMARK,
        // which the impl must convert to a stored DISABLED set of exactly {"BOOKMARK"}.
        service.setStringSet("disable_bible_bookmark_modal_buttons", all - "BOOKMARK")

        assertEquals(all - "BOOKMARK", service.snapshot.value.enabledBibleBookmarkModalButtons)
        assertEquals(setOf("BOOKMARK"), CommonUtils.settings.getStringSet("disable_bible_bookmark_modal_buttons", emptySet()))
    }

    // --- defaults / visibility flags sanity (documents the quirks noted in the impl kdoc) ---

    @Test fun defaults_matchClassicScreenBehavior() {
        val service = AppSettingsServiceImpl()
        val s = service.snapshot.value

        assertEquals("system", s.nightMode) // fragment override, not the XML "manual" default
        assertFalse(s.fullScreenHideButtons) // XML defaultValue="true" is inert stray text - see impl kdoc
        assertEquals("1234", s.calculatorPin)
        assertEquals(100, s.fontSizeMultiplier)
        assertTrue(s.toolbarButtonActionChoices.isNotEmpty())
        assertTrue(s.nightModeChoices.isNotEmpty())
    }

    @Test fun snapshotBuildsWithRealDictionaryProvider_noModulesNeeded() {
        // Smoke-tests the production wiring (RealDictionaryOptionsProvider -> Books.installed())
        // does not throw, and hasAnyDictionary is consistent with the returned option lists -
        // whether any dictionaries are "installed" depends on the test's Sword module fixtures,
        // which this test intentionally does not depend on.
        val service = AppSettingsServiceImpl()
        val s = service.snapshot.value
        val anyOptionsPresent = s.greekDictOptions.isNotEmpty() || s.hebrewDictOptions.isNotEmpty() ||
            s.greekMorphOptions.isNotEmpty() || s.wordLookupDictOptions.isNotEmpty()
        assertEquals(anyOptionsPresent, s.hasAnyDictionary)
    }
}
