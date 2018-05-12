package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VerseHandlerTest {
	private OsisToHtmlParameters osisToHtmlParameters;
	private VerseInfo verseInfo;
	private BookmarkMarker bookmarkMarkerMock;
	private MyNoteMarker myNoteMarker;
	private HtmlTextWriter htmlTextWriter;
	
	private VerseHandler verseHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		osisToHtmlParameters.setChapter(3);
		verseInfo = new VerseInfo();
		bookmarkMarkerMock = mock(BookmarkMarker.class);
		myNoteMarker = new MyNoteMarker(osisToHtmlParameters, verseInfo, htmlTextWriter);
		htmlTextWriter = new HtmlTextWriter();
		
		verseHandler = new VerseHandler(osisToHtmlParameters, verseInfo, bookmarkMarkerMock, myNoteMarker, htmlTextWriter);
	}

	/**
	 * No attributes. Just start verse, write some content, end verse.
	 * 
	 * <title>The creation</title>
	 */
	@Test
	public void testSimpleVerse() {
		osisToHtmlParameters.setShowVerseNumbers(true);
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5");	
		verseHandler.start(attrs);
		htmlTextWriter.write("The Creation");
		verseInfo.isTextSinceVerse = true;
		verseHandler.end();
		
		assertThat(htmlTextWriter.getHtml(), equalTo(" <span class='verse' id='3.5'><span class='verseNo'>5</span>&#160;The Creation</span>"));
	}

	/**
	 * No attributes. Just start verse, write some content, end verse.
	 *
	 * <title>The creation</title>
	 */
	@Test
	public void testSimpleVerseShowsBookmark() {
		when(bookmarkMarkerMock.getBookmarkClasses()).thenReturn(Arrays.asList("bookmarkClass"));

		osisToHtmlParameters.setShowVerseNumbers(true);
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5");
		verseHandler.start(attrs);
		htmlTextWriter.write("The Creation");
		verseInfo.isTextSinceVerse = true;
		verseHandler.end();

		assertThat(htmlTextWriter.getHtml(), equalTo(" <span class='verse bookmarkClass' id='3.5'><span class='verseNo'>5</span>&#160;The Creation</span>"));
	}

	/**
	 * No attributes. Just start verse, write some content, end verse.
	 *
	 * <title>The creation</title>
	 */
	@Test
	public void testNoVerseNumbers() {
		osisToHtmlParameters.setShowVerseNumbers(false);
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5");
		verseHandler.start(attrs);
		htmlTextWriter.write("The Creation");
		verseInfo.isTextSinceVerse = true;
		verseHandler.end();

		assertThat(htmlTextWriter.getHtml(), equalTo(" <span class='verse' id='3.5'><span class='verseNo'>&#x200b;</span>The Creation</span>"));
	}

	/**
	 * No attributes. Just start verse, write some content, end verse.
	 *
	 * <title>The creation</title>
	 */
	@Test
	public void testNoVerseNumbersButBookmarkStillShown() {
		when(bookmarkMarkerMock.getBookmarkClasses()).thenReturn(Arrays.asList("bookmarkClass"));

		osisToHtmlParameters.setShowVerseNumbers(false);
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_OSISID, null, "Ezek.40.5");
		verseHandler.start(attrs);
		htmlTextWriter.write("The Creation");
		verseInfo.isTextSinceVerse = true;
		verseHandler.end();

		assertThat(htmlTextWriter.getHtml(), equalTo(" <span class='verse bookmarkClass' id='3.5'><span class='verseNo'>&#x200b;</span>The Creation</span>"));
	}
}
