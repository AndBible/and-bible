package net.bible.service.format.osistohtml.taghandler

import net.bible.service.format.osistohtml.HtmlTextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.crosswire.jsword.book.OSISUtil
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.xml.sax.helpers.AttributesImpl
import java.util.*

class VerseHandlerTest {
    private var osisToHtmlParameters: OsisToHtmlParameters? = null
    private var verseInfo: VerseInfo? = null
    private var bookmarkMarkerMock: BookmarkMarker? = null
    private var myNoteMarker: MyNoteMarker? = null
    private var htmlTextWriter: HtmlTextWriter? = null
    private var verseHandler: VerseHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        osisToHtmlParameters = OsisToHtmlParameters()
        osisToHtmlParameters!!.chapter = 3
        osisToHtmlParameters!!.isBible = true
        verseInfo = VerseInfo()
        bookmarkMarkerMock = Mockito.mock(BookmarkMarker::class.java)
        htmlTextWriter = HtmlTextWriter()
        myNoteMarker = MyNoteMarker(osisToHtmlParameters!!, verseInfo!!, htmlTextWriter!!)
        verseHandler = VerseHandler(osisToHtmlParameters!!, verseInfo!!, bookmarkMarkerMock!!, myNoteMarker!!, htmlTextWriter!!)
    }

    /**
     * No attributes. Just start verse, write some content, end verse.
     *
     * <title>The creation</title>
     */
    @Test
    fun testSimpleVerse() {
        osisToHtmlParameters!!.isShowVerseNumbers = true
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5")
        verseHandler!!.start(attrs)
        htmlTextWriter!!.write("The Creation")
        verseInfo!!.isTextSinceVerse = true
        verseHandler!!.end()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo(" <span class='verse' id='3.5'><span class='verseNo'>5</span>&#160;<span class='bookmark1'></span><span class='bookmark2'></span>The Creation</span>"))
    }

    /**
     * No attributes. Just start verse, write some content, end verse.
     *
     * <title>The creation</title>
     */
    @Test
    fun testSimpleVerseShowsBookmark() {
        Mockito.`when`(bookmarkMarkerMock!!.bookmarkClasses).thenReturn(Arrays.asList("bookmarkClass"))
        osisToHtmlParameters!!.isShowVerseNumbers = true
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5")
        verseHandler!!.start(attrs)
        htmlTextWriter!!.write("The Creation")
        verseInfo!!.isTextSinceVerse = true
        verseHandler!!.end()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo(" <span class='verse bookmarkClass' id='3.5'><span class='verseNo'>5</span>&#160;<span class='bookmark1'></span><span class='bookmark2'></span>The Creation</span>"))
    }

    /**
     * No attributes. Just start verse, write some content, end verse.
     *
     * <title>The creation</title>
     */
    @Test
    fun testNoVerseNumbers() {
        osisToHtmlParameters!!.isShowVerseNumbers = false
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5")
        verseHandler!!.start(attrs)
        htmlTextWriter!!.write("The Creation")
        verseInfo!!.isTextSinceVerse = true
        verseHandler!!.end()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo(" <span class='verse' id='3.5'><span class='verseNo position-marker'>&#x200b;</span><span class='bookmark1'></span><span class='bookmark2'></span>The Creation</span>"))
    }

    /**
     * No attributes. Just start verse, write some content, end verse.
     *
     * <title>The creation</title>
     */
    @Test
    fun testNoVerseNumbersButBookmarkStillShown() {
        Mockito.`when`(bookmarkMarkerMock!!.bookmarkClasses).thenReturn(Arrays.asList("bookmarkClass"))
        osisToHtmlParameters!!.isShowVerseNumbers = false
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5")
        verseHandler!!.start(attrs)
        htmlTextWriter!!.write("The Creation")
        verseInfo!!.isTextSinceVerse = true
        verseHandler!!.end()
        Assert.assertThat(htmlTextWriter!!.html, CoreMatchers.equalTo(" <span class='verse bookmarkClass' id='3.5'><span class='verseNo position-marker'>&#x200b;</span><span class='bookmark1'></span><span class='bookmark2'></span>The Creation</span>"))
    }
}
