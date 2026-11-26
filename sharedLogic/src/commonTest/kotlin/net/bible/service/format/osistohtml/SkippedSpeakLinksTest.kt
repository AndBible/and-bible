package net.bible.service.format.osistohtml

import net.bible.service.format.osistohtml.osishandlers.SkippedSpeakLinks
import kotlin.test.Test
import kotlin.test.assertEquals

class SkippedSpeakLinksTest {
    @Test
    fun cleanupSpansOrdinalsWithoutMovingRemainingText() {
        val marker = SkippedSpeakLinks.marker
        assertEquals(listOf("Before ", "", "", "", "after."),
            SkippedSpeakLinks.cleanParts(listOf("Before [see ", marker, ", ", marker, "] after.")))
        assertEquals(listOf("Before ", "after."), SkippedSpeakLinks.cleanParts(listOf("Before (see $marker", ") after.")))
    }

    @Test
    fun onlyEmptyBracketedIntroductionsAreRemoved() {
        val marker = SkippedSpeakLinks.marker
        for (intro in listOf("see", "see also", "cf.", "SEE ALSO")) {
            assertEquals("Before after.", SkippedSpeakLinks.clean("Before [$intro $marker] after."), intro)
            assertEquals("Before ($intro explanation) after.", SkippedSpeakLinks.clean("Before ($intro $marker explanation) after."), intro)
            assertEquals("Before $intro after.", SkippedSpeakLinks.clean("Before $intro $marker after."), intro)
        }
    }

    @Test
    fun whitespaceAwayFromSkippedLinksIsUntouched() {
        val marker = SkippedSpeakLinks.marker
        assertEquals("Keep  two\t spaces , ( aside ) before after.",
            SkippedSpeakLinks.clean("Keep  two\t spaces , ( aside ) before [$marker] after."))
    }

    @Test
    fun cleanupKeepsRealWordsAndUnrelatedPunctuation() {
        val marker = SkippedSpeakLinks.marker
        assertEquals("Keep (real words) after.", SkippedSpeakLinks.clean("Keep (real words $marker) after."))
        assertEquals("A (real aside), and [another].", SkippedSpeakLinks.clean("A (real aside), and [another]."))
        assertEquals("Before after.", SkippedSpeakLinks.clean("Before {see ($marker), [$marker]} after."))
        assertEquals("Before see after.", SkippedSpeakLinks.clean("Before see $marker after."))
        assertEquals("Before, after.", SkippedSpeakLinks.clean("Before, $marker, after."))
        assertEquals("Before (explanation) after.", SkippedSpeakLinks.clean("Before ($marker; explanation) after."))
        assertEquals("", SkippedSpeakLinks.clean("(See $marker)."))
    }
}
