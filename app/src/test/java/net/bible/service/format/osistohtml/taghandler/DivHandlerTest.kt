package net.bible.service.format.osistohtml.taghandler

import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.PassageInfo
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.crosswire.jsword.book.OSISUtil
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.xml.sax.helpers.AttributesImpl

class DivHandlerTest {
    private var osisToHtmlParameters: OsisToHtmlParameters? = null
    private var passageInfo: PassageInfo? = null
    private var verseInfo: VerseInfo? = null
    private var htmlTextWriter: HtmlTextWriter? = null
    private var divHandler: DivHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToHtmlParameters = OsisToHtmlParameters()
        passageInfo = PassageInfo()
        verseInfo = VerseInfo()
        htmlTextWriter = HtmlTextWriter()
        divHandler = DivHandler(osisToHtmlParameters!!, verseInfo!!, passageInfo!!, htmlTextWriter!!)
    }

    /**
     * This is the sort of example found but no reference to which module
     * <div type='paragraph' sID='xyz'></div>
     * Some text
     * <div type='paragraph' eID='xyz'></div>
     */
    @Test
    fun testCommonParagraphSidAndEid() {
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7681")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "paragraph")
        divHandler!!.start(attrs)
        divHandler!!.end()
        htmlTextWriter!!.write("Some text")
        passageInfo!!.isAnyTextWritten = true
        val attrs2 = AttributesImpl()
        attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7681")
        attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "paragraph")
        divHandler!!.start(attrs2)
        divHandler!!.end()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo("Some text<div class='breakline'></div>"))
    }

    /**
     * <div type='paragraph'>
     * Some text
    </div> *
     */
    @Test
    fun testSimpleParagraph() {
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "paragraph")
        divHandler!!.start(attrs)
        htmlTextWriter!!.write("Some text")
        passageInfo!!.isAnyTextWritten = true
        divHandler!!.end()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo("Some text<div class='breakline'></div>"))
    }

    /**
     * Osis2mod has started using type="x-p" for paragraphs, see JS-292
     * <div type='x-p' sID='xyz'></div>
     * Some text
     * <div type='x-p' eID='xyz'></div>
     */
    @Test
    fun testNewFreJndParagraphs() {
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7681")
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-p")
        divHandler!!.start(attrs)
        divHandler!!.end()
        htmlTextWriter!!.write("Some text")
        passageInfo!!.isAnyTextWritten = true
        val attrs2 = AttributesImpl()
        attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7681")
        attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-p")
        divHandler!!.start(attrs2)
        divHandler!!.end()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo("Some text<div class='breakline'></div>"))
    }

    /**
     * Some pre-verse titles are not marked as pre-verse directly but are wrapped in a div marked as pre-verse
     * E.g. ESVS Ps 25:	 *
     * <div><verse osisID='Ps.25.1'><div type="x-milestone" subType="x-preverse" sID="pv2905"></div> <title>Teach Me Your Paths</title> <title canonical="true" type="psalm"> Of David.</title> <div type="x-milestone" subType="x-preverse" eID="pv2905"></div>
    </verse></div> */
    @Test
    fun testEsvsTitleInPreVerseDiv() {
        // verse comes first
        htmlTextWriter!!.write("v1")
        verseInfo!!.currentVerseNo = 1
        verseInfo!!.positionToInsertBeforeVerse = 0
        verseInfo!!.isTextSinceVerse = false

        // then a div with a pre-verse attribute
        val attrsSidOpen = AttributesImpl()
        attrsSidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "x-preverse")
        attrsSidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "pv2905")
        divHandler!!.start(attrsSidOpen)
        divHandler!!.end()
        htmlTextWriter!!.write("Preverse text")
        passageInfo!!.isAnyTextWritten = true
        val attrsEidOpen = AttributesImpl()
        attrsEidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "x-preverse")
        attrsEidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "pv2905")
        divHandler!!.start(attrsEidOpen)
        divHandler!!.end()
        htmlTextWriter!!.write("Verse content")
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo("Preverse textv1Verse content"))
    }

    /**
     * In ERV many verses at the end of chapters have a div with pre-verse and start-milestone attributes but there is no matching end-milestone div and the div does not appear to contain anything
     * E.g. ERV Psalm 2:12 and Psalm 3:8 but there are many more.
     *
     * <div type="x-milestone" subType="x-preverse" sID="pv6984"></div>
     */
    @Test
    fun testErvUnMatchedFinalPreVerseDiv() {
        // verse comes first
        htmlTextWriter!!.write("v1")
        verseInfo!!.currentVerseNo = 1
        verseInfo!!.positionToInsertBeforeVerse = 0
        verseInfo!!.isTextSinceVerse = false
        htmlTextWriter!!.write("Verse content")
        verseInfo!!.isTextSinceVerse = true

        // then a div with a pre-verse attribute
        val attrsSidOpen = AttributesImpl()
        attrsSidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-milestone")
        attrsSidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "x-preverse")
        attrsSidOpen.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "pv2905")
        divHandler!!.start(attrsSidOpen)
        divHandler!!.end()

        // no matching eid
        htmlTextWriter!!.abortAnyUnterminatedInsertion()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo("v1Verse content"))
    }
}
