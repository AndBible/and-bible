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

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.settings.AppSettingsController
import net.bible.sharedcore.settings.AppSettingsLabels
import net.bible.sharedcore.settings.AppSettingsNav
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.theme.AbTheme

/**
 * Keys whose classic effect depends on values read only at Activity-creation time (locale via
 * [net.bible.android.view.util.locale.LocaleHelper.onAttach]/`attachBaseContext`; night mode / color
 * mode / animations via the [AbTheme] args below, computed once per composition; `discrete_mode`
 * changes app-wide behaviour gates read elsewhere) or that flip which Activity class future
 * navigation resolves to (`use_compose_ui`, read by [ScreenLauncher.useComposeFor]). None of these
 * are observed reactively by this screen, so [SettingsComposeActivity] force-recreates itself after
 * a write to any of them — the same primitive classic [SettingsActivity.reset] already uses to make
 * a bulk reset visible immediately. (No classic per-row listener does this today: `SettingsFragment`
 * only recreates on the explicit "reset" action. We still recreate per-write here so the NEW Compose
 * host's own theme/locale re-render immediately rather than only on next visit — see the Task 9
 * report for the full rationale.)
 */
private val RECREATE_ON_CHANGE_KEYS = setOf(
    "locale_pref",
    "night_mode_pref3",
    "display_color_mode",
    "discrete_mode",
    "use_compose_ui",
)

/**
 * Compose host for the main app Settings screen — the new-path twin of classic
 * [SettingsActivity]/[SettingsFragment]. Builds [AppSettingsLabels] from `strings.xml`, wires the
 * shared [AppSettingsController] (backed by [AppSettingsServiceImpl]), and renders
 * [AppSettingsScreen]. Every navigation-row / action-row target reuses the SAME classic code path
 * `SettingsFragment.onCreatePreferences` uses for that row (see [onNavigate] kdocs); `sync_settings_shortcut`
 * (`Screen.SyncSettings`, Batch 10-remainder), `ai_settings_shortcut` (`Screen.AiPrompts`, Batch 9) and
 * `reading_progress_settings_shortcut` (`Screen.ReadingProgressSettings`, Batch 10c) all route
 * through [ScreenLauncher].
 */
class SettingsComposeActivity : ActivityBase() {
    private val service by lazy { AppSettingsServiceImpl() }

    private val labels by lazy { buildLabels() }

