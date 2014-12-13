package net.bible.service.format.osistohtml;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import junit.framework.TestCase;

import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

public class LHandlerTest extends TestCase {

	private OsisToHtmlParameters osisToHtmlParameters;
	private HtmlTextWriter htmlTextWriter;
	
	private LHandler lHandler;
	
	protected void setUp() throws Exception {
		osisToHtmlParameters = new OsisToHtmlParameters();
		htmlTextWriter = new HtmlTextWriter();
		
		lHandler = new LHandler(osisToHtmlParameters, htmlTextWriter);
	}

	/**
	 * No attributes. Just start title, write title, end title.
	 * 
	 * <l>Single line</l>
	 */
	public void testSimpleL() {
		Attributes attr = new AttributesImpl();
		lHandler.startL(attr);
		htmlTextWriter.write("Single line");
		lHandler.endL();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("Single line<br />"));
	}

	/**
	 * ESV Ps 117:
	 * <h1 class='heading1'>The Lord's Faithfulness Endures Forever</h1> <span class='verse' id='1'/>&#x200b;</span>Praise the Lord, all nations!<br />&#160;&#160;Extol him, all peoples!<br /> <span class='verse' id='2'/>&#x200b;</span>For great is his steadfast love toward us,<br />&#160;&#160;and the faithfulness of the Lord endures forever.<br />Praise the Lord!<br />
	 */
	
	/** ESV Ps.117.1
	 * <l sID="x7681" />Praise the Lord, all nations!<l eID="x7681" type="x-br" />
	 */
	public void testLsIDeID() {
		AttributesImpl attrs = new AttributesImpl();
		attrs.addAttribute(null, null, OSISUtil.OSIS_ATTR_SID, null, "x7681");
		lHandler.startL(attrs);
		lHandler.endL();
		
		htmlTextWriter.write("Praise the Lord, all nations!");
		
		AttributesImpl attrs2 = new AttributesImpl();
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_EID, null, "x7681");
		attrs2.addAttribute(null, null, OSISUtil.OSIS_ATTR_TYPE, null, "x-br");
		lHandler.startL(attrs2);
		lHandler.endL();
		
		assertThat(htmlTextWriter.getHtml(), equalTo("Praise the Lord, all nations!<br />"));
	}
}
