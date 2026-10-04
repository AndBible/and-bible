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

import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.nav.SystemBarSettingChangedEvent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedcore.settings.AppSettingsSnapshot
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.DictOption
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.FeatureType

/**
 * Supplies the dictionary/morphology/word-lookup option lists offered by the four
 * dictionary-family multi-selects. The production default ([RealDictionaryOptionsProvider])
 * mirrors `SettingsFragment.setupDictionary`/`setupPlainDictionary` exactly (installed-book
 * lookup via JSword's [Books]). Exists as a seam so tests can inject a fake list without
 * touching JSword/Books under Robolectric.
 */
interface DictionaryOptionsProvider {
    val greekDictOptions: List<DictOption>
    val hebrewDictOptions: List<DictOption>
    val greekMorphOptions: List<DictOption>
    val wordLookupDictOptions: List<DictOption>
}

/** Mirrors `SettingsFragment.setupDictionary`/`setupPlainDictionary` (installed-book lookup). */
object RealDictionaryOptionsProvider : DictionaryOptionsProvider {
    private fun booksWithFeature(type: FeatureType): List<DictOption> =
        Books.installed().books.filter { it.hasFeature(type) }.map { DictOption(it.initials, it.name) }

    override val greekDictOptions: List<DictOption> get() = booksWithFeature(FeatureType.GREEK_DEFINITIONS)
    override val hebrewDictOptions: List<DictOption> get() = booksWithFeature(FeatureType.HEBREW_DEFINITIONS)
    override val greekMorphOptions: List<DictOption> get() = booksWithFeature(FeatureType.GREEK_PARSE)

    // Get all dictionaries except Strong's and morphology (mirrors setupPlainDictionary).
    override val wordLookupDictOptions: List<DictOption>
        get() = Books.installed().books.filter {
            it.bookCategory == BookCategory.DICTIONARY &&
                !it.hasFeature(FeatureType.GREEK_DEFINITIONS) &&
                !it.hasFeature(FeatureType.HEBREW_DEFINITIONS) &&
                !it.hasFeature(FeatureType.GREEK_PARSE)
        }.map { DictOption(it.initials, it.name) }
}

/**
 * Android impl of [AppSettingsService]. Reproduces the classic `PreferenceStore` routing
 * (`SettingsActivity.kt`) key-for-key: `locale_pref`/`calculator_pin`/`show_calculator`/
 * `discrete_mode` and any `night_mode*` key persist to [CommonUtils.realSharedPreferences];
 * everything else persists to [CommonUtils.settings] (the Room-backed `SettingsDatabase`).
 * Inverse multi-selects (`disabled_word_lookup_dictionaries`, `disable_bible_bookmark_modal_buttons`,
 * `disable_gen_bookmark_modal_buttons`) store the DISABLED set but present/accept the POSITIVE
 * (enabled) set, per `InverseMultiSelectListPreference` semantics.
 *
 * Defaults reproduce `settings.xml` / `SettingsFragment.onCreatePreferences` runtime overrides
 * exactly, including two non-obvious ones:
 *  - `night_mode_pref3` defaults to `"system"` (NOT the XML `defaultValue="manual"`) because
 *    `onCreatePreferences` unconditionally calls `nightModePref.setDefaultValue("system")` in
 *    BOTH the `autoModeAvailable` and non-`autoModeAvailable` branches.
 *  - `full_screen_hide_buttons_pref` defaults to `false` (NOT `true`, despite the visible intent
 *    in `settings.xml`): the `<SwitchPreferenceCompat .../>` tag self-closes one line early
 *    (`.../>` on the `icon` line) and the following `android:defaultValue="true"` line is inert
 *    stray text, not an XML attribute (verified: `ElementTree`/expat parses the file successfully
 *    and the inflated preference carries no `defaultValue`). This is a pre-existing bug in the
 *    classic XML; reproduced here for byte-for-byte parity, not fixed.
 *  - `strongs_greek_dictionary`/`strongs_hebrew_dictionary`/`robinson_greek_morphology` default to
 *    ALL installed matching books selected (`setupDictionary` calls `pref.setDefaultValue(initials)`
 *    with every installed book's initials) — NOT an empty set.
 *  - `display_color_mode` defaults to `"bw"` on Onyx devices, `"normal"` otherwise (fragment
 *    override of the XML `defaultValue="normal"`).
 *  - `toolbar_button_actions` has no XML default; `onCreatePreferences` sets it to `"default"`
 *    when blank, so that is the effective default read here too.
 */
