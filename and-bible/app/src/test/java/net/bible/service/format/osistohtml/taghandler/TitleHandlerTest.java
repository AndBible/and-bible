package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;


public class TitleHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	private VerseInfo verseInfo;
	private HtmlTextWriter htmlTextWriter;
	
	private TitleHandler titleHandler;
	
	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		verseInfo = new VerseInfo();
		htmlTextWriter = new HtmlTextWriter();
		
		titleHandler = new TitleHandler(osisToHtmlParameters, verseInfo, htmlTextWriter);
	}

	/**
	 * No attributes. Just start title, write title, end title.
	 * 
	 * <title>The creation</title>
	 */
	@Test
	public void testSimpleTitle() {
		Attributes attr = new AttributesImpl();
		titleHandler.start(attr);
		htmlTextWriter.write("The Creation");
		titleHandler.end();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("<h1 class='heading1'>The Creation</h1>"));
		
	}

	/**
	 * <title subType="x-preverse" type="section">
				The
				<divineName>Lord</divineName>
				's Faithfulness Endures Forever
			</title>
	 */
	@Test
	public void testESVTitle() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SUBTYPE, null, "preverse");
		
		// verse comes first
		htmlTextWriter.write("v1");
		verseInfo.currentVerseNo = 1;
		verseInfo.positionToInsertBeforeVerse = 0;
		verseInfo.isTextSinceVerse = false;
		
		// then the title which needs to be moved pre-verse
		titleHandler.start(attrs);
		htmlTextWriter.write("Title");
		titleHandler.end();
		
		// then some verse content which stays after the verse
		htmlTextWriter.write("Verse content");
		
		assertThat(htmlTextWriter.getHtml(), equalTo("<h1 class='heading1'>Title</h1>v1Verse content"));
		
	}
}
