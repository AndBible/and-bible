package net.bible.service.format.osistohtml.taghandler

import net.bible.service.format.osistohtml.HtmlTextWriter
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
    private var htmlTextWriter: HtmlTextWriter? = null
    private var titleHandler: TitleHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToHtmlParameters = OsisToHtmlParameters()
        verseInfo = VerseInfo()
        htmlTextWriter = HtmlTextWriter()
        titleHandler = TitleHandler(osisToHtmlParameters!!, verseInfo!!, htmlTextWriter!!)
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
        htmlTextWriter!!.write("The Creation")
        titleHandler!!.end()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo("<h1 class='heading1'>The Creation</h1>"))
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
        htmlTextWriter!!.write("v1")
        verseInfo!!.currentVerseNo = 1
        verseInfo!!.positionToInsertBeforeVerse = 0
        verseInfo!!.isTextSinceVerse = false

        // then the title which needs to be moved pre-verse
        titleHandler!!.start(attrs)
        htmlTextWriter!!.write("Title")
        titleHandler!!.end()

        // then some verse content which stays after the verse
        htmlTextWriter!!.write("Verse content")
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo("<h1 class='heading1'>Title</h1>v1Verse content"))
    }
}
