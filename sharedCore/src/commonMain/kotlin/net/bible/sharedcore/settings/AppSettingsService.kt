package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.StateFlow

/** One installed dictionary/morphology book offered in a dictionary multi-select. */
data class DictOption(val initials: String, val name: String)

/** Immutable snapshot the main app-settings controller renders from. */
data class AppSettingsSnapshot(
    // Dictionaries (positive selected sets; inverse prefs already converted to positive by the impl)
    val greekDicts: Set<String>,
    val hebrewDicts: Set<String>,
    val greekMorph: Set<String>,
    val enabledWordLookupDicts: Set<String>,          // inverse of disabled_word_lookup_dictionaries
    val greekDictOptions: List<DictOption>,
    val hebrewDictOptions: List<DictOption>,
    val greekMorphOptions: List<DictOption>,
    val wordLookupDictOptions: List<DictOption>,
    val hasAnyDictionary: Boolean,                    // false → hide the Dictionaries category
    // Behavior
    val navigateToVerse: Boolean,
    val openLinksInSpecialWindow: Boolean,
    val screenKeepOn: Boolean,
    val doubleTapToFullscreen: Boolean,
    val autoFullscreen: Boolean,
    val toolbarButtonActions: String,
    val bibleViewSwipeMode: String,
    val disableTwoStepBookmarking: Boolean,
    val volumeKeysScroll: Boolean,
    val nightMode: String,
    // Display
    val locale: String,
    val disableClickToEdit: Boolean,
    val notesContentType: String,
    val fontSizeMultiplier: Int,
    val hideStatusBar: Boolean,
    val fullScreenHideButtons: Boolean,
    val hideWindowButtons: Boolean,
    val hideBibleReferenceOverlay: Boolean,
    val showActiveWindowIndicator: Boolean,
    val enabledBibleBookmarkModalButtons: Set<String>, // inverse of disable_bible_bookmark_modal_buttons
    val enabledGenBookmarkModalButtons: Set<String>,   // inverse of disable_gen_bookmark_modal_buttons
    val bibleBookmarkModalOptions: List<DictOption>,
    val genBookmarkModalOptions: List<DictOption>,
    // E-ink
    val displayColorMode: String,
    val einkMode: Boolean,
    val disableAnimations: Boolean,
    // Persecution
    val discreteMode: Boolean,
    val showCalculator: Boolean,
    val calculatorPin: String,
    // Features / advanced
    val experimentalFeatures: Set<String>,
    val experimentalFeatureOptions: List<DictOption>,
    val enableBluetooth: Boolean,
    val requestSdcardPermission: Boolean,
    val showErrorbox: Boolean,
    // choice entries (label lists come from the host; values fixed by classic array resources)
    val toolbarButtonActionChoices: List<Choice2>,
    val bibleViewSwipeModeChoices: List<Choice2>,
    val nightModeChoices: List<Choice2>,               // already swapped for autoModeAvailable by the impl
    val localeChoices: List<Choice2>,
    val notesContentTypeChoices: List<Choice2>,
    val displayColorModeChoices: List<Choice2>,
    // visibility flags (host-provided; controller does not compute these)
    // Classic parity: only discrete_mode + show_calculator are gated (hidden in discrete builds);
    // the persecution category header, discrete_help and calculator_pin are ALWAYS visible.
    val discreteTogglesVisible: Boolean,               // discrete_mode + show_calculator shown (= !isDiscrete)
    val betaFeaturesVisible: Boolean,                  // crash_app + show_errorbox
    val sdcardPermissionVisible: Boolean,
    val openLinksVisible: Boolean,
)

/** value/label pair mirrored from a classic entries/entryValues array pair. */
data class Choice2(val value: String, val label: String)

interface AppSettingsService {
    val snapshot: StateFlow<AppSettingsSnapshot>
    fun setBool(key: String, value: Boolean)
    fun setString(key: String, value: String)
    fun setInt(key: String, value: Int)
    /** Writes a POSITIVE selection set; the impl converts back to the inverse form when [key] is an inverse pref. */
    fun setStringSet(key: String, value: Set<String>)
    fun refresh()
}
