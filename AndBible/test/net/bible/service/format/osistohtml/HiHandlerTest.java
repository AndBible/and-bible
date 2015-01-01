package net.bible.service.format.osistohtml;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

public class HiHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	private HtmlTextWriter writer;
	
	private HiHandler hiHandler;

	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		writer = new HtmlTextWriter();
		
		hiHandler = new HiHandler(osisToHtmlParameters, writer);
	}

	/**
	 * Highlight words of Christ in red
	 * 
	 * <q who="Jesus">A quote from Jesus</q>
	 */
	@Test
	public void testSuper() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "super");
		hiHandler.start(attrs);

		writer.write("text");
		
		hiHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<span class='hi hi_super'>text</span>"));
	}

	public void testSub() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "sub");
		hiHandler.start(attrs);

		writer.write("text");
		
		hiHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<span class='hi hi_sub'>text</span>"));
	}
}
