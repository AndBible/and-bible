package net.bible.service.format.osistohtml.taghandler

import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OSISUtil2
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.PassageInfo
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.crosswire.jsword.book.OSISUtil
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.xml.sax.helpers.AttributesImpl

/**
 * Continuation quotation marks
 * The <milestone type="cQuote"></milestone> can be used to indicate the presence of a continued quote.
 * If the marker attribute is present, it will use that otherwise it will use a straight double quote, ".
 * Since there is no level attribute on the milestone element, it is best to specify the marker attribute.
 * http://www.crosswire.org/wiki/OSIS_Bibles#Continuation_quotation_marks
 */
class MilestoneHandlerTest {
    private var osisToHtmlParameters: OsisToHtmlParameters? = null
    private var passageInfo: PassageInfo? = null
    private var verseInfo: VerseInfo? = null
    private var writer: HtmlTextWriter? = null
    private var milestoneHandler: MilestoneHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToHtmlParameters = OsisToHtmlParameters()
        passageInfo = PassageInfo()
        verseInfo = VerseInfo()
        writer = HtmlTextWriter()
        milestoneHandler = MilestoneHandler(osisToHtmlParameters!!, passageInfo!!, verseInfo!!, writer!!)
    }

    /**
     * milestone marker='"' at start of verse
     * diatheke -b ESV -f OSIS -k Jn 3:16
     *
     * John 3:16:
     * <q marker=""><milestone marker="“" type="cQuote"></milestone>For God ... eternal life.</q>
     */
    @Test
    fun testQuote() {
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil2.OSIS_ATTR_MARKER, null, "“")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "cQuote")
        milestoneHandler!!.start(attrs)
        milestoneHandler!!.end()
        writer!!.write("For God ... eternal life.")
        passageInfo!!.isAnyTextWritten = true
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo("“For God ... eternal life."))
    }

    /**
     * milestone marker='"' at start of verse
     * diatheke -b ESV -f OSIS -k Jn 3:16
     *
     * John 3:16:
     * <q marker=""><milestone marker="“" type="cQuote"></milestone>For God ... eternal life.</q>
     */
    @Test
    fun testDefaultQuote() {
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "cQuote")
        milestoneHandler!!.start(attrs)
        milestoneHandler!!.end()
        writer!!.write("For God ... eternal life.")
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo("&quot;For God ... eternal life."))
    }

    /**
     * KJV Gen 1:6
     * <milestone marker="¶" type="x-p"></milestone><w lemma="strong:H0430">And God</w>
     */
    @Test
    fun testNewLine() {
        writer!!.write("passage start")
        passageInfo!!.isAnyTextWritten = true
        verseInfo!!.positionToInsertBeforeVerse = writer!!.position
        verseInfo!!.isTextSinceVerse = true
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil2.OSIS_ATTR_MARKER, null, "¶")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "line")
        milestoneHandler!!.start(attrs)
        milestoneHandler!!.end()
        writer!!.write("And God")
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo("passage start<br />And God"))
    }

    /**
     * KJV Gen 1:6
     * <milestone marker="¶" type="x-p"></milestone><w lemma="strong:H0430">And God</w>
     */
    @Test
    fun testNewLineXP() {
        writer!!.write("passage start")
        passageInfo!!.isAnyTextWritten = true
        verseInfo!!.positionToInsertBeforeVerse = writer!!.position
        verseInfo!!.isTextSinceVerse = true
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil2.OSIS_ATTR_MARKER, null, "¶")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-p")
        milestoneHandler!!.start(attrs)
        milestoneHandler!!.end()
        writer!!.write("And God")
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo("passage start<br />And God"))
    }

    /**
     * If verse marker has just been written then move BR to just before verse marker.
     */
    @Test
    fun testNewLineMovedBeforeVerseNo() {
        writer!!.write("passage start")
        passageInfo!!.isAnyTextWritten = true
        verseInfo!!.positionToInsertBeforeVerse = writer!!.position
        writer!!.write("<span class='verseNo' id='1'>1</span>")
        verseInfo!!.isTextSinceVerse = false
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil2.OSIS_ATTR_MARKER, null, "¶")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-p")
        milestoneHandler!!.start(attrs)
        milestoneHandler!!.end()
        writer!!.write("And God")
        Assert.assertThat(writer!!.html, CoreMatchers.equalTo("passage start<br /><span class='verseNo' id='1'>1</span>And God"))
    }
}
