package net.bible.service.format.osistohtml;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

public class VerseHandlerTest {
	private OsisToHtmlParameters osisToHtmlParameters;
	private VerseInfo verseInfo;
	private BookmarkMarker bookmarkMarker;
	private MyNoteMarker myNoteMarker;
	private HtmlTextWriter htmlTextWriter;
	
	private VerseHandler verseHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		verseInfo = new VerseInfo();
		bookmarkMarker = new BookmarkMarker(osisToHtmlParameters, verseInfo, htmlTextWriter);
		myNoteMarker = new MyNoteMarker(osisToHtmlParameters, verseInfo, htmlTextWriter);
		htmlTextWriter = new HtmlTextWriter();
		
		verseHandler = new VerseHandler(osisToHtmlParameters, verseInfo, bookmarkMarker, myNoteMarker, htmlTextWriter);
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
		
		assertThat(htmlTextWriter.getHtml(), equalTo(" <span class='verse' id='5'><span class='verseNo'>5</span>&#160;The Creation</span>"));
	}
}
