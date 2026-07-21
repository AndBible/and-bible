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
 * straight from the snapshot (`hasAnyDictionary`, `persecutionVisible`, `calculatorPinVisible`,
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
            ),
            SettingsItem.MultiSelectRow(
                key = "strongs_hebrew_dictionary",
                title = labels.strongsHebrewDictionaryTitle,
                summary = labels.strongsHebrewDictionarySummary,
                options = s.hebrewDictOptions.optionChoices(),
                selectedValues = s.hebrewDicts,
                visible = s.hasAnyDictionary,
            ),
            SettingsItem.MultiSelectRow(
                key = "robinson_greek_morphology",
                title = labels.robinsonGreekMorphologyTitle,
                summary = labels.robinsonGreekMorphologySummary,
                options = s.greekMorphOptions.optionChoices(),
                selectedValues = s.greekMorph,
                visible = s.hasAnyDictionary,
            ),
            SettingsItem.MultiSelectRow(
                key = "disabled_word_lookup_dictionaries",
                title = labels.disabledWordLookupDictionariesTitle,
                summary = labels.disabledWordLookupDictionariesSummary,
                options = s.wordLookupDictOptions.optionChoices(),
                selectedValues = s.enabledWordLookupDicts,
                visible = s.hasAnyDictionary,
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
            ),
            SettingsItem.SwitchRow(
                key = "open_links_in_special_window_pref",
                title = labels.openLinksInSpecialWindowTitle,
                summary = labels.openLinksInSpecialWindowSummary,
                checked = s.openLinksInSpecialWindow,
            ),
            SettingsItem.SwitchRow(
                key = "screen_keep_on_pref",
                title = labels.screenKeepOnTitle,
                summary = labels.screenKeepOnSummary,
                checked = s.screenKeepOn,
            ),
            SettingsItem.SwitchRow(
                key = "double_tap_to_fullscreen",
                title = labels.doubleTapToFullscreenTitle,
                summary = labels.doubleTapToFullscreenSummary,
                checked = s.doubleTapToFullscreen,
            ),
            SettingsItem.SwitchRow(
                key = "auto_fullscreen_pref",
                title = labels.autoFullscreenTitle,
                summary = labels.autoFullscreenSummary,
                checked = s.autoFullscreen,
            ),
            SettingsItem.ListChoiceRow(
                key = "toolbar_button_actions",
                title = labels.toolbarButtonActionsTitle,
                summary = labels.toolbarButtonActionsSummary,
                entries = s.toolbarButtonActionChoices.entryChoices(),
                selectedValue = s.toolbarButtonActions,
            ),
            SettingsItem.ListChoiceRow(
                key = "bible_view_swipe_mode",
                title = labels.bibleViewSwipeModeTitle,
                summary = labels.bibleViewSwipeModeSummary,
                entries = s.bibleViewSwipeModeChoices.entryChoices(),
                selectedValue = s.bibleViewSwipeMode,
            ),
            SettingsItem.SwitchRow(
                key = "disable_two_step_bookmarking",
                title = labels.disableTwoStepBookmarkingTitle,
                summary = labels.disableTwoStepBookmarkingSummary,
                checked = s.disableTwoStepBookmarking,
            ),
            SettingsItem.SwitchRow(
                key = "volume_keys_scroll",
                title = labels.volumeKeysScrollTitle,
                summary = labels.volumeKeysScrollSummary,
                checked = s.volumeKeysScroll,
            ),
            SettingsItem.ListChoiceRow(
                key = "night_mode_pref3",
                title = labels.nightModeTitle,
                summary = labels.nightModeSummary,
                entries = s.nightModeChoices.entryChoices(),
                selectedValue = s.nightMode,
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
            ),
            SettingsItem.ListChoiceRow(
                key = "locale_pref",
                title = labels.localeTitle,
                summary = labels.localeSummary,
                entries = s.localeChoices.entryChoices(),
                selectedValue = s.locale,
            ),
            SettingsItem.SwitchRow(
                key = "disable_click_to_edit",
                title = labels.disableClickToEditTitle,
                summary = labels.disableClickToEditSummary,
                checked = s.disableClickToEdit,
            ),
            SettingsItem.ListChoiceRow(
                key = "notes_content_type",
                title = labels.notesContentTypeTitle,
                summary = labels.notesContentTypeSummary,
                entries = s.notesContentTypeChoices.entryChoices(),
                selectedValue = s.notesContentType,
            ),
            SettingsItem.SliderRow(
                key = "font_size_multiplier",
                title = labels.fontSizeMultiplierTitle,
                value = s.fontSizeMultiplier,
                min = 10,
                max = 500,
                valueLabel = labels.fontSizePercentFormat.replace("%d", s.fontSizeMultiplier.toString()),
            ),
            SettingsItem.SwitchRow(
                key = "hide_status_bar",
                title = labels.hideStatusBarTitle,
                summary = labels.hideStatusBarSummary,
                checked = s.hideStatusBar,
            ),
            SettingsItem.SwitchRow(
                key = "full_screen_hide_buttons_pref",
                title = labels.fullScreenHideButtonsTitle,
                summary = labels.fullScreenHideButtonsSummary,
                checked = s.fullScreenHideButtons,
            ),
            SettingsItem.SwitchRow(
                key = "hide_window_buttons",
                title = labels.hideWindowButtonsTitle,
                summary = labels.hideWindowButtonsSummary,
                checked = s.hideWindowButtons,
            ),
            SettingsItem.SwitchRow(
                key = "hide_bible_reference_overlay",
                title = labels.hideBibleReferenceOverlayTitle,
                summary = labels.hideBibleReferenceOverlaySummary,
                checked = s.hideBibleReferenceOverlay,
            ),
            SettingsItem.SwitchRow(
                key = "show_active_window_indicator",
                title = labels.showActiveWindowIndicatorTitle,
                summary = labels.showActiveWindowIndicatorSummary,
                checked = s.showActiveWindowIndicator,
            ),
            SettingsItem.MultiSelectRow(
                key = "disable_bible_bookmark_modal_buttons",
                title = labels.disableBibleBookmarkModalButtonsTitle,
                summary = labels.disableBibleBookmarkModalButtonsSummary,
                options = s.bibleBookmarkModalOptions.optionChoices(),
                selectedValues = s.enabledBibleBookmarkModalButtons,
            ),
            SettingsItem.MultiSelectRow(
                key = "disable_gen_bookmark_modal_buttons",
                title = labels.disableGenBookmarkModalButtonsTitle,
                summary = labels.disableGenBookmarkModalButtonsSummary,
                options = s.genBookmarkModalOptions.optionChoices(),
                selectedValues = s.enabledGenBookmarkModalButtons,
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
            ),
            SettingsItem.SwitchRow(
                key = "eink_mode",
                title = labels.einkModeTitle,
                summary = labels.einkModeSummary,
                checked = s.einkMode,
            ),
            SettingsItem.SwitchRow(
                key = "disable_animations",
                title = labels.disableAnimationsTitle,
                summary = labels.disableAnimationsSummary,
                checked = s.disableAnimations,
            ),
            // ---- Persecution ----
            SettingsItem.Category(
                key = "prefs_persecution_cat",
                title = labels.persecutionCat,
                visible = s.persecutionVisible,
            ),
            SettingsItem.InfoRow(
                key = "discrete_help",
                title = labels.discreteHelpTitle,
                summary = labels.discreteHelpSummary,
                onClickKey = AppSettingsNav.DISCRETE_HELP,
                visible = s.persecutionVisible,
            ),
            SettingsItem.SwitchRow(
                key = "discrete_mode",
                title = labels.discreteModeTitle,
                summary = labels.discreteModeSummary,
                checked = s.discreteMode,
                visible = s.persecutionVisible,
            ),
            SettingsItem.SwitchRow(
                key = "show_calculator",
                title = labels.showCalculatorTitle,
                checked = s.showCalculator,
                visible = s.persecutionVisible,
            ),
            SettingsItem.TextInputRow(
                key = "calculator_pin",
                title = labels.calculatorPinTitle,
                summary = labels.calculatorPinSummary,
                value = s.calculatorPin,
                numeric = true,
                visible = s.persecutionVisible && s.calculatorPinVisible,
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
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.AI,
                title = labels.aiShortcutTitle,
                summary = labels.aiShortcutSummary,
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.READING_PROGRESS,
                title = labels.readingProgressShortcutTitle,
                summary = labels.readingProgressShortcutSummary,
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
            ),
            SettingsItem.SwitchRow(
                key = "enable_bluetooth_pref",
                title = labels.enableBluetoothTitle,
                summary = labels.enableBluetoothSummary,
                checked = s.enableBluetooth,
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
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.OPEN_LINKS,
                title = labels.openLinksTitle,
                summary = labels.openLinksSummary,
                visible = s.openLinksVisible,
            ),
            SettingsItem.NavigationRow(
                key = AppSettingsNav.CRASH_APP,
                title = labels.crashAppTitle,
                summary = labels.crashAppSummary,
                visible = s.betaFeaturesVisible,
            ),
            // ---- Developer ----
            SettingsItem.Category(
                key = "prefs_category_developer",
                title = labels.developerCat,
                visible = true,
            ),
            SettingsItem.SwitchRow(
                key = "use_compose_ui",
                title = labels.useComposeUiTitle,
                summary = labels.useComposeUiSummary,
                checked = s.useComposeUi,
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
