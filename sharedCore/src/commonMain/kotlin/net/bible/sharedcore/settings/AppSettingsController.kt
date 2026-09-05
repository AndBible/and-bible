package net.bible.sharedcore.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Navigation keys the host maps to concrete settings screens / actions. Rows whose only job is to
 * jump elsewhere (or trigger a host action such as the crash/open-links debug entries) carry one of
 * these as their `key` and forward through the constructor [AppSettingsController.onNavigate] lambda.
 */
object AppSettingsNav {
    const val TEXT_DISPLAY = "global_text_display_settings"
    const val SYNC = "sync_settings_shortcut"
    const val AI = "ai_settings_shortcut"
    const val READING_PROGRESS = "reading_progress_settings_shortcut"
    const val DISCRETE_HELP = "discrete_help"
    const val OPEN_LINKS = "open_links"
    const val CRASH_APP = "crash_app"
}

/**
 * Builds the declarative [SettingsScreenState] for the main app settings screen from an
 * [AppSettingsService] snapshot, in the classic preference-screen order. Visibility gates come
 * straight from the snapshot (`hasAnyDictionary`, `discreteTogglesVisible`,
 * `betaFeaturesVisible`, `sdcardPermissionVisible`, `openLinksVisible`) — the controller never
 * computes them. Inverse multi-select prefs are already presented as positive selected sets by the
 * service impl, so the rows here (and the [onMultiSelectChange] write-through) stay semantics-free.
 * Modelled on `net.bible.sharedcore.ai.AiConnectionSettingsController`.
 */
