package net.bible.service.format.osistohtml;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import junit.framework.TestCase;
import net.bible.service.format.osistohtml.OsisToHtmlSaxHandler.VerseInfo;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

public class TitleHandlerTest extends TestCase {

	private OsisToHtmlParameters osisToHtmlParameters;
	private VerseInfo verseInfo;
	private HtmlTextWriter htmlTextWriter;
	
	private TitleHandler titleHandler;
	
	protected void setUp() throws Exception {
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
	public void testESVTitle() {
//		Attributes attr = new AttributesImpl();
//		titleHandler.start();
	}
}
