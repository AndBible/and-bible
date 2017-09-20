package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.junit.Before;
import org.junit.Test;

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
	private HtmlTextWriter htmlTextWriter;

	private ChapterDivider chapterDivider;

	@Before
	public void setup() {
		osisToHtmlParameters = new OsisToHtmlParameters();
		osisToHtmlParameters.setChapter(3);
		osisToHtmlParameters.setShowChapterDivider(true);
		htmlTextWriter = new HtmlTextWriter();
		chapterDivider = new ChapterDivider(osisToHtmlParameters, htmlTextWriter);
	}

	@Test
	public void normal() throws Exception {
		osisToHtmlParameters.setShowVerseNumbers(true);
		chapterDivider.start(null);
		assertThat(htmlTextWriter.getHtml(), equalTo("<div class='chapterNo'>&#8212; 3 &#8212;</div><div id='3'></div>"));
	}

	@Test
	public void noVerseOrChapters() throws Exception {
		osisToHtmlParameters.setShowVerseNumbers(false);
		chapterDivider.start(null);
		assertThat(htmlTextWriter.getHtml(), equalTo("<div class='chapterNo'>&nbsp;</div><div id='3'></div>"));
	}

	@Test
	public void chapter1() throws Exception {
		osisToHtmlParameters.setChapter(1);
		osisToHtmlParameters.setShowVerseNumbers(true);
		chapterDivider.start(null);
		assertThat(htmlTextWriter.getHtml(), equalTo("<div id='1'></div>"));
	}

	@Test
	public void commentary() throws Exception {
		osisToHtmlParameters.setChapter(3);
		osisToHtmlParameters.setShowVerseNumbers(true);
		osisToHtmlParameters.setShowChapterDivider(false);
		chapterDivider.start(null);
		assertThat(htmlTextWriter.getHtml(), equalTo(""));
	}
}