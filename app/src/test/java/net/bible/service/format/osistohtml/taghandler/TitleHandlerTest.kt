package net.bible.service.format.osistohtml.taghandler

import net.bible.service.format.osistohtml.TextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.crosswire.jsword.book.OSISUtil
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

class TitleHandlerTest {
    private var osisToHtmlParameters: OsisToHtmlParameters? = null
    private var verseInfo: VerseInfo? = null
    private var textWriter: TextWriter? = null
    private var titleHandler: TitleHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToHtmlParameters = OsisToHtmlParameters()
        verseInfo = VerseInfo()
        textWriter = TextWriter()
        titleHandler = TitleHandler(osisToHtmlParameters!!, verseInfo!!, textWriter!!)
    }

    /**
     * No attributes. Just start title, write title, end title.
     *
     * <title>The creation</title>
     */
    @Test
    fun testSimpleTitle() {
        val attr: Attributes = AttributesImpl()
        titleHandler!!.start(attr)
        textWriter!!.write("The Creation")
        titleHandler!!.end()
        Assert.assertThat(textWriter!!.html, CoreMatchers.equalTo("<h1 class='heading1'>The Creation</h1>"))
    }

    /**
     * <title subType="x-preverse" type="section">
     * The
     * <divineName>Lord</divineName>
     * 's Faithfulness Endures Forever
    </title> *
     */
    @Test
    fun testESVTitle() {
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "preverse")

        // verse comes first
        textWriter!!.write("v1")
        verseInfo!!.currentVerseNo = 1
        verseInfo!!.positionToInsertBeforeVerse = 0
        verseInfo!!.isTextSinceVerse = false

        // then the title which needs to be moved pre-verse
        titleHandler!!.start(attrs)
        textWriter!!.write("Title")
        titleHandler!!.end()

        // then some verse content which stays after the verse
        textWriter!!.write("Verse content")
        Assert.assertThat(textWriter!!.html, CoreMatchers.equalTo("<h1 class='heading1'>Title</h1>v1Verse content"))
    }
}
