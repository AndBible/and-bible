package net.bible.service.device.speak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the merge semantics of [SpeakCommandArray] (it must behave the same whatever list it is built on). */
class SpeakCommandArrayTest {
    private fun array() = SpeakCommandArray(20)

    private fun SpeakCommandArray.describe(): List<String> = map {
        when (it) {
            is TextCommand -> "T:${it.text}"
            is SilenceCommand -> "S"
            else -> it.javaClass.simpleName
        }
    }

    private fun List<SpeakCommand>.texts(): List<String> = map { if (it is TextCommand) "T:${it.text}" else "S" }

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
    fun delimiterIsAppendedWithoutSpace() {
        val a = array()
        a.add(TextCommand("Hi"))
        a.add(TextCommand(", you"))
        assertEquals(listOf("T:Hi, you"), a.describe())
    }

    @Test
    fun secondConsecutiveSilenceIsDropped() {
        val a = array()
        assertTrue(a.add(SilenceCommand()))
        assertFalse(a.add(SilenceCommand()))
        assertEquals(1, a.size)
    }

    @Test
    fun emptyTextIsIgnored() {
        val a = array()
        a.add(TextCommand("x"))
        assertFalse(a.add(TextCommand("")))
        assertEquals(1, a.size)
    }

    @Test
    fun addAtIndexMergesWithTheCommandAtThatIndex() {
        val a = array()
        a.add(TextCommand("b"))
        a.add(0, TextCommand("a"))
        assertEquals(listOf("T:a b"), a.describe())
    }

    @Test
    fun addAllMergesElementByElement() {
        val a = array()
        a.addAll(listOf(TextCommand("a"), TextCommand("b"), SilenceCommand(), SilenceCommand()))
        assertEquals(listOf("T:a b", "S"), a.describe())
    }

    @Test
    fun copyIsEqualAndIndependent() {
        val a = array()
        a.add(TextCommand("a"))
        a.add(SilenceCommand())
        val c = a.copy()
        assertEquals(a.toString(), c.toString())
        assertEquals(a.describe(), c.describe())
        c.add(TextCommand("z"))
        assertEquals(listOf("T:a", "S"), a.describe())
        assertEquals(listOf("T:a", "S", "T:z"), c.describe())
    }

    @Test
    fun setReplacesWithoutMerging() {
        val a = array()
        a.add(TextCommand("a"))
        a[0] = TextCommand("x")
        assertEquals(listOf("T:x"), a.describe())
    }

    @Test
    fun addUntilSentenceBreakSplitsAtTheFirstSentenceEnd() {
        val a = array()
        val rest = ArrayList<SpeakCommand>()
        a.addUntilSentenceBreak(arrayListOf<SpeakCommand>(TextCommand("One. Two")), rest)
        assertEquals(listOf("T:One."), a.describe())
        assertEquals(listOf("T:Two"), rest.texts())
    }

    @Test
    fun removeAtAndIndexAccessWork() {
        val a = array()
        a.add(TextCommand("a"))
        a.add(SilenceCommand())
        a.removeAt(0)
        assertEquals(listOf("S"), a.describe())
        assertEquals(1, a.size)
    }
}
