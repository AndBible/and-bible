package net.bible.service.device.speak

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The merge and sentence-split rules of [SpeakCommandArray]. They rely on regexes with `\p{Pf}` and CJK punctuation,
 * so running them on Native checks that engine too.
 */
class SpeakCommandArrayCommonTest {
    private fun array(maxLength: Int = 20) = SpeakCommandArray(maxLength)

    private fun List<SpeakCommand>.describe(): List<String> = map { if (it is TextCommand) "T:${it.text}" else "S" }

    @Test
    fun addMergesTextIntoTheLastTextCommand() {
        val a = array()
        assertTrue(a.add(TextCommand("Hi")))
        assertTrue(a.add(TextCommand("there")))
        assertEquals(listOf("T:Hi there"), a.describe())
    }

    @Test
    fun addDoesNotMergePastMaxLength() {
        val a = array()
        a.add(TextCommand("aaaaaaaaaaaaaaa"))
        a.add(TextCommand("bbbbbb"))
        assertEquals(listOf("T:aaaaaaaaaaaaaaa", "T:bbbbbb"), a.describe())
    }

    @Test
    fun aLeadingDelimiterIsAppendedWithoutSpace() {
        val a = array()
        a.add(TextCommand("Hi"))
        a.add(TextCommand(", you"))
        a.add(TextCommand("。"))
        assertEquals(listOf("T:Hi, you。"), a.describe())
    }

    @Test
    fun secondConsecutiveSilenceIsDropped() {
        val a = array()
        assertTrue(a.add(SilenceCommand()))
        assertFalse(a.add(SilenceCommand()))
        assertEquals(1, a.size)
    }

    @Test
    fun addUntilSentenceBreakSplitsAtTheFirstSentenceEnd() {
        val a = array()
        val rest = ArrayList<SpeakCommand>()
        a.addUntilSentenceBreak(arrayListOf<SpeakCommand>(TextCommand("One. Two")), rest)
        assertEquals(listOf("T:One."), a.describe())
        assertEquals(listOf("T:Two"), rest.describe())
    }

    @Test
    fun addUntilSentenceBreakSplitsAfterTheIdeographicFullStop() {
        val a = array()
        val rest = ArrayList<SpeakCommand>()
        a.addUntilSentenceBreak(arrayListOf<SpeakCommand>(TextCommand("Go。 Next")), rest)
        assertEquals(listOf("T:Go。"), a.describe())
        assertEquals(listOf("T:Next"), rest.describe().map { it.trim() })
    }

    @Test
    fun aClosingQuoteStaysWithItsSentence() {
        val a = array()
        val rest = ArrayList<SpeakCommand>()
        // U+201D is a final-quote punctuation character (\p{Pf}); it belongs to the first sentence.
        a.addUntilSentenceBreak(arrayListOf<SpeakCommand>(TextCommand("He said stop.” Then left")), rest)
        assertEquals(listOf("T:He said stop.”"), a.describe())
        assertEquals(listOf("T:Then left"), rest.describe().map { it.trim() })
    }

    @Test
    fun aTextWithoutSentenceBreakIsKeptWholeAndNothingIsLeftOver() {
        val a = array()
        val rest = ArrayList<SpeakCommand>()
        a.addUntilSentenceBreak(arrayListOf<SpeakCommand>(TextCommand("no break here")), rest)
        assertEquals(listOf("T:no break here"), a.describe())
        assertTrue(rest.isEmpty())
    }

    @Test
    fun endsSentenceRecognisesIdeographicAndQuotedEnds() {
        val a = array(maxLength = 100)
        a.add(TextCommand("Go。"))
        assertTrue(a.endsSentence)
        val b = array(maxLength = 100)
        b.add(TextCommand("Stop.” "))
        assertTrue(b.endsSentence)
        val c = array(maxLength = 100)
        c.add(TextCommand("and then"))
        assertFalse(c.endsSentence)
    }
}
