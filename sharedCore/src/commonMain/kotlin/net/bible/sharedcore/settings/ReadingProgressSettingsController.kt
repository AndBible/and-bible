package net.bible.sharedcore.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Builds the declarative [SettingsScreenState] for the reading-progress/memorization settings
 * screen from a [ReadingProgressSettingsService] snapshot. This is the classic screen with no
 * categories and no navigation rows — flat list of six items in the classic preference order.
 * Modelled on `net.bible.sharedcore.ai.AiConnectionSettingsController`.
 */
class ReadingProgressSettingsController(
    private val service: ReadingProgressSettingsService,
    private val scope: CoroutineScope,
    private val labels: ReadingProgressSettingsLabels,
) {
    private val _state = MutableStateFlow(build(service.snapshot.value))
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        scope.launch { service.snapshot.collect { _state.value = build(it) } }
    }

    private fun build(s: ReadingProgressSettingsSnapshot): SettingsScreenState {
        val items = listOf(
            SettingsItem.SwitchRow(
                key = "auto_mark_memorized",
                title = labels.autoMarkMemorizedTitle,
                summary = labels.autoMarkMemorizedSummary,
                checked = s.autoMarkMemorized,
                iconKey = "auto_mark_memorized",
            ),
            SettingsItem.SwitchRow(
                key = "memorize_type_full_words",
                title = labels.memorizeTypeFullWordsTitle,
                summary = labels.memorizeTypeFullWordsSummary,
                checked = s.memorizeTypeFullWords,
                iconKey = "memorize_type_full_words",
            ),
            SettingsItem.ListChoiceRow(
                key = "memorize_word_visibility",
                title = labels.memorizeWordVisibilityTitle,
                summary = labels.memorizeWordVisibilitySummary,
                entries = s.memorizeWordVisibilityChoices.map { SettingsItem.Choice(it.value, it.label) },
                selectedValue = s.memorizeWordVisibility,
                iconKey = "memorize_word_visibility",
            ),
            SettingsItem.SwitchRow(
                key = "memorize_error_heatmap",
                title = labels.memorizeErrorHeatmapTitle,
                summary = labels.memorizeErrorHeatmapSummary,
                checked = s.memorizeErrorHeatmap,
                iconKey = "memorize_error_heatmap",
            ),
            SettingsItem.SwitchRow(
                key = "memorize_scramble_hide_used",
                title = labels.memorizeScrambleHideUsedTitle,
                summary = labels.memorizeScrambleHideUsedSummary,
                checked = s.memorizeScrambleHideUsed,
                iconKey = "memorize_scramble_hide_used",
            ),
            SettingsItem.SwitchRow(
                key = "memorize_include_reference",
                title = labels.memorizeIncludeReferenceTitle,
                summary = labels.memorizeIncludeReferenceSummary,
                checked = s.memorizeIncludeReference,
                iconKey = "memorize_include_reference",
            ),
        )
        return SettingsScreenState(title = labels.screenTitle, items = items)
    }

    /** All switch keys equal their classic pref key, so the write is a direct pass-through. */
    fun onSwitch(key: String, checked: Boolean) = service.setBool(key, checked)

    fun onListChoice(key: String, value: String) = service.setString(key, value)
}
