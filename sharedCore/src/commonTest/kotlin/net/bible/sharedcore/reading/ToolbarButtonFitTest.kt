package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolbarButtonFitTest {
    private fun state(
        showBible: Boolean = true, showCommentary: Boolean = true, showStrongs: Boolean = true,
        searchable: Boolean = true, speakable: Boolean = true, speakStopped: Boolean = true,
    ) = ToolbarState("Gen 1", "ESV", false, showBible, showCommentary, showStrongs, 0, searchable, speakable, speakStopped)

    // density 2.0 → each button ≈ 106px; width 1000 → maxWidth 500 → maxButtons = 500/106 = 4
    @Test fun fitsUpToBudgetInOrder() {
        val r = fitToolbarButtons(state(), screenWidthPx = 1000, density = 2f, searchMoreRecent = true)
        assertEquals(listOf(ToolbarButton.BIBLE, ToolbarButton.COMMENTARY, ToolbarButton.STRONGS, ToolbarButton.SEARCH), r)
    }

    @Test fun searchSpeakOrderedByRecency() {
        // narrow: only 1 slot after none of bible/comm/strongs (all hidden)
        val s = state(showBible = false, showCommentary = false, showStrongs = false)
        val searchFirst = fitToolbarButtons(s, screenWidthPx = 300, density = 2f, searchMoreRecent = true)
        val speakFirst = fitToolbarButtons(s, screenWidthPx = 300, density = 2f, searchMoreRecent = false)
        assertEquals(listOf(ToolbarButton.SEARCH), searchFirst)   // 300*0.5/106 = 1
        assertEquals(listOf(ToolbarButton.SPEAK), speakFirst)
    }

    @Test fun capabilityGatingHidesButtons() {
        val s = state(showStrongs = false, speakable = false)
        val r = fitToolbarButtons(s, screenWidthPx = 100000, density = 2f, searchMoreRecent = true)
        assertEquals(listOf(ToolbarButton.BIBLE, ToolbarButton.COMMENTARY, ToolbarButton.SEARCH, ToolbarButton.WORKSPACE), r)
    }

    @Test fun speakRequiresStopped() {
        val s = state(showBible = false, showCommentary = false, showStrongs = false, searchable = false, speakStopped = false)
        val r = fitToolbarButtons(s, screenWidthPx = 100000, density = 2f, searchMoreRecent = false)
        assertEquals(listOf(ToolbarButton.WORKSPACE), r) // speak excluded (not stopped), search excluded (not searchable)
    }
}
