package net.bible.service.format.osistohtml.osishandlers

import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class OsisToSpeakTextSaxHandlerTest {
    private var osisToSpeakTextSaxHandler: OsisToSpeakTextSaxHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToSpeakTextSaxHandler = OsisToSpeakTextSaxHandler(true)
    }

    @Test
    @Throws(Exception::class)
    fun testTopLevelReferenceIsWritten() {
        osisToSpeakTextSaxHandler!!.startDocument()
        osisToSpeakTextSaxHandler!!.startElement(null, null, "reference", null)
        osisToSpeakTextSaxHandler!!.characters("something".toCharArray(), 0, 9)
        osisToSpeakTextSaxHandler!!.endElement(null, null, "reference")
        osisToSpeakTextSaxHandler!!.endDocument()
        Assert.assertThat(osisToSpeakTextSaxHandler!!.writer.html, CoreMatchers.equalTo("something"))
    }

    @Test
    @Throws(Exception::class)
    fun testNoteWithReferenceIsAlsoWritten() {
        osisToSpeakTextSaxHandler!!.startDocument()
        osisToSpeakTextSaxHandler!!.startElement(null, null, "note", null)
        osisToSpeakTextSaxHandler!!.characters("inNoteBeforeRef".toCharArray(), 0, 15)
        osisToSpeakTextSaxHandler!!.startElement(null, null, "reference", null)
        osisToSpeakTextSaxHandler!!.characters("something".toCharArray(), 0, 9)
        osisToSpeakTextSaxHandler!!.endElement(null, null, "reference")
        osisToSpeakTextSaxHandler!!.characters("inNoteAfterRef".toCharArray(), 0, 14)
        osisToSpeakTextSaxHandler!!.endElement(null, null, "note")
        osisToSpeakTextSaxHandler!!.endDocument()
        Assert.assertThat(osisToSpeakTextSaxHandler!!.writer.html, CoreMatchers.equalTo("something"))
    }
}
