package net.bible.service.format.osistohtml

import fi.sykero.kdom.sax.AttributesImpl
import net.bible.service.format.SaxTextPolicy
import net.bible.service.format.osistohtml.osishandlers.OsisToCopyTextSaxHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class OsisToCopyTextCommonTest {
    private fun handler(showVerseNumbers: Boolean) =
        OsisToCopyTextSaxHandler(showVerseNumbers, SaxTextPolicy.MODERN).also { it.startDocument() }

    private fun osisId(id: String) = AttributesImpl().apply { addAttribute("", "osisID", "osisID", "CDATA", id) }

    private fun OsisToCopyTextSaxHandler.verse(attributes: AttributesImpl, text: String) {
        startElement("", "", "verse", attributes)
        characters(text.toCharArray(), 0, text.length)
        endElement("", "", "verse")
    }

    @Test
    fun verseNumbersAreInferredAcrossVerses() {
        val h = handler(showVerseNumbers = true)
        // A verse without an osisID gets number 0, and must not break the inference of a following ordinary verse.
        h.verse(AttributesImpl(), "text")
        h.verse(osisId("Rom.8.28"), "ordinary")
        h.endDocument()

        assertEquals("0. text 28. ordinary ", h.writer.html)
    }

    @Test
    fun singleVerseWithoutNumbers() {
        val h = handler(showVerseNumbers = false)
        h.startElement("", "", "chapter", osisId("Rom.8"))
        h.startElement("", "", "verse", osisId("Rom.8.28"))
        h.characters("And we know that all things...".toCharArray(), 0, 30)
        h.characters(" ".toCharArray(), 0, 1)
        h.endElement("", "", "verse")

        assertEquals("And we know that all things... ", h.writer.html)
    }

    @Test
    fun consecutiveVersesAreNumberedWithTheirOwnVerseNumber() {
        val h = handler(showVerseNumbers = true)
        h.startElement("", "", "chapter", osisId("Rom.8"))
        h.verse(osisId("Rom.8.28"), "One")
        h.verse(osisId("Rom.8.29"), "Two")
        h.endDocument()

        assertEquals("28. One 29. Two ", h.writer.html)
    }

    @Test
    fun textWriterInsertsAtAnEarlierOffsetAndRestoresTheTail() {
        val w = TextWriter()
        w.write("ab")
        val afterAb = w.position
        w.write("kl")
        w.beginInsertAt(afterAb)
        w.write("cd")
        w.write("ef")
        // Already inserting: the nested request is ignored.
        w.beginInsertAt(w.position)
        w.write("gh")
        w.finishInserting()
        w.write("ij")
        w.finishInserting()
        w.write("mn")

        assertEquals("abcdefghijklmn", w.html)
    }

    @Test
    fun textWriterRemoveAfterAndTempStoreRemoveEverythingPastTheirPosition() {
        val w = TextWriter()
        w.write("abcdef")
        w.removeAfter(2)
        assertEquals("ab", w.html)

        w.writeToTempStore()
        w.write("temp")
        w.finishWritingToTempStore()
        assertEquals("temp", w.tempStoreString)
        w.clearTempStore()
        assertEquals("", w.tempStoreString)
        assertEquals("ab", w.html)
    }
}
