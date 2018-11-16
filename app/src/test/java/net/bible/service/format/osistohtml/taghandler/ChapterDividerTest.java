package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public class ChapterDividerTest {
	private OsisToHtmlParameters osisToHtmlParameters;
	private OsisToHtmlSaxHandler.VerseInfo verseInfo;
	private HtmlTextWriter htmlTextWriter;

	private ChapterDivider chapterDivider;

	@Before
	public void setup() {
		osisToHtmlParameters = new OsisToHtmlParameters();
		osisToHtmlParameters.setChapter(3);
		osisToHtmlParameters.setShowChapterDivider(true);
		verseInfo = new OsisToHtmlSaxHandler.VerseInfo();
		htmlTextWriter = new HtmlTextWriter();
		chapterDivider = new ChapterDivider(osisToHtmlParameters, verseInfo, htmlTextWriter);
	}

	@Test
	public void normal() {
		osisToHtmlParameters.setShowVerseNumbers(true);
		chapterDivider.start(null);
		assertThat(htmlTextWriter.getHtml(), equalTo("<div class='chapterNo'>&#8212; 3 &#8212;</div><div id='3'></div>"));
	}

	@Test
	public void noVerseOrChapters() {
		osisToHtmlParameters.setShowVerseNumbers(false);
		chapterDivider.start(null);
		assertThat(htmlTextWriter.getHtml(), equalTo("<div class='chapterNo'>&nbsp;</div><div id='3'></div>"));
	}

	@Test
	public void chapter1() {
		osisToHtmlParameters.setChapter(1);
		osisToHtmlParameters.setShowVerseNumbers(true);
		chapterDivider.start(null);
		assertThat(htmlTextWriter.getHtml(), equalTo("<div id='1'></div>"));
	}

	@Test
	public void commentary() {
		osisToHtmlParameters.setChapter(3);
		osisToHtmlParameters.setShowVerseNumbers(true);
		osisToHtmlParameters.setShowChapterDivider(false);
		chapterDivider.start(null);
		assertThat(htmlTextWriter.getHtml(), equalTo(""));
	}

	@Test
	public void testChapterBeforeInitialPreVerseTitle() {
		// Chapter comes first
		chapterDivider.start(null);

		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "preverse");

		// verse comes next
		htmlTextWriter.write("v1");
		verseInfo.currentVerseNo = 1;
		verseInfo.isTextSinceVerse = false;
		TitleHandler titleHandler = new TitleHandler(osisToHtmlParameters, verseInfo, htmlTextWriter);

		// then the title which needs to be moved pre-verse
		titleHandler.start(attrs);
		htmlTextWriter.write("Title");
		titleHandler.end();

		// then some verse content which stays after the verse
		htmlTextWriter.write("Verse content");

		assertThat(htmlTextWriter.getHtml(), equalTo("<div class='chapterNo'>&nbsp;</div><div id='3'></div><h1 class='heading1'>Title</h1>v1Verse content"));

	}
}