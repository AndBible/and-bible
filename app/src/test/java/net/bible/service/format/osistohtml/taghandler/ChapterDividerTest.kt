package net.bible.service.format.osistohtml.taghandler

import net.bible.service.format.osistohtml.TextWriter
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.crosswire.jsword.book.OSISUtil
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.xml.sax.helpers.AttributesImpl

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChapterDividerTest {
    private var osisToHtmlParameters: OsisToHtmlParameters? = null
    private var verseInfo: VerseInfo? = null
    private var textWriter: TextWriter? = null
    private var chapterDivider: ChapterDivider? = null

    @Before
    fun setup() {
        osisToHtmlParameters = OsisToHtmlParameters()
        osisToHtmlParameters!!.chapter = 3
        osisToHtmlParameters!!.isShowChapterDivider = true
        verseInfo = VerseInfo()
        textWriter = TextWriter()
        chapterDivider = ChapterDivider(osisToHtmlParameters!!, verseInfo!!, textWriter!!)
    }

    @Test
    fun normal() {
        osisToHtmlParameters!!.isShowVerseNumbers = true
        chapterDivider!!.doStart()
        Assert.assertThat(textWriter!!.html, CoreMatchers.equalTo("<div class='chapterNo'>&#8212; 3 &#8212;</div><span class='position-marker' id='3'>&#x200b;</span>"))
    }

    @Test
    fun noVerseOrChapters() {
        osisToHtmlParameters!!.isShowVerseNumbers = false
        chapterDivider!!.doStart()
        Assert.assertThat(textWriter!!.html, CoreMatchers.equalTo("<span class='position-marker' id='3'>&#x200b;</span>"))
    }

    @Test
    fun chapter1() {
        osisToHtmlParameters!!.chapter = 1
        osisToHtmlParameters!!.isShowVerseNumbers = true
        chapterDivider!!.doStart()
        Assert.assertThat(textWriter!!.html, CoreMatchers.equalTo("<span class='position-marker' id='1'>&#x200b;</span>"))
    }

    @Test
    fun commentary() {
        osisToHtmlParameters!!.chapter = 3
        osisToHtmlParameters!!.isShowVerseNumbers = true
        osisToHtmlParameters!!.isShowChapterDivider = false
        chapterDivider!!.doStart()
        Assert.assertThat(textWriter!!.html, CoreMatchers.equalTo(""))
    }

    @Test
    fun testChapterBeforeInitialPreVerseTitle() {
        // Chapter comes first
        chapterDivider!!.doStart()
        val attrs = AttributesImpl()
        attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "preverse")

        // verse comes next
        textWriter!!.write("v1")
        verseInfo!!.currentVerseNo = 1
        verseInfo!!.isTextSinceVerse = false
        val titleHandler = TitleHandler(osisToHtmlParameters!!, verseInfo!!, textWriter!!)

        // then the title which needs to be moved pre-verse
        titleHandler.start(attrs)
        textWriter!!.write("Title")
        titleHandler.end()

        // then some verse content which stays after the verse
        textWriter!!.write("Verse content")
        Assert.assertThat(textWriter!!.html, CoreMatchers.equalTo("<span class='position-marker' id='3'>&#x200b;</span><h1 class='heading1'>Title</h1>v1Verse content"))
    }
}