class AppSettingsServiceImpl(
    private val dictionaryOptionsProvider: DictionaryOptionsProvider = RealDictionaryOptionsProvider,
) : AppSettingsService {

    // The four dictionary/morphology option lists each do a live Books.installed() scan; a routine
    // build() on a setting change would otherwise re-scan JSword four times. Cache them for the
    // service's lifetime (the classic screen does not live-update when a book is installed while
    // open either, so a per-screen cache is correct). Production still uses the real Books lookup;
    // it just isn't repeated on every build()/setStringSet.
    private val greekDictOptions: List<DictOption> by lazy { dictionaryOptionsProvider.greekDictOptions }
    private val hebrewDictOptions: List<DictOption> by lazy { dictionaryOptionsProvider.hebrewDictOptions }
    private val greekMorphOptions: List<DictOption> by lazy { dictionaryOptionsProvider.greekMorphOptions }
    private val wordLookupDictOptions: List<DictOption> by lazy { dictionaryOptionsProvider.wordLookupDictOptions }

    private val _snapshot = MutableStateFlow(build())
    override val snapshot: StateFlow<AppSettingsSnapshot> = _snapshot.asStateFlow()

    // ---- PreferenceStore routing (mirrors SettingsActivity.kt's PreferenceStore.useRealShared) ----

    private fun useRealShared(key: String): Boolean =
        key == "locale_pref" || key == "calculator_pin" || key == "show_calculator" ||
            key == "discrete_mode" || key.startsWith("night_mode")

    private fun readBool(key: String, default: Boolean): Boolean =
        if (useRealShared(key)) CommonUtils.realSharedPreferences.getBoolean(key, default)
        else CommonUtils.settings.getBoolean(key, default)

    private fun readString(key: String, default: String): String =
        if (useRealShared(key)) (CommonUtils.realSharedPreferences.getString(key, default) ?: default)
        else (CommonUtils.settings.getString(key, default) ?: default)

    private fun readStringSet(key: String, default: Set<String>): Set<String> =
        // classic's preference datastore (deleted S12) never routed StringSet through realShared
        CommonUtils.settings.getStringSet(key, default)

    // ---- Inverse multi-select helpers ----

    /** Positive (enabled) set = allOptions - storedDisabledSet. Stored disabled set defaults to empty (all enabled). */
    private fun readInverseSet(key: String, allInitials: Set<String>): Set<String> {
        val disabled = readStringSet(key, emptySet())
        return allInitials - disabled
    }

    private fun writeInverseSet(key: String, positive: Set<String>, allInitials: Set<String>) {
        CommonUtils.settings.setStringSet(key, allInitials - positive)
    }

    // ---- Choice/array helpers ----

    private fun choicesFrom(labelsRes: Int, valuesRes: Int): List<Choice2> {
        val labels = application.resources.getStringArray(labelsRes)
        val values = application.resources.getStringArray(valuesRes)
        return values.indices.map { Choice2(values[it], labels.getOrElse(it) { values[it] }) }
    }

    private fun dictOptionsFrom(namesRes: Int, idsRes: Int): List<DictOption> {
        val names = application.resources.getStringArray(namesRes)
        val ids = application.resources.getStringArray(idsRes)
        return ids.indices.map { DictOption(ids[it], names.getOrElse(it) { ids[it] }) }
    }

    private fun nightModeChoices(): List<Choice2> =
        if (ScreenSettings.autoModeAvailable)
            choicesFrom(R.array.prefs_night_mode_descriptions_system_auto_manual, R.array.prefs_night_mode_values_system_auto_manual)
        else
            choicesFrom(R.array.prefs_night_mode_descriptions_system_manual, R.array.prefs_night_mode_values_system_manual)

    private fun bibleBookmarkModalOptions(): List<DictOption> =
        dictOptionsFrom(R.array.prefs_bible_bookmark_modal_action_names, R.array.prefs_bible_bookmark_modal_action_ids)

    private fun genBookmarkModalOptions(): List<DictOption> =
        dictOptionsFrom(R.array.prefs_gen_bookmark_modal_action_names, R.array.prefs_gen_bookmark_modal_action_ids)

    private fun experimentalFeatureOptions(): List<DictOption> =
        dictOptionsFrom(R.array.experimental_features_names, R.array.experimental_features_values)

    // ---- Snapshot construction ----

    private fun build(): AppSettingsSnapshot {
        val bibleBookmarkModalOptions = bibleBookmarkModalOptions()
        val genBookmarkModalOptions = genBookmarkModalOptions()
        val experimentalFeatureOptions = experimentalFeatureOptions()

        return AppSettingsSnapshot(
            // Dictionaries
            greekDicts = readStringSet("strongs_greek_dictionary", greekDictOptions.map { it.initials }.toSet()),
            hebrewDicts = readStringSet("strongs_hebrew_dictionary", hebrewDictOptions.map { it.initials }.toSet()),
            greekMorph = readStringSet("robinson_greek_morphology", greekMorphOptions.map { it.initials }.toSet()),
            enabledWordLookupDicts = readInverseSet(
                "disabled_word_lookup_dictionaries", wordLookupDictOptions.map { it.initials }.toSet()
            ),
            greekDictOptions = greekDictOptions,
            hebrewDictOptions = hebrewDictOptions,
            greekMorphOptions = greekMorphOptions,
            wordLookupDictOptions = wordLookupDictOptions,
            hasAnyDictionary = greekDictOptions.isNotEmpty() || hebrewDictOptions.isNotEmpty() ||
                greekMorphOptions.isNotEmpty() || wordLookupDictOptions.isNotEmpty(),
            // Behavior
            navigateToVerse = readBool("navigate_to_verse_pref", false),
            openLinksInSpecialWindow = readBool("open_links_in_special_window_pref", true),
            screenKeepOn = readBool("screen_keep_on_pref", false),
            doubleTapToFullscreen = readBool("double_tap_to_fullscreen", true),
            autoFullscreen = readBool("auto_fullscreen_pref", false),
            toolbarButtonActions = readString("toolbar_button_actions", "default").ifBlank { "default" },
            bibleViewSwipeMode = readString("bible_view_swipe_mode", "CHAPTER"),
            disableTwoStepBookmarking = readBool("disable_two_step_bookmarking", false),
            volumeKeysScroll = readBool("volume_keys_scroll", true),
            nightMode = readString("night_mode_pref3", "system"),
            // Display
            locale = readString("locale_pref", ""),
            disableClickToEdit = readBool("disable_click_to_edit", false),
            notesContentType = readString("notes_content_type", "HTML"),
            fontSizeMultiplier = CommonUtils.settings.getInt("font_size_multiplier", 100),
            hideStatusBar = readBool("hide_status_bar", false),
            // See kdoc: settings.xml's defaultValue="true" for this key is inert stray text.
            fullScreenHideButtons = readBool("full_screen_hide_buttons_pref", false),
            hideWindowButtons = readBool("hide_window_buttons", false),
            hideBibleReferenceOverlay = readBool("hide_bible_reference_overlay", false),
            showActiveWindowIndicator = readBool("show_active_window_indicator", true),
            enabledBibleBookmarkModalButtons = readInverseSet(
                "disable_bible_bookmark_modal_buttons", bibleBookmarkModalOptions.map { it.initials }.toSet()
            ),
            enabledGenBookmarkModalButtons = readInverseSet(
                "disable_gen_bookmark_modal_buttons", genBookmarkModalOptions.map { it.initials }.toSet()
            ),
            bibleBookmarkModalOptions = bibleBookmarkModalOptions,
            genBookmarkModalOptions = genBookmarkModalOptions,
            // E-ink
            displayColorMode = readString("display_color_mode", if (CommonUtils.isOnyxDevice) "bw" else "normal"),
            einkMode = readBool("eink_mode", false),
            disableAnimations = readBool("disable_animations", false),
            // Persecution
            discreteMode = readBool("discrete_mode", false),
            showCalculator = readBool("show_calculator", false),
            calculatorPin = readString("calculator_pin", "1234"),
            // Features / advanced / developer
            experimentalFeatures = readStringSet("experimental_features", emptySet()),
            experimentalFeatureOptions = experimentalFeatureOptions,
            enableBluetooth = readBool("enable_bluetooth_pref", true),
            requestSdcardPermission = readBool("request_sdcard_permission_pref", false),
            showErrorbox = readBool("show_errorbox", false),
            // Choice entries
            toolbarButtonActionChoices = choicesFrom(
                R.array.prefs_toolbar_button_action_descriptions, R.array.prefs_toolbar_button_action_values
            ),
            bibleViewSwipeModeChoices = choicesFrom(
                R.array.prefs_bible_view_swipe_mode_descriptions, R.array.prefs_bible_view_swipe_mode_values
            ),
            nightModeChoices = nightModeChoices(),
            localeChoices = choicesFrom(R.array.prefs_interface_locale_descriptions, R.array.prefs_interface_locale_values),
            notesContentTypeChoices = choicesFrom(R.array.prefs_notes_content_type_entries, R.array.prefs_notes_content_type_values),
            displayColorModeChoices = choicesFrom(R.array.prefs_display_color_mode_names, R.array.prefs_display_color_mode_values),
            // Visibility flags (mirror SettingsFragment.onCreatePreferences gating exactly).
            // Classic only hides discrete_mode + show_calculator when isDiscrete
            // (SettingsActivity.kt: `if (BuildVariant.Appearance.isDiscrete) isVisible = false`);
            // the category header, discrete_help and calculator_pin are never gated.
            discreteTogglesVisible = !BuildVariant.Appearance.isDiscrete,
            betaFeaturesVisible = CommonUtils.isBeta,
            sdcardPermissionVisible = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q,
            openLinksVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    }

    // ---- Writes ----

    override fun setBool(key: String, value: Boolean) {
        if (useRealShared(key)) CommonUtils.realSharedPreferences.edit().putBoolean(key, value).apply()
        else CommonUtils.settings.setBoolean(key, value)
        refresh()
        if (key == "hide_status_bar") ABEventBus.post(SystemBarSettingChangedEvent())
    }

    override fun setString(key: String, value: String) {
        if (useRealShared(key)) CommonUtils.realSharedPreferences.edit().putString(key, value).apply()
        else CommonUtils.settings.setString(key, value)
        refresh()
    }

    override fun setInt(key: String, value: Int) {
        // PreferenceStore.putInt never checks useRealShared - always CommonUtils.settings.
        CommonUtils.settings.setInt(key, value)
        refresh()
    }

    override fun setStringSet(key: String, value: Set<String>) {
        when (key) {
            "disabled_word_lookup_dictionaries" ->
                writeInverseSet(key, value, wordLookupDictOptions.map { it.initials }.toSet())
            "disable_bible_bookmark_modal_buttons" ->
                writeInverseSet(key, value, bibleBookmarkModalOptions().map { it.initials }.toSet())
            "disable_gen_bookmark_modal_buttons" ->
                writeInverseSet(key, value, genBookmarkModalOptions().map { it.initials }.toSet())
            else -> CommonUtils.settings.setStringSet(key, value)
        }
        refresh()
    }

    override fun refresh() {
        _snapshot.value = build()
    }
}