    private val controller by lazy {
        AppSettingsController(
            service = service,
            scope = lifecycleScope,
            labels = labels,
            onNavigate = { key -> onNavigate(key) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val state by controller.state.collectAsState()
                    AppSettingsScreen(
                        state = state,
                        onUp = { finish() },
                        onSwitch = { key, checked -> controller.onSwitch(key, checked); maybeRecreate(key) },
                        onListChoice = { key, value -> controller.onListChoice(key, value); maybeRecreate(key) },
                        onTextInput = controller::onTextInput,
                        onSliderChange = controller::onSliderChange,
                        onMultiSelectChange = controller::onMultiSelectChange,
                        onNavigate = controller::onNavigate,
                        onReset = { confirmResetSettings() },
                        resetContentDescription = getString(R.string.reset_settings),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Parity with classic's pull-based refresh (e.g. installed-dictionary lists changed
        // elsewhere while this screen was backgrounded).
        service.refresh()
    }

    // --- Recreate parity -----------------------------------------------------------------------

    private fun maybeRecreate(key: String) {
        if (key in RECREATE_ON_CHANGE_KEYS) recreate()
    }

    // --- Navigation / actions --------------------------------------------------------------------
    // Each branch reuses the SAME classic code path SettingsFragment.onCreatePreferences wires to
    // the matching preference's onPreferenceClickListener.

    private fun onNavigate(key: String) {
        when (key) {
            AppSettingsNav.SYNC -> ScreenLauncher.open(this, Screen.SyncSettings)
            // Batch 9's AI settings screen is already flag-routed.
            AppSettingsNav.AI -> ScreenLauncher.open(this, Screen.AiPrompts)
            AppSettingsNav.READING_PROGRESS -> ScreenLauncher.open(this, Screen.ReadingProgressSettings)
            // Deferred to Batch 12 (BibleView reading-view) permanently for this batch: always the
            // classic TextDisplaySettingsActivity, with the same GLOBAL SettingsBundle extra classic
            // SettingsFragment builds.
            AppSettingsNav.TEXT_DISPLAY -> openGlobalTextDisplaySettings()
            AppSettingsNav.DISCRETE_HELP -> showDiscreteHelpDialog()
            AppSettingsNav.OPEN_LINKS -> openLinksSettings()
            AppSettingsNav.CRASH_APP -> crashApp()
        }
    }

    /** Mirrors classic SettingsFragment's "global_text_display_settings" click listener exactly. */
    private fun openGlobalTextDisplaySettings() {
        val settingsBundle = SettingsBundle(
            level = SettingsLevel.GLOBAL,
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        val intent = Intent(this, TextDisplaySettingsActivity::class.java)
        intent.putExtra("settingsBundle", settingsBundle.toJson())
        startActivity(intent)
    }

    /** Mirrors classic SettingsFragment's "discrete_help" click listener (persecution HTML dialog) exactly. */
    private fun showDiscreteHelpDialog() {
        val linkUrl = "https://github.com/AndBible/and-bible/wiki/Discrete-build"
        val linkText = "<a href=\"$linkUrl\">$linkUrl</a>"

        val dPar1 = getString(R.string.discrete_mode_info_par1)
        val dPar2 = getString(R.string.discrete_mode_info_par2)
        val dLink = getString(R.string.discrete_mode_link, linkText)

        val calcPar1 = getString(R.string.calculator_par1)
        val calcPar2 = getString(R.string.calculator_par2)
        val calcPar3 = getString(R.string.calculator_par3)

        val dText = "$dPar1<br><br>$dPar2<br><br>$dLink<br><br>"
        val calcText = "$calcPar1$calcPar2<br><br>$calcPar3<br><br>$dLink"
        val htmlMessage = if (!BuildVariant.Appearance.isDiscrete) dText else calcText

        val spanned = htmlToSpan(htmlMessage)

        val d = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.prefs_persecution_cat))
            setMessage(spanned)
            setPositiveButton(R.string.okay, null)
            setCancelable(true)
        }.create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    /** Mirrors classic SettingsFragment's "open_links" click listener exactly (row is gated invisible below S). */
    private fun openLinksSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                Uri.parse("package:$packageName"),
            )
            startActivity(intent)
        }
    }

    /**
     * Mirrors classic SettingsFragment's "crash_app" click listener exactly (row is gated to beta
     * builds), including using a fresh, activity-lifecycle-independent [CoroutineScope] (NOT
     * [lifecycleScope]) so the delayed crash still fires even if the user navigates away from
     * Settings before the 10 seconds elapse — same as classic.
     */
    private fun crashApp() {
        CoroutineScope(Dispatchers.Main).launch {
            ABEventBus.post(BibleApplication.ErrorNotificationEvent("Crashing app in 10 seconds!"))
            delay(10000)
            throw RuntimeException("Crash app!")
        }
    }

    // --- Reset ----------------------------------------------------------------------------------

    /** Same confirmation dialog classic [SettingsActivity.reset] shows before clearing. */
    private fun confirmResetSettings() {
        AlertDialog.Builder(this)
            .setMessage(R.string.reset_app_prefs)
            .setCancelable(true)
            .setPositiveButton(R.string.yes) { _, _ -> resetSettings() }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .show()
    }

    /**
     * Clears the SAME hardcoded key list classic [SettingsActivity.reset] clears
     * ([SettingsActivity.RESET_KEYS] + the four realShared-routed keys, via
     * [SettingsActivity.performReset] — extracted there so the two reset paths cannot drift), then
     * refreshes the service snapshot and recreates (locale/night-mode/color-mode/discrete-mode are
     * all in the cleared set, so this recreate is also covered by [RECREATE_ON_CHANGE_KEYS]'s
     * rationale).
     */
    private fun resetSettings() {
        SettingsActivity.performReset()
        service.refresh()
        recreate()
    }

    // --- Labels ----------------------------------------------------------------------------------

    private fun buildLabels() = AppSettingsLabels(
        screenTitle = getString(R.string.settings),
        fontSizePercentFormat = getString(R.string.pref_font_size_multiplier_percent_format),

        dictionariesCat = getString(R.string.prefs_dictionaries_cat),
        behaviorCat = getString(R.string.prefs_behavior_customization_cat),
        displayCat = getString(R.string.prefs_display_customization_cat),
        einkCat = getString(R.string.prefs_eink_settings_cat),
        persecutionCat = getString(R.string.prefs_persecution_cat),
        featuresCat = getString(R.string.prefs_features_cat),
        advancedCat = getString(R.string.prefs_advanced_settings_cat),
        developerCat = getString(R.string.prefs_category_developer),

        strongsGreekDictionaryTitle = getString(R.string.choose_strongs_greek_dictionary_title),
        strongsGreekDictionarySummary = getString(R.string.choose_strongs_greek_dictionary_summary),
        strongsHebrewDictionaryTitle = getString(R.string.choose_strongs_hebrew_dictionary_title),
        strongsHebrewDictionarySummary = getString(R.string.choose_strongs_hebrew_dictionary_summary),
        robinsonGreekMorphologyTitle = getString(R.string.choose_strongs_greek_morphology_title),
        robinsonGreekMorphologySummary = getString(R.string.choose_strongs_greek_morphology_summary),
        disabledWordLookupDictionariesTitle = getString(R.string.choose_word_lookup_dictionary_title),
        disabledWordLookupDictionariesSummary = getString(R.string.choose_word_lookup_dictionary_summary),

        navigateToVerseTitle = getString(R.string.prefs_navigate_to_verse_title),
        navigateToVerseSummary = getString(R.string.prefs_navigate_to_verse_summary),
        openLinksInSpecialWindowTitle = getString(R.string.prefs_open_links_in_special_window_title),
        openLinksInSpecialWindowSummary = getString(R.string.prefs_open_links_in_special_window_summary),
        screenKeepOnTitle = getString(R.string.prefs_screen_keep_on_title),
        screenKeepOnSummary = getString(R.string.prefs_screen_keep_on_summary),
        doubleTapToFullscreenTitle = getString(R.string.prefs_double_tap_to_fullscreen_title),
        doubleTapToFullscreenSummary = getString(R.string.prefs_double_tap_to_fullscreen_summary),
        autoFullscreenTitle = getString(R.string.auto_fullscreen),
        autoFullscreenSummary = getString(R.string.auto_fullscreen_summary),
        toolbarButtonActionsTitle = getString(R.string.prefs_toolbar_button_action_title),
        toolbarButtonActionsSummary = getString(R.string.prefs_toolbar_button_action_summary),
        bibleViewSwipeModeTitle = getString(R.string.prefs_bible_view_swipe_mode_title),
        bibleViewSwipeModeSummary = getString(R.string.prefs_bible_view_swipe_mode_summary),
        disableTwoStepBookmarkingTitle = getString(R.string.prefs_disable_two_step_bookmarking_title),
        disableTwoStepBookmarkingSummary = getString(R.string.prefs_disable_two_step_bookmarking_summary),
        volumeKeysScrollTitle = getString(R.string.prefs_volume_keys_scroll_title),
        volumeKeysScrollSummary = getString(R.string.prefs_volume_keys_scroll_summary),
        nightModeTitle = getString(R.string.prefs_night_mode_title),
        nightModeSummary = getString(R.string.prefs_night_mode_summary),

        localeTitle = getString(R.string.prefs_interface_locale_title),
        localeSummary = getString(R.string.prefs_interface_locale_summary),
        disableClickToEditTitle = getString(R.string.prefs_disable_click_to_edit_title),
        disableClickToEditSummary = getString(R.string.prefs_disable_click_to_edit_summary),
        notesContentTypeTitle = getString(R.string.prefs_notes_content_type_title),
        notesContentTypeSummary = getString(R.string.prefs_notes_content_type_summary),
        fontSizeMultiplierTitle = getString(R.string.pref_font_size_multiplier_title),
        hideStatusBarTitle = getString(R.string.prefs_hide_status_bar_title),
        hideStatusBarSummary = getString(R.string.prefs_hide_status_bar_summary),
        fullScreenHideButtonsTitle = getString(R.string.full_screen_hide_buttons_pref_title),
        fullScreenHideButtonsSummary = getString(R.string.full_screen_hide_buttons_pref_summary),
        hideWindowButtonsTitle = getString(R.string.hide_window_buttons_title),
        hideWindowButtonsSummary = getString(R.string.hide_window_buttons_summary),
        hideBibleReferenceOverlayTitle = getString(R.string.hide_bible_reference_overlay_title),
        hideBibleReferenceOverlaySummary = getString(R.string.hide_bible_reference_overlay_summary),
        showActiveWindowIndicatorTitle = getString(R.string.active_window_indicator_title),
        showActiveWindowIndicatorSummary = getString(R.string.active_window_indicator_summary),
        disableBibleBookmarkModalButtonsTitle = getString(R.string.prefs_in_window_bible_bookmark_modal_buttons_title),
        disableBibleBookmarkModalButtonsSummary = getString(R.string.prefs_in_window_bookmark_modal_buttons_description),
        disableGenBookmarkModalButtonsTitle = getString(R.string.prefs_in_window_gen_bookmark_modal_buttons_title),
        disableGenBookmarkModalButtonsSummary = getString(R.string.prefs_in_window_bookmark_modal_buttons_description),

        displayColorModeTitle = getString(R.string.prefs_display_color_mode_title),
        displayColorModeSummary = getString(R.string.prefs_display_color_mode_summary),
        einkModeTitle = getString(R.string.prefs_eink_display_title),
        einkModeSummary = getString(R.string.prefs_eink_display_summary),
        disableAnimationsTitle = getString(R.string.prefs_disable_animations_title),
        disableAnimationsSummary = getString(R.string.prefs_disable_animations_summary),

        discreteHelpTitle = getString(R.string.prefs_persecuted_help),
        discreteHelpSummary = getString(R.string.prefs_persecuted_summary),
        discreteModeTitle = getString(R.string.prefs_discrete_mode),
        discreteModeSummary = getString(R.string.prefs_discrete_mode_desc),
        showCalculatorTitle = getString(R.string.prefs_show_calculator),
        calculatorPinTitle = getString(R.string.prefs_calculator_pin),
        calculatorPinSummary = getString(R.string.prefs_calculator_pin_desc),

        experimentalFeaturesTitle = getString(R.string.prefs_experimental_features_title),
        experimentalFeaturesSummary = getString(R.string.prefs_experimental_features_summary),
        enableBluetoothTitle = getString(R.string.prefs_enable_bluetooth_title),
        enableBluetoothSummary = getString(R.string.prefs_enable_bluetooth_summary),
        requestSdcardPermissionTitle = getString(R.string.prefs_request_sdcard_permission_title),
        requestSdcardPermissionSummary = getString(R.string.prefs_request_sdcard_permission_summary),
        showErrorboxTitle = getString(R.string.prefs_show_error_box_title),
        showErrorboxSummary = getString(R.string.prefs_show_error_box_summary),
        openLinksTitle = getString(R.string.open_bible_links_title),
        openLinksSummary = getString(R.string.open_bible_links_summary),
        crashAppTitle = getString(R.string.crash_app),
        crashAppSummary = getString(R.string.crash_app_summary),
        useComposeUiTitle = getString(R.string.prefs_use_compose_ui),
        useComposeUiSummary = getString(R.string.prefs_use_compose_ui_summary),

        syncShortcutTitle = getString(R.string.cloud_sync_title),
        syncShortcutSummary = getString(R.string.sync_settings_shortcut_summary),
        aiShortcutTitle = getString(R.string.ai_settings),
        aiShortcutSummary = getString(R.string.ai_settings_shortcut_summary),
        readingProgressShortcutTitle = getString(R.string.reading_progress_settings),
        readingProgressShortcutSummary = getString(R.string.reading_progress_settings_summary),
        textDisplayShortcutTitle = getString(R.string.global_text_display_settings_title),
        textDisplayShortcutSummary = getString(R.string.global_text_display_settings_summary),
    )
}