class AppSettingsController(
    private val service: AppSettingsService,
    private val scope: CoroutineScope,
    private val labels: AppSettingsLabels,
    private val onNavigate: (String) -> Unit,
) {
    private val _state = MutableStateFlow(build(service.snapshot.value))
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        scope.launch { service.snapshot.collect { _state.value = build(it) } }
    }

    private fun List<DictOption>.optionChoices(): List<SettingsItem.Choice> =
        map { SettingsItem.Choice(it.initials, it.name) }

    private fun List<Choice2>.entryChoices(): List<SettingsItem.Choice> =
        map { SettingsItem.Choice(it.value, it.label) }

    private fun build(s: AppSettingsSnapshot): SettingsScreenState {
        // Round 14b: each row's `iconKey` is its own classic preference key (`:app`'s
        // `settingsDrawableRes` owns the actual drawable lookup); `request_sdcard_permission_pref`
        // stays iconless, matching `res/xml/settings.xml`. (It was one of two such rows until
        // Batch Z-late's epilogue deleted the Developer category and its single switch.)
        val items = listOf(
            // ---- Dictionaries ----
            SettingsItem.Category(
                key = "dictionaries_category",
                title = labels.dictionariesCat,
                visible = s.hasAnyDictionary,
            ),
            SettingsItem.MultiSelectRow(
                key = "strongs_greek_dictionary",
                title = labels.strongsGreekDictionaryTitle,
                summary = labels.strongsGreekDictionarySummary,
                options = s.greekDictOptions.optionChoices(),
                selectedValues = s.greekDicts,
                visible = s.hasAnyDictionary,
                iconKey = "strongs_greek_dictionary",
            ),
            SettingsItem.MultiSelectRow(
                key = "strongs_hebrew_dictionary",
                title = labels.strongsHebrewDictionaryTitle,
                summary = labels.strongsHebrewDictionarySummary,
                options = s.hebrewDictOptions.optionChoices(),
                selectedValues = s.hebrewDicts,
                visible = s.hasAnyDictionary,
                iconKey = "strongs_hebrew_dictionary",
            ),
            SettingsItem.MultiSelectRow(
                key = "robinson_greek_morphology",
                title = labels.robinsonGreekMorphologyTitle,
                summary = labels.robinsonGreekMorphologySummary,
                options = s.greekMorphOptions.optionChoices(),
                selectedValues = s.greekMorph,
                visible = s.hasAnyDictionary,
                iconKey = "robinson_greek_morphology",
            ),
            SettingsItem.MultiSelectRow(
                key = "disabled_word_lookup_dictionaries",
                title = labels.disabledWordLookupDictionariesTitle,
                summary = labels.disabledWordLookupDictionariesSummary,
                options = s.wordLookupDictOptions.optionChoices(),
                selectedValues = s.enabledWordLookupDicts,
                visible = s.hasAnyDictionary,
                iconKey = "disabled_word_lookup_dictionaries",
            ),
            // ---- Behavior ----
            SettingsItem.Category(
                key = "behavior_category",
                title = labels.behaviorCat,
                visible = true,
            ),
            SettingsItem.SwitchRow(
                key = "navigate_to_verse_pref",
                title = labels.navigateToVerseTitle,
                summary = labels.navigateToVerseSummary,
                checked = s.navigateToVerse,
                iconKey = "navigate_to_verse_pref",
            ),
            SettingsItem.SwitchRow(
                key = "open_links_in_special_window_pref",
                title = labels.openLinksInSpecialWindowTitle,
                summary = labels.openLinksInSpecialWindowSummary,
                checked = s.openLinksInSpecialWindow,
                iconKey = "open_links_in_special_window_pref",
            ),
            SettingsItem.SwitchRow(
                key = "screen_keep_on_pref",
                title = labels.screenKeepOnTitle,
                summary = labels.screenKeepOnSummary,
                checked = s.screenKeepOn,
                iconKey = "screen_keep_on_pref",
            ),
            SettingsItem.SwitchRow(
                key = "double_tap_to_fullscreen",
                title = labels.doubleTapToFullscreenTitle,
                summary = labels.doubleTapToFullscreenSummary,
                checked = s.doubleTapToFullscreen,
                iconKey = "double_tap_to_fullscreen",
            ),
            SettingsItem.SwitchRow(
                key = "auto_fullscreen_pref",
                title = labels.autoFullscreenTitle,
                summary = labels.autoFullscreenSummary,
                checked = s.autoFullscreen,
                iconKey = "auto_fullscreen_pref",
            ),
            SettingsItem.ListChoiceRow(
                key = "toolbar_button_actions",
                title = labels.toolbarButtonActionsTitle,
                summary = labels.toolbarButtonActionsSummary,
                entries = s.toolbarButtonActionChoices.entryChoices(),
                selectedValue = s.toolbarButtonActions,
                iconKey = "toolbar_button_actions",
            ),
            SettingsItem.ListChoiceRow(
                key = "bible_view_swipe_mode",
                title = labels.bibleViewSwipeModeTitle,
                summary = labels.bibleViewSwipeModeSummary,
                entries = s.bibleViewSwipeModeChoices.entryChoices(),
                selectedValue = s.bibleViewSwipeMode,
                iconKey = "bible_view_swipe_mode",
            ),
            SettingsItem.SwitchRow(
                key = "disable_two_step_bookmarking",
                title = labels.disableTwoStepBookmarkingTitle,
                summary = labels.disableTwoStepBookmarkingSummary,
                checked = s.disableTwoStepBookmarking,
                iconKey = "disable_two_step_bookmarking",
            ),
            SettingsItem.SwitchRow(
                key = "volume_keys_scroll",
                title = labels.volumeKeysScrollTitle,
                summary = labels.volumeKeysScrollSummary,
                checked = s.volumeKeysScroll,
                iconKey = "volume_keys_scroll",
            ),
            SettingsItem.ListChoiceRow(
                key = "night_mode_pref3",
                title = labels.nightModeTitle,
                summary = labels.nightModeSummary,
                entries = s.nightModeChoices.entryChoices(),
                selectedValue = s.nightMode,
                iconKey = "night_mode_pref3",
            ),
            // ---- Display ----
            SettingsItem.Category(
                key = "display_category",
                title = labels.displayCat,
                visible = true,
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.TEXT_DISPLAY,
                title = labels.textDisplayShortcutTitle,
                summary = labels.textDisplayShortcutSummary,
                iconKey = AppSettingsNav.TEXT_DISPLAY,
            ),
            SettingsItem.ListChoiceRow(
                key = "locale_pref",
                title = labels.localeTitle,
                summary = labels.localeSummary,
                entries = s.localeChoices.entryChoices(),
                selectedValue = s.locale,
                iconKey = "locale_pref",
            ),
            SettingsItem.SwitchRow(
                key = "disable_click_to_edit",
                title = labels.disableClickToEditTitle,
                summary = labels.disableClickToEditSummary,
                checked = s.disableClickToEdit,
                iconKey = "disable_click_to_edit",
            ),
            SettingsItem.ListChoiceRow(
                key = "notes_content_type",
                title = labels.notesContentTypeTitle,
                summary = labels.notesContentTypeSummary,
                entries = s.notesContentTypeChoices.entryChoices(),
                selectedValue = s.notesContentType,
                iconKey = "notes_content_type",
            ),
            SettingsItem.SliderRow(
                key = "font_size_multiplier",
                title = labels.fontSizeMultiplierTitle,
                value = s.fontSizeMultiplier,
                min = 10,
                max = 500,
                valueLabel = labels.fontSizePercentFormat.replace("%d", s.fontSizeMultiplier.toString()).replace("%%", "%"),
                valueFormat = labels.fontSizePercentFormat,
                iconKey = "font_size_multiplier",
            ),
            SettingsItem.SwitchRow(
                key = "hide_status_bar",
                title = labels.hideStatusBarTitle,
                summary = labels.hideStatusBarSummary,
                checked = s.hideStatusBar,
                iconKey = "hide_status_bar",
            ),
            SettingsItem.SwitchRow(
                key = "full_screen_hide_buttons_pref",
                title = labels.fullScreenHideButtonsTitle,
                summary = labels.fullScreenHideButtonsSummary,
                checked = s.fullScreenHideButtons,
                iconKey = "full_screen_hide_buttons_pref",
            ),
            SettingsItem.SwitchRow(
                key = "hide_window_buttons",
                title = labels.hideWindowButtonsTitle,
                summary = labels.hideWindowButtonsSummary,
                checked = s.hideWindowButtons,
                iconKey = "hide_window_buttons",
            ),
            SettingsItem.SwitchRow(
                key = "hide_bible_reference_overlay",
                title = labels.hideBibleReferenceOverlayTitle,
                summary = labels.hideBibleReferenceOverlaySummary,
                checked = s.hideBibleReferenceOverlay,
                iconKey = "hide_bible_reference_overlay",
            ),
            SettingsItem.SwitchRow(
                key = "show_active_window_indicator",
                title = labels.showActiveWindowIndicatorTitle,
                summary = labels.showActiveWindowIndicatorSummary,
                checked = s.showActiveWindowIndicator,
                iconKey = "show_active_window_indicator",
            ),
            SettingsItem.MultiSelectRow(
                key = "disable_bible_bookmark_modal_buttons",
                title = labels.disableBibleBookmarkModalButtonsTitle,
                summary = labels.disableBibleBookmarkModalButtonsSummary,
                options = s.bibleBookmarkModalOptions.optionChoices(),
                selectedValues = s.enabledBibleBookmarkModalButtons,
                iconKey = "disable_bible_bookmark_modal_buttons",
            ),
            SettingsItem.MultiSelectRow(
                key = "disable_gen_bookmark_modal_buttons",
                title = labels.disableGenBookmarkModalButtonsTitle,
                summary = labels.disableGenBookmarkModalButtonsSummary,
                options = s.genBookmarkModalOptions.optionChoices(),
                selectedValues = s.enabledGenBookmarkModalButtons,
                iconKey = "disable_gen_bookmark_modal_buttons",
            ),
            // ---- E-ink ----
            SettingsItem.Category(
                key = "prefs_eink_settings_cat",
                title = labels.einkCat,
                visible = true,
            ),
            SettingsItem.ListChoiceRow(
                key = "display_color_mode",
                title = labels.displayColorModeTitle,
                summary = labels.displayColorModeSummary,
                entries = s.displayColorModeChoices.entryChoices(),
                selectedValue = s.displayColorMode,
                iconKey = "display_color_mode",
            ),
            SettingsItem.SwitchRow(
                key = "eink_mode",
                title = labels.einkModeTitle,
                summary = labels.einkModeSummary,
                checked = s.einkMode,
                iconKey = "eink_mode",
            ),
            SettingsItem.SwitchRow(
                key = "disable_animations",
                title = labels.disableAnimationsTitle,
                summary = labels.disableAnimationsSummary,
                checked = s.disableAnimations,
                iconKey = "disable_animations",
            ),
            // ---- Persecution ----
            // Classic parity: the category header, discrete_help and calculator_pin are always
            // visible; only discrete_mode + show_calculator are hidden in a discrete build
            // (so calculator_pin, the PIN setter, stays reachable even then).
            SettingsItem.Category(
                key = "prefs_persecution_cat",
                title = labels.persecutionCat,
                visible = true,
            ),
            SettingsItem.InfoRow(
                key = "discrete_help",
                title = labels.discreteHelpTitle,
                summary = labels.discreteHelpSummary,
                onClickKey = AppSettingsNav.DISCRETE_HELP,
                visible = true,
                iconKey = "discrete_help",
            ),
            SettingsItem.SwitchRow(
                key = "discrete_mode",
                title = labels.discreteModeTitle,
                summary = labels.discreteModeSummary,
                checked = s.discreteMode,
                visible = s.discreteTogglesVisible,
                iconKey = "discrete_mode",
            ),
            SettingsItem.SwitchRow(
                key = "show_calculator",
                title = labels.showCalculatorTitle,
                checked = s.showCalculator,
                visible = s.discreteTogglesVisible,
                iconKey = "show_calculator",
            ),
            SettingsItem.TextInputRow(
                key = "calculator_pin",
                title = labels.calculatorPinTitle,
                summary = labels.calculatorPinSummary,
                value = s.calculatorPin,
                numeric = true,
                visible = true,
                iconKey = "calculator_pin",
            ),
            // ---- Features ----
            SettingsItem.Category(
                key = "prefs_features_cat",
                title = labels.featuresCat,
                visible = true,
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.SYNC,
                title = labels.syncShortcutTitle,
                summary = labels.syncShortcutSummary,
                iconKey = AppSettingsNav.SYNC,
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.AI,
                title = labels.aiShortcutTitle,
                summary = labels.aiShortcutSummary,
                iconKey = AppSettingsNav.AI,
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.READING_PROGRESS,
                title = labels.readingProgressShortcutTitle,
                summary = labels.readingProgressShortcutSummary,
                iconKey = AppSettingsNav.READING_PROGRESS,
            ),
            // ---- Advanced ----
            SettingsItem.Category(
                key = "prefs_advanced_settings_cat",
                title = labels.advancedCat,
                visible = true,
            ),
            SettingsItem.MultiSelectRow(
                key = "experimental_features",
                title = labels.experimentalFeaturesTitle,
                summary = labels.experimentalFeaturesSummary,
                options = s.experimentalFeatureOptions.optionChoices(),
                selectedValues = s.experimentalFeatures,
                iconKey = "experimental_features",
            ),
            SettingsItem.SwitchRow(
                key = "enable_bluetooth_pref",
                title = labels.enableBluetoothTitle,
                summary = labels.enableBluetoothSummary,
                checked = s.enableBluetooth,
                iconKey = "enable_bluetooth_pref",
            ),
            SettingsItem.SwitchRow(
                key = "request_sdcard_permission_pref",
                title = labels.requestSdcardPermissionTitle,
                summary = labels.requestSdcardPermissionSummary,
                checked = s.requestSdcardPermission,
                visible = s.sdcardPermissionVisible,
            ),
            SettingsItem.SwitchRow(
                key = "show_errorbox",
                title = labels.showErrorboxTitle,
                summary = labels.showErrorboxSummary,
                checked = s.showErrorbox,
                visible = s.betaFeaturesVisible,
                iconKey = "show_errorbox",
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.OPEN_LINKS,
                title = labels.openLinksTitle,
                summary = labels.openLinksSummary,
                visible = s.openLinksVisible,
                iconKey = AppSettingsNav.OPEN_LINKS,
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.CRASH_APP,
                title = labels.crashAppTitle,
                summary = labels.crashAppSummary,
                visible = s.betaFeaturesVisible,
                iconKey = AppSettingsNav.CRASH_APP,
            ),
        )
        return SettingsScreenState(title = labels.screenTitle, items = items)
    }

    /** All switch keys equal their classic pref key, so the write is a direct pass-through. */
    fun onSwitch(key: String, checked: Boolean) = service.setBool(key, checked)

    fun onListChoice(key: String, value: String) = service.setString(key, value)

    fun onTextInput(key: String, value: String) = service.setString("calculator_pin", value)

    fun onSliderChange(key: String, value: Int) = service.setInt("font_size_multiplier", value)

    /** Forwards the positive selected set; the service impl re-inverts inverse prefs. */
    fun onMultiSelectChange(key: String, values: Set<String>) = service.setStringSet(key, values)

    fun onNavigate(key: String) = onNavigate.invoke(key)
}
