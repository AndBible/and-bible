package net.bible.service.format.osistohtml.osishandlers

import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class OsisToCanonicalTextSaxHandlerTest {
    private var osisToCanonicalTextSaxHandler: OsisToCanonicalTextSaxHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToCanonicalTextSaxHandler = OsisToCanonicalTextSaxHandler()
        osisToCanonicalTextSaxHandler!!.startDocument()
    }

    @Test
    @Throws(Exception::class)
    fun testTopLevelReferenceIsWritten() {
        osisToCanonicalTextSaxHandler!!.startElement(null, null, "reference", null)
        osisToCanonicalTextSaxHandler!!.characters("something".toCharArray(), 0, 9)
        osisToCanonicalTextSaxHandler!!.endElement(null, null, "reference")
        Assert.assertThat(osisToCanonicalTextSaxHandler!!.writer.html, CoreMatchers.equalTo("something"))
    }

    @Test
    @Throws(Exception::class)
    fun testReferenceInNoteIsNotWritten() {
        osisToCanonicalTextSaxHandler!!.startElement(null, null, "note", null)
        osisToCanonicalTextSaxHandler!!.startElement(null, null, "reference", null)
        osisToCanonicalTextSaxHandler!!.characters("something".toCharArray(), 0, 9)
        osisToCanonicalTextSaxHandler!!.endElement(null, null, "reference")
        osisToCanonicalTextSaxHandler!!.endElement(null, null, "note")
        Assert.assertThat(osisToCanonicalTextSaxHandler!!.writer.html, Matchers.isEmptyString())
    }

    @Test
    @Throws(Exception::class)
    fun testExtraSpacesAreRemoved() {
        osisToCanonicalTextSaxHandler!!.startElement(null, null, "verse", null)
        osisToCanonicalTextSaxHandler!!.characters("  ".toCharArray(), 0, 2)
        osisToCanonicalTextSaxHandler!!.characters(" ".toCharArray(), 0, 1)
        osisToCanonicalTextSaxHandler!!.characters("Be exalted,".toCharArray(), 0, 11)
        osisToCanonicalTextSaxHandler!!.characters(" ".toCharArray(), 0, 1)
        osisToCanonicalTextSaxHandler!!.characters("  ".toCharArray(), 0, 2)
        osisToCanonicalTextSaxHandler!!.characters("O God".toCharArray(), 0, 5)
        osisToCanonicalTextSaxHandler!!.endElement(null, null, "verse")

        // the last verse tag just add an extra space at the end but that is required to separate verses and is easier to leave.
        Assert.assertThat(osisToCanonicalTextSaxHandler!!.writer.html, CoreMatchers.equalTo("Be exalted, O God "))
    }
}
