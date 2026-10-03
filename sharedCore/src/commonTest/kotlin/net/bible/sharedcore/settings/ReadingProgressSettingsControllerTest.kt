package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeReadingProgressSettingsService(initial: ReadingProgressSettingsSnapshot) : ReadingProgressSettingsService {
    val _snap = MutableStateFlow(initial)
    override val snapshot: StateFlow<ReadingProgressSettingsSnapshot> get() = _snap
    val boolWrites = mutableListOf<Pair<String, Boolean>>()
    val stringWrites = mutableListOf<Pair<String, String>>()
    override fun setBool(key: String, value: Boolean) { boolWrites += key to value }
    override fun setString(key: String, value: String) { stringWrites += key to value }
    override fun refresh() {}
}

class ReadingProgressSettingsControllerTest {
    private fun snap(
        autoMarkMemorized: Boolean = true,
        memorizeTypeFullWords: Boolean = false,
        memorizeWordVisibility: String = "normal",
        memorizeErrorHeatmap: Boolean = true,
        memorizeScrambleHideUsed: Boolean = false,
        memorizeIncludeReference: Boolean = true,
    ) = ReadingProgressSettingsSnapshot(
        autoMarkMemorized = autoMarkMemorized,
        memorizeTypeFullWords = memorizeTypeFullWords,
        memorizeWordVisibility = memorizeWordVisibility,
        memorizeWordVisibilityChoices = emptyList(),
        memorizeErrorHeatmap = memorizeErrorHeatmap,
        memorizeScrambleHideUsed = memorizeScrambleHideUsed,
        memorizeIncludeReference = memorizeIncludeReference,
    )

    private fun controller(s: ReadingProgressSettingsService) =
        ReadingProgressSettingsController(
            s,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ReadingProgressSettingsLabels.forTest(),
        )

    @Test fun buildsSixRowsInOrder() {
        val keys = controller(FakeReadingProgressSettingsService(snap())).state.value.visibleItems.map { it.key }
        assertEquals(
            listOf(
                "auto_mark_memorized", "memorize_type_full_words", "memorize_word_visibility",
                "memorize_error_heatmap", "memorize_scramble_hide_used", "memorize_include_reference",
            ),
            keys,
        )
    }

    @Test fun visibilityRowIsListChoice() {
        val row = controller(FakeReadingProgressSettingsService(snap(memorizeWordVisibility = "light"))).state.value.items
            .filterIsInstance<SettingsItem.ListChoiceRow>().single()
        assertEquals("memorize_word_visibility", row.key)
        assertEquals("light", row.selectedValue)
    }

    @Test fun switchWriteGoesThroughByKey() {
        val svc = FakeReadingProgressSettingsService(snap())
        controller(svc).onSwitch("memorize_error_heatmap", false)
        assertEquals("memorize_error_heatmap" to false, svc.boolWrites.single())
    }

    @Test fun listChoiceWriteGoesThroughByKey() {
        val svc = FakeReadingProgressSettingsService(snap())
        controller(svc).onListChoice("memorize_word_visibility", "full")
        assertEquals("memorize_word_visibility" to "full", svc.stringWrites.single())
    }

    /** Round 14b: all six rows take their own key as `iconKey` (see `res/xml/reading_progress_settings.xml`). */
    @Test fun everyRowCarriesItsOwnKeyAsIconKey() {
        val items = controller(FakeReadingProgressSettingsService(snap())).state.value.items
        assertEquals(6, items.size)
        assertEquals(emptyList(), items.filter { it.iconKeyOrNull() != it.key }.map { it.key })
    }
}
