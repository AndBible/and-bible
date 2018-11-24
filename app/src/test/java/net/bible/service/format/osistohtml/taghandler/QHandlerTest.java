package net.bible.service.format.osistohtml.taghandler;

import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;

import org.crosswire.jsword.book.OSISUtil;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class QHandlerTest {

	private OsisToHtmlParameters osisToHtmlParameters;
	private HtmlTextWriter writer;
	
	private QHandler qHandler;

	@Before
	public void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		writer = new HtmlTextWriter();
		
		qHandler = new QHandler(osisToHtmlParameters, writer);
	}

	/**
	 * Highlight words of Christ in red
	 * 
	 * <q who="Jesus">A quote from Jesus</q>
	 */
	@Test
	public void testRedLetter() {
		osisToHtmlParameters.setRedLetter(true);
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_Q_WHO, null, "Jesus");
		qHandler.start(attrs);

		writer.write("A quote from Jesus");
		
		qHandler.end();
		
		assertThat(writer.getHtml(), equalTo("<span class='redLetter'>A quote from Jesus</span>"));
	}

	/**
	 * Highlight words of Christ in red
	 * 
	 * <q who="Jesus">A quote from Jesus</q>
	 */
	@Test
	public void testQuoteWithSpecificMarker() {
		osisToHtmlParameters.setRedLetter(true);
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, "marker", null, "'");
		qHandler.start(attrs);

		writer.write("A specific quote");
		
		qHandler.end();
		
		assertThat(writer.getHtml(), equalTo("'A specific quote'"));
	}


	/**
	 * Highlight words of Christ in red
	 * 
	 * <q>A quote</q>
	 */
	@Test
	public void testQuoteMilestones() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "id1");
		qHandler.start(attrs);
		qHandler.end();

		writer.write("A quote");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "id1");
		qHandler.start(attrs2);
		qHandler.end();
		
		assertThat(writer.getHtml(), equalTo("&quot;A quote&quot;"));
	}


	/**
	 * Highlight disabled
	 * 
	 * <q who="Jesus">A quote from Jesus</q>
	 */
	@Test
	public void testRedLetterDisabled() {
		osisToHtmlParameters.setRedLetter(false);
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.ATTRIBUTE_Q_WHO, null, "Jesus");
		qHandler.start(attrs);

		writer.write("A quote from Jesus");
		
		qHandler.end();
		
		assertThat(writer.getHtml(), equalTo("A quote from Jesus"));
	}
}
